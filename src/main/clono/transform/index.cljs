(ns clono.transform.index
  (:require
   [clojure.string :as str]
   [clono.ast :as ast]
   [clono.diagnostic :as diagnostic]
   [clono.directive-validation :as directive-validation]
   [clono.index.marker :as marker]
   [clono.index.reading :as reading]
   [clono.index.sorting :as sorting]
   [goog.object :as gobj]
   [goog.string :as gstring]))

(def ^:private allowed-ancestor-node-types
  #{"paragraph" "list" "listItem" "blockquote"})

(def ^:private reading-error-codes
  #{:reading-not-string
    :reading-empty
    :reading-surrounding-whitespace
    :reading-unsupported-characters
    :reading-invalid-prolonged-mark
    :reading-unsupported-initial})

(defn- attributes [node]
  (or (.-attributes node) #js {}))

(defn- term [node]
  (apply str (map #(.-value %) (ast/children node))))

(defn- node-diagnostic [context node message]
  (diagnostic/at-point
   (:source-name context)
   "index"
   (ast/property node "position" "start")
   message))

(defn- term-diagnostics [node context]
  (let [children (vec (ast/children node))]
    (if (or (empty? children)
            (not-every? #(= "text" (.-type %)) children)
            (str/blank? (term node)))
      [(node-diagnostic
        context
        node
        "`index`のラベルには空白ではないプレーンテキストの索引語が必要です。")]
      [])))

(defn- reading-message [code]
  (case code
    :reading-not-string
    "`index`の`reading`属性には文字列を指定してください。"

    :reading-empty
    "`index`の`reading`属性には空白ではない読みを指定してください。"

    :reading-surrounding-whitespace
    "`index`の`reading`属性の先頭または末尾に空白を含めることはできません。"

    :reading-invalid-prolonged-mark
    "`index`の`reading`属性にある音引きから母音を決定できません。"

    :reading-unsupported-initial
    "`index`の`reading`属性を索引の分類へ割り当てられません。"

    "`index`の`reading`属性に使用できない文字または文字の組み合わせがあります。"))

(defn- reading-diagnostics [node context reading]
  (if (nil? reading)
    [(node-diagnostic
      context
      node
      "`index`には`reading`属性が必要です。")]
    (try
      (sorting/group-id reading)
      []
      (catch :default error
        (let [code (:code (ex-data error))]
          (if (contains? reading-error-codes code)
            [(node-diagnostic context node (reading-message code))]
            (throw error)))))))

(defn- attribute-diagnostics [node context]
  (let [node-attributes (attributes node)
        reading (gobj/get node-attributes "reading")
        names (set (array-seq (js/Object.keys node-attributes)))]
    (cond-> (reading-diagnostics node context reading)
      (not (every? #{"reading"} names))
      (conj (node-diagnostic
             context
             node
             "`index`には`reading`以外の属性を指定できません。")))))

(defn diagnostics [node context _known-directive-names]
  (if (not= "textDirective" (.-type node))
    [(node-diagnostic
      context
      node
      "`index`はText directiveとして記述する必要があります。")]
    (vec (concat (term-diagnostics node context)
                 (attribute-diagnostics node context)))))

(defn- parent-map [tree]
  (let [result (js/Map.)]
    (doseq [parent (ast/nodes tree)
            child (ast/children parent)]
      (.set result child parent))
    result))

(defn- directive-label? [node]
  (and (some? (.-data node))
       (true? (ast/property node "data" "directiveLabel"))))

(defn- allowed-ancestor? [node]
  (or (contains? allowed-ancestor-node-types (.-type node))
      (and (= "containerDirective" (.-type node))
           (= "column" (.-name node)))))

(defn- valid-placement? [tree node parents]
  (let [paragraph (.get parents node)]
    (and (= "paragraph" (.-type paragraph))
         (not (directive-label? paragraph))
         (loop [ancestor paragraph]
           (cond
             (identical? ancestor tree) true
             (nil? ancestor) false
             (allowed-ancestor? ancestor)
             (recur (.get parents ancestor))
             :else false)))))

(defn- index-nodes [tree known-directive-names]
  (->> (directive-validation/validation-nodes tree known-directive-names)
       (filter #(and (= "index" (.-name %))
                     (= "textDirective" (.-type %))))
       vec))

(defn document-diagnostics [tree context known-directive-names]
  (let [parents (parent-map tree)]
    (->> (index-nodes tree known-directive-names)
         (keep (fn [node]
                 (when-not (valid-placement? tree node parents)
                   (node-diagnostic
                    context
                    node
                    "`index`は許可された通常の段落の直接の子として記述してください。"))))
         vec)))

(defn collect-index-entries [node context]
  (let [source-term (term node)
        source-reading (gobj/get (attributes node) "reading")
        normalized-reading (reading/normalize source-reading)
        start (ast/property node "position" "start")]
    [{:term source-term
      :normalized-term (.normalize source-term "NFKC")
      :reading source-reading
      :normalized-reading normalized-reading
      :sort-key (sorting/sort-key normalized-reading)
      :group-id (sorting/group-id normalized-reading)
      :source-name (:source-name context)
      :line (ast/property start "line")
      :column (ast/property start "column")
      :offset (ast/property start "offset")
      :node node}]))

(defn- entry-diagnostic [entry message]
  (diagnostic/at-point
   (:source-name entry)
   "index"
   (ast/property (:node entry) "position" "start")
   message))

(defn- reading-conflict-diagnostics [entries]
  (:diagnostics
   (reduce
    (fn [{:keys [readings] :as result} entry]
      (let [source-term (:term entry)
            normalized-reading (:normalized-reading entry)]
        (if-let [first-reading (get readings source-term)]
          (cond-> result
            (not= first-reading normalized-reading)
            (update :diagnostics
                    conj
                    (entry-diagnostic
                     entry
                     (str "`index`の索引語`" source-term
                          "`には異なる読みを指定できません。"))))
          (assoc-in result [:readings source-term] normalized-reading))))
    {:readings {}
     :diagnostics []}
    entries)))

(defn- generated-html-ids [reference-targets]
  (into #{}
        (mapcat #(keep % [:target-id :title-target-id]))
        reference-targets))

(defn- marker-collision-diagnostics [entries reference-targets]
  (let [existing-ids (generated-html-ids reference-targets)]
    (->> entries
         (keep (fn [entry]
                 (when (contains? existing-ids (:marker-id entry))
                   (entry-diagnostic
                    entry
                    (str "`index`から生成するHTML ID`" (:marker-id entry)
                         "`が重複しています。")))))
         vec)))

(defn prepare-index-entries [entries reference-targets]
  (let [numbered-entries
        (mapv (fn [index entry]
                (assoc entry :marker-id (marker/id (inc index))))
              (range)
              entries)
        diagnostics
        (vec (concat (reading-conflict-diagnostics numbered-entries)
                     (marker-collision-diagnostics numbered-entries
                                                   reference-targets)))]
    (if (seq diagnostics)
      {:ok? false
       :entries nil
       :diagnostics diagnostics}
      {:ok? true
       :entries numbered-entries
       :diagnostics []})))

(defn add-index-entries [context entries]
  (assoc context
         :index-entries entries
         :index-entries-by-location
         (into {}
               (map (fn [entry]
                      [[(:source-name entry) (:offset entry)] entry]))
               entries)))

(defn- index-entry [node context]
  (get (:index-entries-by-location context)
       [(:source-name context)
        (ast/property node "position" "start" "offset")]))

(defn- html-node [value]
  #js {:type "html" :value value})

(defn transform [node context]
  (if-let [entry (index-entry node context)]
    [(html-node
      (str "<span class=\"clono-index-marker\" id=\""
           (:marker-id entry)
           "\">"
           (gstring/htmlEscape (:term entry))
           "</span>"))]
    (throw (ex-info "Index entry is missing from the transformation context"
                    {:source-name (:source-name context)
                     :offset (ast/property node "position" "start" "offset")}))))

(def rule
  {:node-type "textDirective"
   :allowed-attribute-names #{"reading"}
   :required-text-attributes? true
   :diagnostics diagnostics
   :document-diagnostics document-diagnostics
   :collect-index-entries collect-index-entries
   :transform transform})
