(ns clono.transform.xref.table)

(def rule
  {:type "table"
   :class-names (fn [_target]
                  ["clono-xref-table"])
   :placeholder-texts
   {"number" "表X.X"
    "number-title" "表X.X 参照先未解決"
    "title" "参照先未解決"}})
