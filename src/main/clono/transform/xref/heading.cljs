(ns clono.transform.xref.heading)

(defn- heading-level-class [target]
  (when-let [depth (:heading-depth target)]
    (str "clono-xref-heading-h" depth)))

(defn- document-kind-class [target]
  (when-let [kind (:document-kind target)]
    (str "clono-xref-heading-"
         (case kind
           "chapter" "chapter"
           "appendix" "appendix"
           "frontmatter" "unnumbered"
           "backmatter" "unnumbered"))))

(defn- class-names [target]
  (cond-> ["clono-xref-heading"]
    (some? target)
    (into (keep identity [(heading-level-class target)
                          (document-kind-class target)]))))

(def rule
  {:type "heading"
   :class-names class-names
   :placeholder-texts
   {"number" "見出し番号未解決"
    "number-title" "見出し参照先未解決"
    "title" "参照先未解決"}})
