(ns clono.transform.xref.listing)

(def rule
  {:type "listing"
   :class-names (fn [_target]
                  ["clono-xref-listing"])
   :placeholder-texts
   {"number" "リストX.X"
    "number-title" "リストX.X 参照先未解決"
    "title" "参照先未解決"}})
