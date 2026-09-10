(ns clono.research.listing-directive
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

(defn listing-directive? [node]
  (and (= "containerDirective" (.-type node))
       (= "listing" (.-name node))))

(defn listing-directives [tree]
  (filter listing-directive? (nodes tree)))

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

(defn transformed-nodes [directive]
  (let [code (first (body-children directive))
        id (gobj/get (.-attributes directive) "id")
        caption (node-text (label-node directive))
        escaped-id (gstring/htmlEscape id)
        escaped-caption (gstring/htmlEscape caption)]
    [(html-node
      (str "<figure class=\"clono-numbered-listing\" id=\"listing-"
           escaped-id
           "\">\n<figcaption class=\"clono-listing-caption\" id=\"listing-"
           escaped-id
           "-caption\">"
           escaped-caption
           "</figcaption>"))
     code
     (html-node "</figure>")]))

(defn transform [tree]
  (set! (.-children tree)
        (into-array
         (mapcat #(if (listing-directive? %)
                    (transformed-nodes %)
                    [%])
                 (children tree))))
  tree)

(defn transformed-markdown [source]
  (-> source parse transform serialize))
