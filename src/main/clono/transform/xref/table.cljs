(ns clono.transform.xref.table)

(def rule
  {:type "table"
   :class-names (fn [_target]
                  ["clono-xref-table"])})
