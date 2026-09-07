(ns clono.transform.xref.heading)

(def ^:private heading-level-classes
  {1 "clono-xref-heading-h1"
   2 "clono-xref-heading-h2"
   3 "clono-xref-heading-h3"})

(def ^:private document-kind-classes
  {"chapter" "clono-xref-heading-chapter"
   "appendix" "clono-xref-heading-appendix"
   "frontmatter" "clono-xref-heading-unnumbered"
   "backmatter" "clono-xref-heading-unnumbered"})

(defn- heading-level-class [target]
  (get heading-level-classes (:heading-depth target)))

(defn- document-kind-class [target]
  (get document-kind-classes (:document-kind target)))

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
