(ns clono.book.reference-resolution
  (:require
   [clono.diagnostic :as diagnostic]
   [clono.document-url :as document-url]
   [clono.transform :as transform]))

(defn- resolve-target [source-name target]
  (try
    (cond-> (assoc target
                   :href (document-url/relative-html-url
                          source-name
                          (:source-name target)
                          (:target-id target)))
      (:title-target-id target)
      (assoc :title-href
             (document-url/relative-html-url
              source-name
              (:source-name target)
              (:title-target-id target))))
    (catch :default _error
      (assoc target :resolution-error? true))))

(defn- resolve-manuscript [targets {:keys [tree context] :as manuscript}]
  (let [resolved-targets
        (mapv #(resolve-target (:source-name context) %) targets)
        resolved-context (assoc context :reference-targets resolved-targets)
        diagnostics
        (diagnostic/finalize
         (transform/reference-diagnostics tree resolved-context))]
    {:manuscript (assoc manuscript :context resolved-context)
     :diagnostics diagnostics}))

(defn resolve-references [manuscripts targets]
  (let [results (mapv #(resolve-manuscript targets %) manuscripts)
        diagnostics (into [] (mapcat :diagnostics) results)]
    (if (seq diagnostics)
      {:ok? false
       :manuscripts nil
       :diagnostics diagnostics}
      {:ok? true
       :manuscripts (mapv :manuscript results)
       :diagnostics []})))
