(ns clono.diagnostic
  (:require
   [clono.ast :as ast]
   [goog.object :as gobj]))

(def offset-key ::offset)

(defn at-point [source-name directive-name point message]
  {:file source-name
   :line (gobj/get point "line")
   :column (gobj/get point "column")
   :directive directive-name
   :message message
   offset-key (gobj/get point "offset")})

(defn for-node [source-name node message]
  (at-point
   source-name
   (.-name node)
   (ast/property node "position" "start")
   message))

(defn finalize [diagnostics]
  (->> diagnostics
       (sort-by offset-key)
       (mapv #(dissoc % offset-key))))
