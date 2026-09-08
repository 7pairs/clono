(ns clono.research.table-directive
  (:require
   ["mdast-util-directive" :refer [directiveFromMarkdown directiveToMarkdown]]
   ["mdast-util-from-markdown" :refer [fromMarkdown]]
   ["mdast-util-gfm-table" :refer [gfmTableFromMarkdown gfmTableToMarkdown]]
   ["mdast-util-to-markdown" :refer [toMarkdown]]
   ["micromark-extension-directive" :refer [directive]]
   ["micromark-extension-gfm-table" :refer [gfmTable]]
   [goog.object :as gobj]
   [goog.string :as gstring]))

(defn parse [source]
  (fromMarkdown
   source
   #js {:extensions #js [(directive) (gfmTable)]
        :mdastExtensions #js [(directiveFromMarkdown)
                              (gfmTableFromMarkdown)]}))

(defn serialize [tree]
  (toMarkdown
   tree
   #js {:extensions #js [(directiveToMarkdown)
                         (gfmTableToMarkdown)]}))

(defn children [node]
  (when (js/Array.isArray (.-children node))
    (array-seq (.-children node))))

(defn nodes [tree]
  (tree-seq #(some? (children %)) children tree))

(defn directive-label? [node]
  (and (some? (.-data node))
       (true? (gobj/get (.-data node) "directiveLabel"))))

(defn directive-node [tree]
  (first (filter #(and (= "containerDirective" (.-type %))
                       (= "table" (.-name %)))
                 (nodes tree))))

(defn label-node [directive]
  (first (filter directive-label? (children directive))))

(defn body-children [directive]
  (remove directive-label? (children directive)))

(defn node-text [node]
  (if (string? (.-value node))
    (.-value node)
    (apply str (map node-text (children node)))))

(defn html-node [value]
  #js {:type "html" :value value})

(defn transform [tree]
  (let [directive (directive-node tree)
        table (first (body-children directive))
        id (gobj/get (.-attributes directive) "id")
        caption (node-text (label-node directive))]
    (set! (.-children tree)
          (into-array
           [(html-node
             (str "<figure class=\"clono-numbered-table\" id=\"table-"
                  (gstring/htmlEscape id)
                  "\">"))
            table
            (html-node
             (str "<figcaption class=\"clono-table-caption\" id=\"table-"
                  (gstring/htmlEscape id)
                  "-caption\">"
                  (gstring/htmlEscape caption)
                  "</figcaption>\n</figure>"))]))
    tree))

(defn transformed-markdown [source]
  (-> source parse transform serialize))

