(ns clono.index.marker)

(def id-prefix
  "clono-index-marker-")

(defn id [number]
  (str id-prefix number))

(defn reserved-id? [value]
  (and (string? value)
       (.startsWith value id-prefix)))
