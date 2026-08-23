(ns clono.research.inspect
  (:require
   ["node:fs" :as fs]
   [clono.research.markdown-ast :as markdown-ast]))

(def default-input-path "input/candidate.md")

(defn main [& arguments]
  (let [input-path (or (first arguments) default-input-path)
        markdown (.readFileSync fs input-path "utf8")
        tree (markdown-ast/parse markdown)]
    (println (.stringify js/JSON tree nil 2))))

