(ns clono.research.build-project
  (:require
   [clono.research.generated-index :as generated-index]))

(defn main []
  (let [result (generated-index/build-project! (.cwd js/process))]
    (println (str "Generated index in " (:output-directory result)))))
