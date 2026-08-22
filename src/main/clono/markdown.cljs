(ns clono.markdown
  (:require
   ["mdast-util-directive" :refer [directiveFromMarkdown directiveToMarkdown]]
   ["mdast-util-from-markdown" :refer [fromMarkdown]]
   ["mdast-util-gfm-footnote" :refer [gfmFootnoteFromMarkdown gfmFootnoteToMarkdown]]
   ["mdast-util-gfm-table" :refer [gfmTableFromMarkdown gfmTableToMarkdown]]
   ["mdast-util-to-markdown" :refer [toMarkdown]]
   ["micromark-extension-directive" :refer [directive]]
   ["micromark-extension-gfm-footnote" :refer [gfmFootnote]]
   ["micromark-extension-gfm-table" :refer [gfmTable]]))

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
