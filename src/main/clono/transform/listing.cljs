(ns clono.transform.listing
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

(defn- listing-id [id]
  (str "listing-" id))

(defn- caption-id [id]
  (str (listing-id id) "-caption"))

(defn- node-diagnostic [context node message]
  (diagnostic/at-point
   (:source-name context)
   "listing"
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
          "`listing`のキャプションには空白ではないプレーンテキストが必要です。")]
        []))
    [(node-diagnostic
      context
      node
      "`listing`にはプレーンテキストのキャプションが必要です。")]))

(defn- attribute-diagnostics [node context]
  (let [node-attributes (attributes node)
        id (gobj/get node-attributes "id")
        names (set (array-seq (js/Object.keys node-attributes)))]
    (cond-> []
      (nil? id)
      (conj (node-diagnostic context node "`listing`には`id`属性が必要です。"))

      (and (some? id) (not (reference-id/valid? id)))
      (conj (node-diagnostic
             context
             node
             "`listing`の`id`属性には英小文字で始まる英小文字、数字、ハイフンだけの値を指定してください。"))

      (not (every? #{"id"} names))
      (conj (node-diagnostic
             context
             node
             "`listing`には`id`以外の属性を指定できません。")))))

(defn- source-line-at [source offset]
  (let [line-end (str/index-of source "\n" offset)]
    (subs source offset (or line-end (count source)))))

(defn- fenced-code? [node context]
  (let [source (:source context)
        offset (ast/property node "position" "start" "offset")]
    (and (string? source)
         (number? offset)
         (some? (re-find #"^ {0,3}(?:`{3,}|~{3,})"
                         (source-line-at source offset))))))

(defn- code-node [node context]
  (let [body (vec (body-children node))]
    (when (and (= 1 (count body))
               (= "code" (.-type (first body)))
               (fenced-code? (first body) context))
      (first body))))

(defn- contains-unknown-directive? [node known-directive-names]
  (->> (rest (ast/nodes node))
       (some #(directive-validation/unknown-directive?
               %
               known-directive-names))))

(defn- content-diagnostics [node context known-directive-names]
  (if-let [code (code-node node context)]
    (cond-> []
      (str/includes? (or (gobj/get code "lang") "") ":")
      (conj (node-diagnostic
             context
             code
             "`listing`の言語指定にはコロンを使用できません。"))

      (some? (gobj/get code "meta"))
      (conj (node-diagnostic
             context
             code
             "`listing`のコードフェンスにはメタ情報を指定できません。")))
    (if (contains-unknown-directive? node known-directive-names)
      []
      [(node-diagnostic
        context
        node
        "`listing`の直下には一つのフェンス付きコードブロックだけを記述してください。")])))

(defn diagnostics [node context known-directive-names]
  (if (not= "containerDirective" (.-type node))
    [(node-diagnostic
      context
      node
      "`listing`はContainer directiveとして記述する必要があります。")]
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

(defn- listing-nodes [tree known-directive-names]
  (->> (directive-validation/validation-nodes tree known-directive-names)
       (filter #(and (= "listing" (.-name %))
                     (= "containerDirective" (.-type %))))
       vec))

(defn- placement-diagnostics [tree listings context]
  (let [parents (parent-map tree)]
    (->> listings
         (keep (fn [node]
                 (when (and (not (identical? tree (.get parents node)))
                            (not (directive-ancestor? node parents)))
                   (node-diagnostic
                    context
                    node
                    "`listing`はMarkdown文書のトップレベルに記述する必要があります。"))))
         vec)))

(defn- document-kind-diagnostics [listings context]
  (let [entry (:publication-entry context)]
    (if (and (= :build (:mode context))
             (some? entry)
             (not (contains? allowed-document-kinds (:kind entry))))
      (mapv #(node-diagnostic
              context
              %
              "`listing`は本文または付録の掲載Markdownにだけ記述できます。")
            listings)
      [])))

(defn document-diagnostics [tree context known-directive-names]
  (let [listings (listing-nodes tree known-directive-names)]
    (concat (placement-diagnostics tree listings context)
            (document-kind-diagnostics listings context))))

(defn- html-node [value]
  #js {:type "html" :value value})

(defn transform [node context]
  (let [id (logical-id node)
        caption (label-value (label-node node))]
    [(html-node
      (str "<figure class=\"clono-numbered-listing\" id=\""
           (gstring/htmlEscape (listing-id id))
           "\">\n"
           "<figcaption class=\"clono-listing-caption\" id=\""
           (gstring/htmlEscape (caption-id id))
           "\">"
           (gstring/htmlEscape caption)
           "</figcaption>"))
     (code-node node context)
     (html-node "</figure>")]))

(def rule
  {:node-type "containerDirective"
   :allowed-attribute-names #{"id"}
   :diagnostics diagnostics
   :document-diagnostics document-diagnostics
   :transform transform})
