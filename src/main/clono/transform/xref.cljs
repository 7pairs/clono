(ns clono.transform.xref
  (:require
   [clojure.string :as str]
   [clono.ast :as ast]
   [clono.diagnostic :as diagnostic]
   [clono.transform.figure :as figure]
   [goog.object :as gobj]))

(def allowed-formats
  #{"number" "number-title" "title"})

(def placeholder-texts
  {"number" "図X.X"
   "number-title" "図X.X 参照先未解決"
   "title" "参照先未解決"})

(defn- attributes [node]
  (or (.-attributes node) #js {}))

(defn- label-value [node]
  (apply str (map #(.-value %) (ast/children node))))

(defn- node-diagnostic [context node message]
  (diagnostic/at-point
   (:source-name context)
   "xref"
   (ast/property node "position" "start")
   message))

(defn- label-diagnostics [node context]
  (let [children (vec (ast/children node))
        label (label-value node)]
    (if (or (empty? children)
            (not-every? #(= "text" (.-type %)) children)
            (str/blank? label)
            (not (re-matches figure/logical-id-pattern label)))
      [(node-diagnostic
        context
        node
        "`xref`のラベルには有効な参照先の論理IDが必要です。")]
      [])))

(defn- attribute-diagnostics [node context]
  (let [node-attributes (attributes node)
        type (gobj/get node-attributes "type")
        format (gobj/get node-attributes "format")
        names (set (array-seq (js/Object.keys node-attributes)))]
    (cond-> []
      (nil? type)
      (conj (node-diagnostic context node "`xref`には`type`属性が必要です。"))

      (and (some? type) (not= "figure" type))
      (conj (node-diagnostic
             context
             node
             "`xref`の`type`属性には`figure`を指定してください。"))

      (nil? format)
      (conj (node-diagnostic context node "`xref`には`format`属性が必要です。"))

      (and (some? format) (not (contains? allowed-formats format)))
      (conj (node-diagnostic
             context
             node
             "`xref`の`format`属性には`number`、`number-title`または`title`を指定してください。"))

      (not (every? #{"type" "format"} names))
      (conj (node-diagnostic
             context
             node
             "`xref`には`type`と`format`以外の属性を指定できません。")))))

(defn diagnostics [node context _known-directive-names]
  (if (not= "textDirective" (.-type node))
    [(node-diagnostic
      context
      node
      "`xref`はText directiveとして記述する必要があります。")]
    (vec (concat (label-diagnostics node context)
                 (attribute-diagnostics node context)))))

(defn- reference-target [node context]
  (let [id (label-value node)]
    (first (filter #(= id (:logical-id %))
                   (:reference-targets context)))))

(defn reference-diagnostics [node context]
  (let [target (reference-target node context)
        type (gobj/get (attributes node) "type")
        format (gobj/get (attributes node) "format")]
    (cond
      (and (= :build (:mode context))
           (nil? (:publication-entry context)))
      [(node-diagnostic
        context
        node
        "`publication`に掲載されていないMarkdownでは`xref`を使用できません。")]

      (and (nil? target) (= :transform (:mode context)))
      []

      (nil? target)
      [(node-diagnostic
        context
        node
        (str "`xref`の参照先`" (label-value node)
             "`を解決できません。"))]

      (not= type (:type target))
      [(node-diagnostic
        context
        node
        "`xref`の参照種別が参照先と一致しません。")]

      (and (not (:numbered? target))
           (contains? #{"number" "number-title"} format))
      [(node-diagnostic
        context
        node
        "`xref`の表示形式に番号を持たない参照先の番号を指定できません。")]

      :else
      [])))

(defn- html-node [value]
  #js {:type "html" :value value})

(defn- class-value [format placeholder?]
  (str "clono-xref clono-xref-figure clono-xref-"
       format
       (when placeholder? " clono-xref-placeholder")))

(defn- resolved-html [target format]
  (let [title-attribute
        (when (contains? #{"number-title" "title"} format)
          (str " data-title-href=\"#" (:title-target-id target) "\""))]
    (str "<a class=\""
         (class-value format false)
         "\" href=\"#"
         (:target-id target)
         "\""
         title-attribute
         "></a>")))

(defn- placeholder-html [format]
  (str "<span class=\""
       (class-value format true)
       "\">"
       (get placeholder-texts format)
       "</span>"))

(defn transform [node context]
  (let [target (reference-target node context)
        format (gobj/get (attributes node) "format")]
    [(html-node
      (if target
        (resolved-html target format)
        (placeholder-html format)))]))

(def rule
  {:node-type "textDirective"
   :allowed-attribute-names #{"type" "format"}
   :required-text-attributes? true
   :diagnostics diagnostics
   :reference-diagnostics reference-diagnostics
   :transform transform})
