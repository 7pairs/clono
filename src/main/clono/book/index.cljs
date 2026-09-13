(ns clono.book.index
  (:require
   [clojure.string :as string]
   [clono.transform :as transform]))

(defn- published-markdown? [entry]
  (and (= :document (:type entry))
       (string/ends-with? (string/lower-case (:path entry)) ".md")))

(defn collect [publication manuscripts]
  (let [manuscripts-by-source
        (into {}
              (map (fn [{:keys [context] :as manuscript}]
                     [(:source-name context) manuscript]))
              manuscripts)]
    (->> publication
         (filter published-markdown?)
         (keep #(get manuscripts-by-source (:path %)))
         (mapcat (fn [{:keys [tree context]}]
                   (transform/collect-index-entries tree context)))
         vec)))
