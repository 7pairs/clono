(ns clono.pipeline
  (:require
   [clono.markdown :as markdown]
   [clono.transform :as transform]))

(defn run [source]
  (-> source
      markdown/parse
      transform/transform
      markdown/serialize))
