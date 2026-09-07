(ns clono.research.heading-ids
  (:require
   ["mdast-util-directive" :refer [directiveFromMarkdown directiveToMarkdown]]
   ["mdast-util-from-markdown" :refer [fromMarkdown]]
   ["mdast-util-gfm-footnote" :refer [gfmFootnoteFromMarkdown gfmFootnoteToMarkdown]]
   ["mdast-util-gfm-table" :refer [gfmTableFromMarkdown gfmTableToMarkdown]]
   ["mdast-util-to-markdown" :refer [toMarkdown]]
   ["micromark-extension-directive" :refer [directive]]
   ["micromark-extension-gfm-footnote" :refer [gfmFootnote]]
   ["micromark-extension-gfm-table" :refer [gfmTable]]))

(def logical-id-pattern
  #"^[a-z][a-z0-9-]*$")

(def heading-id-suffix-pattern
  #"[ \t]+\{#([^{}]*)\}[ \t]*$")

(defn parse [source]
  (fromMarkdown
   source
   #js {:extensions #js [(directive) (gfmFootnote) (gfmTable)]
        :mdastExtensions #js [(directiveFromMarkdown)
                              (gfmFootnoteFromMarkdown)
                              (gfmTableFromMarkdown)]}))

(defn serialize [tree]
  (toMarkdown
   tree
   #js {:extensions #js [(directiveToMarkdown)
                         (gfmFootnoteToMarkdown)
                         (gfmTableToMarkdown)]}))

(defn children [node]
  (when (js/Array.isArray (.-children node))
    (array-seq (.-children node))))

(defn headings [tree]
  (filter #(= "heading" (.-type %)) (children tree)))

(defn explicit-id-candidate [heading]
  (when-let [last-child (last (children heading))]
    (when (= "text" (.-type last-child))
      (when-let [[suffix value]
                 (re-find heading-id-suffix-pattern (.-value last-child))]
        {:value value
         :valid? (boolean (re-matches logical-id-pattern value))
         :suffix suffix}))))
