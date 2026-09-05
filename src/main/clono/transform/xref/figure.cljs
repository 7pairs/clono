(ns clono.transform.xref.figure)

(def rule
  {:type "figure"
   :class-names (fn [_target]
                  ["clono-xref-figure"])
   :placeholder-texts
   {"number" "図X.X"
    "number-title" "図X.X 参照先未解決"
    "title" "参照先未解決"}})
