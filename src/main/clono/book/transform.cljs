(ns clono.book.transform
  (:require
   ["node:fs" :as fs]
   [clono.pipeline :as pipeline]))

(defn- error-message [error]
  (or (.-message error) (str error)))

(defn- diagnostic [file-path message]
  {:file file-path
   :message message})

(defn- read-markdown [operation]
  (try
    {:ok? true
     :source (.readFileSync fs (:source-path operation) "utf8")}
    (catch :default error
      {:ok? false
       :diagnostics
       [(diagnostic (:path operation)
                    (str "Markdown原稿を読み込めません: "
                         (error-message error)))]})))

(defn- transform-markdown [operation source]
  (try
    (let [result (pipeline/run (:path operation) source)]
      (if (:ok? result)
        {:ok? true
         :operation (assoc operation :content (:output result))
         :diagnostics []}
        {:ok? false
         :operation nil
         :diagnostics (:diagnostics result)}))
    (catch :default error
      {:ok? false
       :operation nil
       :diagnostics
       [(diagnostic (:path operation)
                    (str "Markdown原稿を変換できません: "
                         (error-message error)))]})))

(defn- transform-operation [operation]
  (if-not (= :transform-markdown (:action operation))
    {:ok? true
     :operation operation
     :diagnostics []}
    (let [read-result (read-markdown operation)]
      (if (:ok? read-result)
        (transform-markdown operation (:source read-result))
        {:ok? false
         :operation nil
         :diagnostics (:diagnostics read-result)}))))

(defn run [plan]
  (let [result
        (reduce
         (fn [result operation]
           (let [operation-result (transform-operation operation)]
             {:operations (cond-> (:operations result)
                            (:operation operation-result)
                            (conj (:operation operation-result)))
              :diagnostics (into (:diagnostics result)
                                 (:diagnostics operation-result))}))
         {:operations []
          :diagnostics []}
         (:operations plan))]
    (if (seq (:diagnostics result))
      {:ok? false
       :plan nil
       :diagnostics (:diagnostics result)}
      {:ok? true
       :plan (assoc plan :operations (:operations result))
       :diagnostics []})))
