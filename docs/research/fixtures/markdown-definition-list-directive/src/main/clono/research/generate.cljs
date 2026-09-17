(ns clono.research.generate
  (:require
   ["node:fs" :as fs]
   [clono.research.definition-list-directive :as definition-list-directive]))

(defn main []
  (let [source (.readFileSync fs "input/definition-list.md" "utf8")
        result (definition-list-directive/transform-markdown
                source
                "input/definition-list.md")]
    (if (:ok? result)
      (do
        (.mkdirSync fs "output/generated" #js {:recursive true})
        (.writeFileSync
         fs
         "output/generated/manuscript.md"
         (:output result)
         "utf8"))
      (throw (ex-info "Definition list fixture transformation failed"
                      {:diagnostics (:diagnostics result)})))))
