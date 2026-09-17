(ns clono.research.generate
  (:require
   ["node:fs" :as fs]
   [clono.research.definition-list-directive :as definition-list-directive]))

(defn main []
  (let [source (.readFileSync fs "input/definition-list.md" "utf8")
        output (definition-list-directive/transformed-markdown source)]
    (.mkdirSync fs "output/generated" #js {:recursive true})
    (.writeFileSync fs "output/generated/manuscript.md" output "utf8")))
