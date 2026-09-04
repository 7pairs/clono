(ns clono.research.inspect
  (:require
   ["node:fs" :as fs]
   [clono.research.heading-ids :as heading-ids]))

(def input-path "input/headings.md")

(defn main []
  (let [source (.readFileSync fs input-path "utf8")
        tree (heading-ids/parse source)]
    (println (.stringify js/JSON tree nil 2))))
