(ns clono.research.inspect
  (:require
   ["node:fs" :as fs]
   [clono.research.table-directive :as table-directive]))

(def input-path "input/numbered-table.md")

(defn main []
  (let [source (.readFileSync fs input-path "utf8")
        tree (table-directive/parse source)]
    (println "AST:")
    (println (.stringify js/JSON tree nil 2))
    (println "\nTransformed Markdown:")
    (println (-> tree table-directive/transform table-directive/serialize))))

