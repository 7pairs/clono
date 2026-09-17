(ns clono.research.definition-list-directive
  (:require
   ["mdast-util-directive" :refer [directiveFromMarkdown directiveToMarkdown]]
   ["mdast-util-from-markdown" :refer [fromMarkdown]]
   ["mdast-util-to-markdown" :refer [toMarkdown]]
   ["micromark-extension-directive" :refer [directive]]
   [clojure.string :as string]
   [goog.object :as gobj]
   [goog.string :as gstring]))

(def allowed-term-node-types
  #{"text" "inlineCode"})

(defn parse [source]
  (fromMarkdown
   source
   #js {:extensions #js [(directive)]
        :mdastExtensions #js [(directiveFromMarkdown)]}))

(defn serialize [tree]
  (toMarkdown tree #js {:extensions #js [(directiveToMarkdown)]}))

(defn children [node]
  (when (js/Array.isArray (.-children node))
    (array-seq (.-children node))))

(defn nodes [tree]
  (tree-seq #(some? (children %)) children tree))

(defn definition-list-directive? [node]
  (and (= "containerDirective" (.-type node))
       (= "definition-list" (.-name node))))

(defn definition-directive? [node]
  (and (= "containerDirective" (.-type node))
       (= "definition" (.-name node))))

(defn term-directive? [node]
  (and (= "leafDirective" (.-type node))
       (= "term" (.-name node))))

(defn definition-list-directives [tree]
  (filter definition-list-directive? (nodes tree)))

(defn definition-directives [definition-list]
  (filter definition-directive? (children definition-list)))

(defn term-directives [definition]
  (filter term-directive? (children definition)))

(defn body-children [definition]
  (remove term-directive? (children definition)))

(defn property [object & names]
  (reduce gobj/get object names))

(defn diagnostic [source-name node message]
  {:file source-name
   :line (property node "position" "start" "line")
   :column (property node "position" "start" "column")
   :directive (.-name node)
   :message message})

(defn term-text [term]
  (apply str (map #(or (.-value %) "") (children term))))

(defn term-validation-message [term]
  (cond
    (some #(not (contains? allowed-term-node-types (.-type %)))
          (children term))
    "`term`のラベルには通常テキストとインラインコードだけを指定できます。"

    (string/blank? (term-text term))
    "`term`には空でないラベルが必要です。"))

(defn validate [tree source-name]
  (->> (nodes tree)
       (filter term-directive?)
       (keep (fn [term]
               (when-let [message (term-validation-message term)]
                 (diagnostic source-name term message))))
       vec))

(defn html-node [value]
  #js {:type "html" :value value})

(declare inline-html)

(defn child-inline-html [node]
  (apply str (map inline-html (children node))))

(defn inline-html [node]
  (case (.-type node)
    "text" (gstring/htmlEscape (.-value node))
    "inlineCode" (str "<code>" (gstring/htmlEscape (.-value node)) "</code>")
    (throw (js/Error. (str "Unsupported definition term node: " (.-type node))))))

(defn term-html [term]
  (child-inline-html term))

(defn transformed-definition-nodes [definition]
  (concat
   [(html-node
     (str "<div class=\"clono-definition-item\">\n"
          (apply str
                 (map #(str "<dt>" (term-html %) "</dt>\n")
                      (term-directives definition)))
          "<dd>"))]
   (body-children definition)
   [(html-node "</dd>\n</div>")]))

(defn transformed-list-nodes [definition-list]
  (concat
   [(html-node "<dl class=\"clono-definition-list\">")]
   (mapcat transformed-definition-nodes
           (definition-directives definition-list))
   [(html-node "</dl>")]))

(defn transform [tree]
  (set! (.-children tree)
        (into-array
         (mapcat #(if (definition-list-directive? %)
                    (transformed-list-nodes %)
                    [%])
                 (children tree))))
  tree)

(defn transform-markdown [source source-name]
  (let [tree (parse source)
        diagnostics (validate tree source-name)]
    (if (seq diagnostics)
      {:ok? false
       :output nil
       :diagnostics diagnostics}
      {:ok? true
       :output (-> tree transform serialize)
       :diagnostics []})))
