(ns clono.test-support)

(def markdown-source
  (str "# 最小パイプライン\n\n"
       ":::column{title=\"コラム\"}\n"
       "**本文**と脚注[^note]です。\n"
       ":::\n\n"
       "::page-break\n\n"
       "これは:index[索引項目]{reading=\"さくいんこうもく\"}です。\n\n"
       "```markdown\n"
       ":index[コード内]{reading=\"こおとない\"}\n"
       "```\n\n"
       "[^note]: 脚注の`コード`です。\n"))

(def standard-markdown-source
  (str "# 最小パイプライン\n\n"
       "**本文**と脚注[^note]です。\n\n"
       "```markdown\n"
       ":index[コード内]{reading=\"こおとない\"}\n"
       "```\n\n"
       "[^note]: 脚注の`コード`です。\n"))

(defn children [node]
  (when (js/Array.isArray (.-children node))
    (array-seq (.-children node))))

(defn nodes [tree]
  (tree-seq #(some? (children %)) children tree))

(defn nodes-by-type [tree type]
  (filter #(= type (.-type %)) (nodes tree)))

(defn directive [tree name]
  (first (filter #(= name (.-name %)) (nodes tree))))
