(ns clono.pipeline
  (:require
   [clono.diagnostic :as diagnostic]
   [clono.directive-syntax :as directive-syntax]
   [clono.directive-validation :as directive-validation]
   [clono.markdown :as markdown]
   [clono.transform :as transform]))

(defn run [source-name source]
  (let [tree (markdown/parse source)
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
                      (transform/validate tree source-name)))]
    (if (seq diagnostics)
      {:ok? false
       :output nil
       :diagnostics diagnostics}
      {:ok? true
       :output (-> tree transform/transform markdown/serialize)
       :diagnostics []})))
