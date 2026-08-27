(ns clono.book.plan
  (:require
   ["node:fs" :as fs]
   ["node:path" :as path]
   [clojure.string :as string]))

(def ^:private reserved-root-names
  #{"_clono" ".clono-output.json"})

(defn- error-message [error]
  (or (.-message error) (str error)))

(defn- diagnostic [file-path message]
  {:file file-path
   :message message})

(defn- portable-path [segments]
  (string/join "/" segments))

(defn- operation [action source-path relative-path]
  {:action action
   :source-path source-path
   :path relative-path})

(declare walk-directory)

(defn- walk-entry [parent-path parent-segments entry]
  (let [name (.-name entry)
        segments (conj parent-segments name)
        relative-path (portable-path segments)
        source-path (.join path parent-path name)]
    (if (and (empty? parent-segments)
             (contains? reserved-root-names name))
      {:operations []
       :diagnostics
       [(diagnostic source-path
                    (str "入力原稿ルート直下の`" name "`はclonoの予約パスです。"))]}
      (try
        (let [entry-stat (.lstatSync fs source-path)]
          (cond
            (.isSymbolicLink entry-stat)
            {:operations []
             :diagnostics
             [(diagnostic source-path
                          (str "入力原稿ツリーにシンボリックリンクは使用できません: "
                               relative-path))]}

            (.isDirectory entry-stat)
            (let [descendants (walk-directory source-path segments)]
              {:operations (into [(operation :create-directory
                                             source-path
                                             relative-path)]
                                 (:operations descendants))
               :diagnostics (:diagnostics descendants)})

            (.isFile entry-stat)
            {:operations
             [(operation (if (= ".md" (.toLowerCase (.extname path name)))
                           :transform-markdown
                           :copy-file)
                         source-path
                         relative-path)]
             :diagnostics []}

            :else
            {:operations []
             :diagnostics
             [(diagnostic source-path
                          (str "入力原稿ツリーに対応していないファイル種別があります: "
                               relative-path))]}))
        (catch :default error
          {:operations []
           :diagnostics
           [(diagnostic source-path
                        (str "入力原稿ツリーの項目を確認できません: "
                             relative-path ": " (error-message error)))]})))))

(defn- walk-directory [directory-path segments]
  (try
    (reduce
     (fn [result entry]
       (let [entry-result (walk-entry directory-path
                                      segments
                                      entry)]
         {:operations (into (:operations result) (:operations entry-result))
          :diagnostics (into (:diagnostics result) (:diagnostics entry-result))}))
     {:operations []
      :diagnostics []}
     (sort-by #(.-name %) (array-seq (.readdirSync fs
                                                   directory-path
                                                   #js {:withFileTypes true}))))
    (catch :default error
      {:operations []
       :diagnostics
       [(diagnostic directory-path
                    (str "入力原稿ディレクトリを読み取れません: "
                         (or (not-empty (portable-path segments)) ".")
                         ": " (error-message error)))]})))

(defn- publication-diagnostics [config operations]
  (let [planned-files (->> operations
                           (remove #(= :create-directory (:action %)))
                           (map :path)
                           set)]
    (->> (:publication config)
         (remove #(contains? planned-files (:path %)))
         (mapv #(diagnostic
                 (:config-path config)
                 (str "`publication`の原稿を変換計画に含められません: "
                      (:path %)))))))

(defn create [config]
  (let [walk-result (walk-directory (:source-path config)
                                    [])
        diagnostics (into (:diagnostics walk-result)
                          (publication-diagnostics config
                                                   (:operations walk-result)))]
    (if (seq diagnostics)
      {:ok? false
       :plan nil
       :diagnostics diagnostics}
      {:ok? true
       :plan {:project-root (:project-root config)
              :source-root (:source-root config)
              :source-path (:source-path config)
              :output-root (:output-root config)
              :output-path (:output-path config)
              :publication (:publication config)
              :operations (:operations walk-result)}
       :diagnostics []})))
