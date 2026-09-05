(ns clono.book.reference-targets
  (:require
   [clono.reference-targets :as reference-targets]
   [clono.transform :as transform]))

(defn collect [manuscripts]
  (->> manuscripts
       (mapcat (fn [{:keys [tree context]}]
                 (transform/collect-reference-targets tree context)))
       reference-targets/validate))
