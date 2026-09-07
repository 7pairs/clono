(ns clono.reference-id)

(def pattern
  #"^[a-z][a-z0-9-]*$")

(defn valid? [value]
  (and (string? value)
       (boolean (re-matches pattern value))))
