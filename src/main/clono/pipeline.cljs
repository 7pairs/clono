(ns clono.pipeline
  (:require
   [clono.diagnostic :as diagnostic]
   [clono.directive-syntax :as directive-syntax]
   [clono.directive-validation :as directive-validation]
   [clono.markdown :as markdown]
   [clono.reference-targets :as reference-targets]
   [clono.transform :as transform]
   [clono.transform.heading :as heading]))

(defn analyze [context source]
  (let [source-name (:source-name context)
        tree (markdown/parse source)
        diagnostics (diagnostic/finalize
                     (concat
                      (directive-syntax/diagnostics
                       source
                       tree
                       source-name
                       {:known-directive-names transform/known-directive-names
                        :required-text-attribute-names
                        transform/required-text-attribute-names})
                      (directive-validation/unknown-diagnostics
                       tree
                       source-name
                       transform/known-directive-names)
                      (heading/diagnostics source tree context)
                      (transform/validate tree (assoc context :source source))))]
    (if (seq diagnostics)
      {:ok? false
       :tree nil
       :source nil
       :diagnostics diagnostics}
      {:ok? true
       :tree tree
       :source source
       :diagnostics []})))

(defn run-analyzed [context tree]
  (let [provided-reference-targets? (contains? context :reference-targets)
        provided-index-entries? (contains? context :index-entries)
        collected-targets
        (if provided-reference-targets?
          (:reference-targets context)
          (transform/collect-reference-targets tree context))
        target-validation
        (if provided-reference-targets?
          {:ok? true
           :targets collected-targets
           :diagnostics []}
          (reference-targets/validate collected-targets))]
    (if-not (:ok? target-validation)
      {:ok? false
       :output nil
       :diagnostics (:diagnostics target-validation)}
      (let [collected-index-entries
            (if provided-index-entries?
              (:index-entries context)
              (transform/collect-index-entries tree context))
            index-validation
            (if provided-index-entries?
              {:ok? true
               :entries collected-index-entries
               :diagnostics []}
              (transform/prepare-index-entries
               collected-index-entries
               (:targets target-validation)))]
        (if-not (:ok? index-validation)
          {:ok? false
           :output nil
           :diagnostics (diagnostic/finalize
                         (:diagnostics index-validation))}
          (let [transformation-context
                (-> context
                    (assoc :reference-targets (:targets target-validation))
                    (transform/add-index-entries
                     (:entries index-validation)))
                reference-diagnostics
                (diagnostic/finalize
                 (transform/reference-diagnostics tree
                                                  transformation-context))]
            (if (seq reference-diagnostics)
              {:ok? false
               :output nil
               :diagnostics reference-diagnostics}
              {:ok? true
               :output (-> tree
                           (transform/transform transformation-context)
                           markdown/serialize)
               :diagnostics []})))))))

(defn run [context source]
  (let [analysis (analyze context source)]
    (if (:ok? analysis)
      (run-analyzed (assoc context :source (:source analysis))
                    (:tree analysis))
      {:ok? false
       :output nil
       :diagnostics (:diagnostics analysis)})))
