(ns clono.transform.table
  (:require
   [clojure.string :as str]
   [clono.ast :as ast]
   [clono.diagnostic :as diagnostic]
   [clono.directive-validation :as directive-validation]
   [clono.reference-id :as reference-id]
   [goog.object :as gobj]
   [goog.string :as gstring]))

(def allowed-document-kinds
  #{"chapter" "appendix"})

(def allowed-content-node-types
  #{"table"
    "tableRow"
    "tableCell"
    "text"
    "emphasis"
    "strong"
    "inlineCode"
    "link"
    "linkReference"})

(def node-descriptions
  {"image" "画像"
   "imageReference" "参照形式の画像"
   "footnoteReference" "脚注参照"
   "html" "raw HTML"
   "break" "強制改行"
   "containerDirective" "directive"
   "leafDirective" "directive"
   "textDirective" "directive"})

(defn- attributes [node]
  (or (.-attributes node) #js {}))

(defn- directive-label? [node]
  (and (some? (.-data node))
       (true? (ast/property node "data" "directiveLabel"))))

(defn- label-node [node]
  (first (filter directive-label? (ast/children node))))

(defn- body-children [node]
  (remove directive-label? (ast/children node)))

(defn- label-value [label]
  (apply str (map #(.-value %) (ast/children label))))

(defn- logical-id [node]
  (gobj/get (attributes node) "id"))

(defn- table-id [id]
  (str "table-" id))

(defn- caption-id [id]
  (str (table-id id) "-caption"))

(defn- node-description [node]
  (get node-descriptions (.-type node) (str "`" (.-type node) "`")))

(defn- node-diagnostic [context node message]
  (diagnostic/at-point
   (:source-name context)
   "table"
   (ast/property node "position" "start")
   message))

(defn- caption-diagnostics [node context]
  (if-let [label (label-node node)]
    (let [children (vec (ast/children label))]
      (if (or (not-every? #(= "text" (.-type %)) children)
              (str/blank? (label-value label)))
        [(node-diagnostic
          context
          label
          "`table`のキャプションには空白ではないプレーンテキストが必要です。")]
        []))
    [(node-diagnostic
      context
      node
      "`table`にはプレーンテキストのキャプションが必要です。")]))

(defn- attribute-diagnostics [node context]
  (let [node-attributes (attributes node)
        id (gobj/get node-attributes "id")
        names (set (array-seq (js/Object.keys node-attributes)))]
    (cond-> []
      (nil? id)
      (conj (node-diagnostic context node "`table`には`id`属性が必要です。"))

      (and (some? id) (not (reference-id/valid? id)))
      (conj (node-diagnostic
             context
             node
             "`table`の`id`属性には英小文字で始まる英小文字、数字、ハイフンだけの値を指定してください。"))

      (not (every? #{"id"} names))
      (conj (node-diagnostic
             context
             node
             "`table`には`id`以外の属性を指定できません。")))))

(defn- table-node [node]
  (let [body (vec (body-children node))]
    (when (and (= 1 (count body))
               (= "table" (.-type (first body))))
      (first body))))

(defn- invalid-content-nodes [table known-directive-names]
  (loop [remaining (seq (ast/nodes table))
         invalid []]
    (if-let [node (first remaining)]
      (let [type (.-type node)
            unknown-directive?
            (directive-validation/unknown-directive?
             node
             known-directive-names)]
        (cond
          unknown-directive?
          (recur (next remaining) invalid)

          (contains? allowed-content-node-types type)
          (recur (next remaining) invalid)

          :else
          (recur (next remaining) (conj invalid node))))
      invalid)))

(defn- contains-unknown-directive? [node known-directive-names]
  (->> (rest (ast/nodes node))
       (some #(directive-validation/unknown-directive?
               %
               known-directive-names))))

(defn- content-diagnostics [node context known-directive-names]
  (if-let [table (table-node node)]
    (mapv
     (fn [invalid-node]
       (node-diagnostic
        context
        invalid-node
        (str "`table`のセル内では"
             (node-description invalid-node)
             "を使用できません。")))
     (invalid-content-nodes table known-directive-names))
    (if (contains-unknown-directive? node known-directive-names)
      []
      [(node-diagnostic
        context
        node
        "`table`の直下には一つのGFM形式のMarkdown表だけを記述してください。")])))

(defn diagnostics [node context known-directive-names]
  (if (not= "containerDirective" (.-type node))
    [(node-diagnostic
      context
      node
      "`table`はContainer directiveとして記述する必要があります。")]
    (vec (concat (caption-diagnostics node context)
                 (attribute-diagnostics node context)
                 (content-diagnostics node context known-directive-names)))))

(defn- parent-map [tree]
  (let [result (js/Map.)]
    (doseq [parent (ast/nodes tree)
            child (ast/children parent)]
      (.set result child parent))
    result))

(defn- directive-ancestor? [node parents]
  (loop [ancestor (.get parents node)]
    (cond
      (nil? ancestor) false
      (ast/directive-node? ancestor) true
      :else (recur (.get parents ancestor)))))

(defn- table-nodes [tree known-directive-names]
  (->> (directive-validation/validation-nodes tree known-directive-names)
       (filter #(and (= "table" (.-name %))
                     (= "containerDirective" (.-type %))))
       vec))

(defn- placement-diagnostics [tree tables context]
  (let [parents (parent-map tree)]
    (->> tables
         (keep (fn [node]
                 (when (and (not (identical? tree (.get parents node)))
                            (not (directive-ancestor? node parents)))
                   (node-diagnostic
                    context
                    node
                    "`table`はMarkdown文書のトップレベルに記述する必要があります。"))))
         vec)))

(defn- document-kind-diagnostics [tables context]
  (let [entry (:publication-entry context)]
    (if (and (= :build (:mode context))
             (some? entry)
             (not (contains? allowed-document-kinds (:kind entry))))
      (mapv #(node-diagnostic
              context
              %
              "`table`は本文または付録の掲載Markdownにだけ記述できます。")
            tables)
      [])))

(defn document-diagnostics [tree context known-directive-names]
  (let [tables (table-nodes tree known-directive-names)]
    (concat (placement-diagnostics tree tables context)
            (document-kind-diagnostics tables context))))

(defn collect-reference-targets [node context]
  (let [id (logical-id node)
        start (ast/property node "position" "start")]
    [{:logical-id id
      :type "table"
      :target-id (table-id id)
      :title-target-id (caption-id id)
      :numbered? true
      :source-name (:source-name context)
      :line (ast/property start "line")
      :column (ast/property start "column")}]))

(defn- html-node [value]
  #js {:type "html" :value value})

(defn transform [node _context]
  (let [id (logical-id node)
        caption (label-value (label-node node))]
    [(html-node
      (str "<figure class=\"clono-numbered-table\" id=\""
           (gstring/htmlEscape (table-id id))
           "\">"))
     (table-node node)
     (html-node
      (str "<figcaption class=\"clono-table-caption\" id=\""
           (gstring/htmlEscape (caption-id id))
           "\">"
           (gstring/htmlEscape caption)
           "</figcaption>\n"
           "</figure>"))]))

(def rule
  {:node-type "containerDirective"
   :allowed-attribute-names #{"id"}
   :diagnostics diagnostics
   :document-diagnostics document-diagnostics
   :collect-reference-targets collect-reference-targets
   :transform transform})
