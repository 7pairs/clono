(ns clono.reference-targets
  (:require
   [clono.index.marker :as index-marker]))

(defn- heading-target? [target]
  (= "heading" (:type target)))

(defn- target-subject [target]
  (if (heading-target? target)
    "見出し"
    (str "`" (:type target) "`")))

(defn- target-diagnostic [target message]
  (cond-> {:file (:source-name target)
           :line (:line target)
           :column (:column target)
           :message message}
    (not (heading-target? target))
    (assoc :directive (:type target))))

(defn- generated-html-ids [target]
  (distinct (keep target [:target-id :title-target-id])))

(defn- reserved-index-marker-id [generated-ids]
  (first (filter index-marker/reserved-id?
                 generated-ids)))

(defn- collect-target
  [{:keys [logical-ids html-ids] :as result} target]
  (let [logical-id (:logical-id target)
        subject (target-subject target)
        generated-ids (generated-html-ids target)
        reserved-id (reserved-index-marker-id generated-ids)]
    (if (contains? logical-ids logical-id)
      (-> result
          (update :targets conj target)
          (update :diagnostics
                  conj
                  (target-diagnostic
                   target
                   (str subject "の論理ID`" logical-id
                        "`が重複しています。"))))
      (let [collision (first (filter #(contains? html-ids %) generated-ids))]
        (cond-> (-> result
                    (update :targets conj target)
                    (update :logical-ids conj logical-id)
                    (update :html-ids into generated-ids))
          reserved-id
          (update :diagnostics
                  conj
                  (target-diagnostic
                   target
                   (str subject "のHTML ID`" reserved-id
                        "`にはclonoの予約接頭辞`"
                        index-marker/id-prefix
                        "`を使用できません。")))

          collision
          (update :diagnostics
                  conj
                  (target-diagnostic
                   target
                   (if (heading-target? target)
                     (str "見出しのHTML ID`" collision
                          "`が重複しています。")
                     (str subject "から生成するHTML ID`" collision
                          "`が重複しています。")))))))))

(defn validate [targets]
  (let [result
        (reduce collect-target
                {:targets []
                 :logical-ids #{}
                 :html-ids #{}
                 :diagnostics []}
                targets)
        diagnostics (:diagnostics result)]
    (if (seq diagnostics)
      {:ok? false
       :targets nil
       :diagnostics diagnostics}
      {:ok? true
       :targets (:targets result)
       :diagnostics []})))
