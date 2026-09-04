(ns clono.book.reference-targets
  (:require
   [clono.transform :as transform]))

(defn- target-diagnostic [target message]
  {:file (:source-name target)
   :line (:line target)
   :column (:column target)
   :directive (:type target)
   :message message})

(defn- generated-html-ids [target]
  (keep target [:target-id :title-target-id]))

(defn- collect-target
  [{:keys [logical-ids html-ids] :as result} target]
  (let [logical-id (:logical-id target)
        target-type (:type target)
        generated-ids (generated-html-ids target)]
    (cond
      (contains? logical-ids logical-id)
      (-> result
          (update :targets conj target)
          (update :diagnostics
                  conj
                  (target-diagnostic
                   target
                   (str "`" target-type "`の論理ID`" logical-id
                        "`が重複しています。"))))

      :else
      (let [collision (first (filter #(contains? html-ids %) generated-ids))]
        (cond-> (-> result
                    (update :targets conj target)
                    (update :logical-ids conj logical-id)
                    (update :html-ids into generated-ids))
          collision
          (update :diagnostics
                  conj
                  (target-diagnostic
                   target
                   (str "`" target-type "`から生成するHTML ID`"
                        collision "`が重複しています。"))))))))

(defn collect [manuscripts]
  (let [result
        (reduce
         (fn [result {:keys [tree context]}]
           (reduce collect-target
                   result
                   (transform/collect-reference-targets tree context)))
         {:targets []
          :logical-ids #{}
          :html-ids #{}
          :diagnostics []}
         manuscripts)
        diagnostics (:diagnostics result)]
    (if (seq diagnostics)
      {:ok? false
       :targets nil
       :diagnostics diagnostics}
      {:ok? true
       :targets (:targets result)
       :diagnostics []})))
