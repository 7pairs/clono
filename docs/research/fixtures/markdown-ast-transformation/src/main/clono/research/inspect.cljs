(ns clono.research.inspect
  (:require
   ["node:fs" :as fs]
   [clono.research.transformer :as transformer]))

(def default-input-path "input/valid.md")

(defn main [& arguments]
  (let [input-path (or (first arguments) default-input-path)
        markdown (.readFileSync fs input-path "utf8")
        result (transformer/transform markdown input-path)]
    (if (:ok? result)
      (println (:output result))
      (do
        (js/console.error (.stringify js/JSON (clj->js (:diagnostics result)) nil 2))
        (set! (.-exitCode js/process) 1)))))

