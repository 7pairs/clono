(ns clono.research.markdown-ast
  (:require
   ["mdast-util-directive" :refer [directiveFromMarkdown directiveToMarkdown]]
   ["mdast-util-from-markdown" :refer [fromMarkdown]]
   ["mdast-util-to-markdown" :refer [toMarkdown]]
   ["micromark-extension-directive" :refer [directive]]))

(defn parse [markdown]
  (fromMarkdown
   markdown
   #js {:extensions #js [(directive)]
        :mdastExtensions #js [(directiveFromMarkdown)]}))

(defn serialize [tree]
  (toMarkdown
   tree
   #js {:extensions #js [(directiveToMarkdown)]}))

