(ns clono.pipeline
  (:require
   [clono.diagnostic :as diagnostic]
   [clono.directive-syntax :as directive-syntax]
   [clono.directive-validation :as directive-validation]
   [clono.markdown :as markdown]
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
                      (transform/validate tree context)))]
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
  (let [reference-targets
        (if (contains? context :reference-targets)
          (:reference-targets context)
          (transform/collect-reference-targets tree context))
        transformation-context
        (assoc context :reference-targets reference-targets)
        reference-diagnostics
        (diagnostic/finalize
         (transform/reference-diagnostics tree transformation-context))]
    (if (seq reference-diagnostics)
      {:ok? false
       :output nil
       :diagnostics reference-diagnostics}
      {:ok? true
       :output (-> tree
                   (transform/transform transformation-context)
                   markdown/serialize)
       :diagnostics []})))

(defn run [context source]
  (let [analysis (analyze context source)]
    (if (:ok? analysis)
      (run-analyzed (assoc context :source (:source analysis))
                    (:tree analysis))
      {:ok? false
       :output nil
       :diagnostics (:diagnostics analysis)})))
