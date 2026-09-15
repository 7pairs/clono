(ns clono.book.index
  (:require
   [clojure.string :as string]
   [clono.diagnostic :as diagnostic]
   [clono.index.generator :as generator]
   [clono.transform :as transform]))

(defn- published-markdown? [entry]
  (and (= :document (:type entry))
       (string/ends-with? (string/lower-case (:path entry)) ".md")))

(defn collect [publication manuscripts]
  (let [manuscripts-by-source
        (into {}
              (map (fn [{:keys [context] :as manuscript}]
                     [(:source-name context) manuscript]))
              manuscripts)]
    (->> publication
         (filter published-markdown?)
         (keep #(get manuscripts-by-source (:path %)))
         (mapcat (fn [{:keys [tree context]}]
                   (transform/collect-index-entries tree context)))
         vec)))

(defn- index-entry [publication]
  (first (filter #(= :index (:type %)) publication)))

(defn- entry-diagnostics [publication diagnostics]
  (let [source-ranks
        (->> publication
             (filter published-markdown?)
             (map-indexed (fn [index entry]
                            [(:path entry) index]))
             (into {}))]
    (->> diagnostics
         (sort-by (juxt #(get source-ranks (:file %) js/Number.MAX_SAFE_INTEGER)
                        #(get % diagnostic/offset-key js/Number.MAX_SAFE_INTEGER)))
         (mapv #(dissoc % diagnostic/offset-key)))))

(defn prepare [config manuscripts reference-targets]
  (let [publication (:publication config)
        entries (collect publication manuscripts)
        missing-index-diagnostics
        (if (and (seq entries) (nil? (index-entry publication)))
          [{:file (:config-path config)
            :message (str "`publication`には、掲載Markdownの索引指定を出力する"
                          "`index`が必要です。")}]
          [])
        preparation (transform/prepare-index-entries entries reference-targets)
        diagnostics
        (into missing-index-diagnostics
              (entry-diagnostics publication (:diagnostics preparation)))]
    (if (seq diagnostics)
      {:ok? false
       :entries nil
       :diagnostics diagnostics}
      {:ok? true
       :entries (:entries preparation)
       :diagnostics []})))

(defn add-entries [manuscripts entries]
  (mapv (fn [manuscript]
          (update manuscript :context transform/add-index-entries entries))
        manuscripts))

(defn generate [publication entries]
  (when-let [entry (index-entry publication)]
    {:path (:path entry)
     :content (generator/generate-markdown entry entries)}))
