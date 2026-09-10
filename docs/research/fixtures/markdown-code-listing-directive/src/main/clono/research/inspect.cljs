(ns clono.research.inspect
  (:require
   ["node:fs" :as fs]
   [clono.research.listing-directive :as listing-directive]))

(def input-path "input/numbered-listings.md")

(defn main []
  (let [source (.readFileSync fs input-path "utf8")
        tree (listing-directive/parse source)]
    (println "AST:")
    (println (.stringify js/JSON tree nil 2))
    (println "\nTransformed Markdown:")
    (println (-> tree listing-directive/transform listing-directive/serialize))))
