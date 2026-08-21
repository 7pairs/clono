(ns clono.research.transformer
  (:require
   [clojure.string :as string]
   [clono.research.markdown-ast :as markdown-ast]
   [goog.object :as gobj]))

(def known-directive-names
  #{"align" "page-break" "index"})

(def expected-node-types
  {"align" "containerDirective"
   "page-break" "leafDirective"
   "index" "textDirective"})

(defn children [node]
  (when (js/Array.isArray (.-children node))
    (array-seq (.-children node))))

(defn nodes [tree]
  (tree-seq #(some? (children %)) children tree))

(defn property [object & names]
  (reduce gobj/get object names))

(defn attributes [node]
  (or (.-attributes node) #js {}))

(defn diagnostic [source-name node message]
  {:file source-name
   :line (property node "position" "start" "line")
   :column (property node "position" "start" "column")
   :directive (.-name node)
   :message message})

(defn wrong-node-type-message [node]
  (let [name (.-name node)
        expected-type (get expected-node-types name)]
    (when (not= expected-type (.-type node))
      (str "`" name "`は`" expected-type "`として記述する必要があります。"))))

(defn invalid-attributes-message [node]
  (let [name (.-name node)
        node-attributes (attributes node)]
    (case name
      "align"
      (when (not= "right" (gobj/get node-attributes "position"))
        "`align`には`position=\"right\"`が必要です。")

      "page-break"
      (when (pos? (alength (js/Object.keys node-attributes)))
        "`page-break`には属性を指定できません。")

      "index"
      (let [reading (gobj/get node-attributes "reading")]
        (when (or (not (string? reading))
                  (string/blank? reading))
          "`index`には空でない`reading`属性が必要です。"))

      nil)))

(defn validation-message [node]
  (when (contains? known-directive-names (.-name node))
    (or (wrong-node-type-message node)
        (invalid-attributes-message node))))

(defn validate [tree source-name]
  (->> (nodes tree)
       (keep (fn [node]
               (when-let [message (validation-message node)]
                 (diagnostic source-name node message))))
       vec))

(defn html-node [value]
  #js {:type "html" :value value})

(defn escape-html-attribute [value]
  (-> value
      (string/replace "&" "&amp;")
      (string/replace "\"" "&quot;")
      (string/replace "<" "&lt;")
      (string/replace ">" "&gt;")))

(declare transform-node!)

(defn transform-children! [node]
  (when-let [node-children (children node)]
    (set! (.-children node)
          (into-array (mapcat transform-node! node-children))))
  node)

(defn transform-node! [node]
  (transform-children! node)
  (case (.-name node)
    "align"
    (concat [(html-node "<div class=\"text-align-right\">")]
            (children node)
            [(html-node "</div>")])

    "page-break"
    [(html-node "<div class=\"page-break\" aria-hidden=\"true\"></div>")]

    "index"
    (let [reading (-> node attributes (gobj/get "reading") escape-html-attribute)]
      (concat [(html-node (str "<span class=\"index-marker\" data-index-reading=\""
                              reading
                              "\">"))]
              (children node)
              [(html-node "</span>")]))

    [node]))

(defn transform-tree! [tree]
  (transform-children! tree))

(defn transform [markdown source-name]
  (let [tree (markdown-ast/parse markdown)
        diagnostics (validate tree source-name)]
    (if (seq diagnostics)
      {:ok? false
       :output nil
       :diagnostics diagnostics}
      (do
        (transform-tree! tree)
        {:ok? true
         :output (markdown-ast/serialize tree)
         :diagnostics []}))))

