(ns clono.research.definition-list-directive
  (:require
   ["mdast-util-directive" :refer [directiveFromMarkdown directiveToMarkdown]]
   ["mdast-util-from-markdown" :refer [fromMarkdown]]
   ["mdast-util-to-markdown" :refer [toMarkdown]]
   ["micromark-extension-directive" :refer [directive]]
   [goog.object :as gobj]
   [goog.string :as gstring]))

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

(defn directive-label? [node]
  (and (some? (.-data node))
       (true? (gobj/get (.-data node) "directiveLabel"))))

(defn definition-list-directive? [node]
  (and (= "containerDirective" (.-type node))
       (= "definition-list" (.-name node))))

(defn definition-directive? [node]
  (and (= "containerDirective" (.-type node))
       (= "definition" (.-name node))))

(defn definition-list-directives [tree]
  (filter definition-list-directive? (nodes tree)))

(defn definition-directives [definition-list]
  (filter definition-directive? (children definition-list)))

(defn label-node [definition]
  (first (filter directive-label? (children definition))))

(defn body-children [definition]
  (remove directive-label? (children definition)))

(defn html-node [value]
  #js {:type "html" :value value})

(declare inline-html)

(defn child-inline-html [node]
  (apply str (map inline-html (children node))))

(defn inline-html [node]
  (case (.-type node)
    "text" (gstring/htmlEscape (.-value node))
    "inlineCode" (str "<code>" (gstring/htmlEscape (.-value node)) "</code>")
    "strong" (str "<strong>" (child-inline-html node) "</strong>")
    "emphasis" (str "<em>" (child-inline-html node) "</em>")
    (throw (js/Error. (str "Unsupported definition term node: " (.-type node))))))

(defn term-html [definition]
  (child-inline-html (label-node definition)))

(defn transformed-definition-nodes [definition]
  (concat
   [(html-node
     (str "<div class=\"clono-definition-item\">\n"
          "<dt>"
          (term-html definition)
          "</dt>\n"
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

(defn transformed-markdown [source]
  (-> source parse transform serialize))

