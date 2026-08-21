(ns clono.research.markdown-ast
  (:require
   ["mdast-util-directive" :refer [directiveFromMarkdown directiveToMarkdown]]
   ["mdast-util-from-markdown" :refer [fromMarkdown]]
   ["mdast-util-gfm-footnote" :refer [gfmFootnoteFromMarkdown gfmFootnoteToMarkdown]]
   ["mdast-util-to-markdown" :refer [toMarkdown]]
   ["micromark-extension-directive" :refer [directive]]
   ["micromark-extension-gfm-footnote" :refer [gfmFootnote]]))

(defn parse [markdown]
  (fromMarkdown
   markdown
   #js {:extensions #js [(directive) (gfmFootnote)]
        :mdastExtensions #js [(directiveFromMarkdown)
                              (gfmFootnoteFromMarkdown)]}))

(defn serialize [tree]
  (toMarkdown
   tree
   #js {:extensions #js [(directiveToMarkdown)
                         (gfmFootnoteToMarkdown)]}))

