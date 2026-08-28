(ns clono.book.publish
  (:require
   ["node:crypto" :as crypto]
   ["node:fs" :as fs]
   ["node:path" :as path]
   [clono.book.generate :as generate]
   [goog.object :as gobj]))

(def ^:private marker-name ".clono-output.json")

(defn- error-message [error]
  (or (.-message error) (str error)))

(defn- diagnostic [file-path message]
  {:file file-path
   :message message})

(defn- failure-result [diagnostics]
  {:ok? false
   :output-path nil
   :diagnostics diagnostics})

(defn- lstat-if-present [file-path]
  (try
    (.lstatSync fs file-path)
    (catch :default error
      (if (= "ENOENT" (.-code error))
        nil
        (throw error)))))

(defn- output-paths [output-path]
  (let [output-parent (.dirname path output-path)
        output-name (.basename path output-path)]
    {:output-parent output-parent
     :output-name output-name
     :lock-path (.join path output-parent
                       (str "." output-name ".clono-lock"))
     :staging-prefix (.join path output-parent
                            (str "." output-name ".clono-staging-"))
     :backup-prefix (.join path output-parent
                           (str "." output-name ".clono-backup-"))}))

(defn- prepare-output-parent [output-parent]
  (try
    (.mkdirSync fs output-parent #js {:recursive true})
    (let [parent-stat (.lstatSync fs output-parent)]
      (cond
        (.isSymbolicLink parent-stat)
        {:ok? false
         :diagnostics
         [(diagnostic output-parent
                      "出力先の親ディレクトリにシンボリックリンクは使用できません。")]}

        (not (.isDirectory parent-stat))
        {:ok? false
         :diagnostics
         [(diagnostic output-parent
                      "出力先の親パスにはディレクトリを指定してください。")]}

        :else
        {:ok? true
         :diagnostics []}))
    (catch :default error
      {:ok? false
       :diagnostics
       [(diagnostic output-parent
                    (str "出力先の親ディレクトリを準備できません: "
                         (error-message error)))]})))

(defn- expected-marker? [marker plan]
  (and (= 1 (gobj/get marker "format"))
       (= "clono" (gobj/get marker "producer"))
       (= (:source-root plan) (gobj/get marker "sourceRoot"))
       (= (:output-root plan) (gobj/get marker "outputRoot"))))

(defn- inspect-marker [plan output-path]
  (let [marker-path (.join path output-path marker-name)]
    (try
      (let [marker-stat (lstat-if-present marker-path)]
        (if (or (nil? marker-stat)
                (.isSymbolicLink marker-stat)
                (not (.isFile marker-stat)))
          {:ok? false
           :diagnostics
           [(diagnostic output-path
                        "空でない生成済み原稿ルートに有効な所有マーカーがありません。")]}
          (let [marker (js/JSON.parse (.readFileSync fs marker-path "utf8"))]
            (if (expected-marker? marker plan)
              {:ok? true
               :state :owned
               :diagnostics []}
              {:ok? false
               :diagnostics
               [(diagnostic marker-path
                            "所有マーカーが現在の書籍プロジェクトと一致しません。")]}))))
      (catch :default error
        {:ok? false
         :diagnostics
         [(diagnostic marker-path
                      (str "所有マーカーを読み取れません: "
                           (error-message error)))]}))))

(defn- inspect-output [plan output-path]
  (try
    (if-let [output-stat (lstat-if-present output-path)]
      (cond
        (.isSymbolicLink output-stat)
        {:ok? false
         :diagnostics
         [(diagnostic output-path
                      "生成済み原稿ルートにシンボリックリンクは使用できません。")]}

        (not (.isDirectory output-stat))
        {:ok? false
         :diagnostics
         [(diagnostic output-path
                      "生成済み原稿ルートにはディレクトリパスを指定してください。")]}

        (empty? (array-seq (.readdirSync fs output-path)))
        {:ok? true
         :state :empty
         :diagnostics []}

        :else
        (inspect-marker plan output-path))
      {:ok? true
       :state :missing
       :diagnostics []})
    (catch :default error
      {:ok? false
       :diagnostics
       [(diagnostic output-path
                    (str "生成済み原稿ルートの状態を確認できません: "
                         (error-message error)))]})))

(defn ^:no-doc rename-path! [source destination]
  (.renameSync fs source destination))

(defn ^:no-doc remove-staging! [staging-path]
  (.rmSync fs staging-path #js {:recursive true :force true}))

(defn ^:no-doc remove-backup! [backup-path]
  (.rmSync fs backup-path #js {:recursive true}))

(defn ^:no-doc acquire-lock! [lock-path]
  (.mkdirSync fs lock-path))

(defn ^:no-doc remove-lock! [lock-path]
  (.rmdirSync fs lock-path))

(defn- backup-path [backup-prefix]
  (str backup-prefix (.randomUUID crypto)))

(defn- publish-missing [staging-path output-path]
  (try
    (rename-path! staging-path output-path)
    {:ok? true
     :diagnostics []}
    (catch :default error
      {:ok? false
       :diagnostics
       [(diagnostic output-path
                    (str "生成済み原稿ツリーを公開できません: "
                         (error-message error)))]})))

(defn- publish-empty [staging-path output-path]
  (try
    (.rmdirSync fs output-path)
    (rename-path! staging-path output-path)
    {:ok? true
     :diagnostics []}
    (catch :default error
      {:ok? false
       :diagnostics
       [(diagnostic output-path
                    (str "空の生成済み原稿ルートを初期化できません: "
                         (error-message error)))]})))

(defn- restore-backup [backup-path output-path publish-error]
  (try
    (rename-path! backup-path output-path)
    {:ok? false
     :diagnostics
     [(diagnostic output-path
                  (str "生成済み原稿ツリーを公開できなかったため、既存出力を復元しました: "
                       (error-message publish-error)))]}
    (catch :default restore-error
      {:ok? false
       :diagnostics
       [(diagnostic output-path
                    (str "生成済み原稿ツリーを公開できず、既存出力も復元できませんでした。"
                         "既存出力は`" backup-path "`に残っています: "
                         (error-message publish-error) "; 復元エラー: "
                         (error-message restore-error)))]})))

(defn- publish-owned [staging-path output-path backup-prefix]
  (let [backup-path (backup-path backup-prefix)]
    (try
      (rename-path! output-path backup-path)
      (try
        (rename-path! staging-path output-path)
        (try
          (remove-backup! backup-path)
          {:ok? true
           :diagnostics []}
          (catch :default error
            {:ok? false
             :diagnostics
             [(diagnostic backup-path
                          (str "新しい生成済み原稿ツリーは公開しましたが、"
                               "以前の出力を削除できません: "
                               (error-message error)))]}))
        (catch :default error
          (restore-backup backup-path output-path error)))
      (catch :default error
        {:ok? false
         :diagnostics
         [(diagnostic output-path
                      (str "既存の生成済み原稿ツリーを退避できません: "
                           (error-message error)))]}))))

(defn- publish-generated [staging-path output-path backup-prefix state]
  (case state
    :missing (publish-missing staging-path output-path)
    :empty (publish-empty staging-path output-path)
    :owned (publish-owned staging-path output-path backup-prefix)
    {:ok? false
     :diagnostics
     [(diagnostic output-path
                  (str "未対応の生成済み原稿ルートの状態です: " state))]}))

(defn- acquire-output-lock [lock-path]
  (try
    (acquire-lock! lock-path)
    {:ok? true
     :diagnostics []}
    (catch :default error
      {:ok? false
       :diagnostics
       [(diagnostic lock-path
                    (if (= "EEXIST" (.-code error))
                      "別のclonoプロセスが生成済み原稿ルートを処理しています。"
                      (str "生成済み原稿ルートの排他ロックを取得できません: "
                           (error-message error))))]})))

(defn- with-output-lock [lock-path action]
  (let [lock-result (acquire-output-lock lock-path)]
    (if-not (:ok? lock-result)
      lock-result
      (let [action-result
            (try
              (action)
              (catch :default error
                {:ok? false
                 :diagnostics
                 [(diagnostic lock-path
                              (str "生成済み原稿ツリーの公開処理に失敗しました: "
                                   (error-message error)))]}))
            lock-removal-diagnostics
            (try
              (remove-lock! lock-path)
              []
              (catch :default error
                [(diagnostic lock-path
                             (str (when (:ok? action-result)
                                    "生成済み原稿ツリーは公開しましたが、")
                                  "排他ロックを削除できません: "
                                  (error-message error)))]))
            diagnostics (into (:diagnostics action-result)
                              lock-removal-diagnostics)]
        {:ok? (and (:ok? action-result) (empty? lock-removal-diagnostics))
         :diagnostics diagnostics}))))

(defn- cleanup-failure [result staging-path]
  (if (:ok? result)
    result
    (let [cleanup-diagnostics
          (try
            (remove-staging! staging-path)
            []
            (catch :default error
              [(diagnostic staging-path
                           (str "stagingディレクトリを削除できません: "
                                (error-message error)))]))]
      (failure-result (into (:diagnostics result) cleanup-diagnostics)))))

(defn- publish-staging [plan output-path lock-path backup-prefix staging-path]
  (try
    (let [generation-result (generate/run plan staging-path)]
      (if-not (:ok? generation-result)
        (cleanup-failure generation-result staging-path)
        (let [publication-result
              (with-output-lock
                lock-path
                (fn []
                  (let [output-result (inspect-output plan output-path)]
                    (if (:ok? output-result)
                      (publish-generated staging-path
                                         output-path
                                         backup-prefix
                                         (:state output-result))
                      output-result))))]
          (if (:ok? publication-result)
            {:ok? true
             :output-path output-path
             :diagnostics []}
            (cleanup-failure publication-result staging-path)))))
    (catch :default error
      (cleanup-failure
       (failure-result
        [(diagnostic output-path
                     (str "生成済み原稿ツリーを公開できません: "
                          (error-message error)))])
       staging-path))))

(defn run [plan]
  (let [output-path (.resolve path (:output-path plan))
        {:keys [output-parent lock-path staging-prefix backup-prefix]}
        (output-paths output-path)
        parent-result (prepare-output-parent output-parent)]
    (if-not (:ok? parent-result)
      (failure-result (:diagnostics parent-result))
      (try
        (let [staging-path (.mkdtempSync fs staging-prefix)]
          (publish-staging plan
                           output-path
                           lock-path
                           backup-prefix
                           staging-path))
        (catch :default error
          (failure-result
           [(diagnostic output-path
                        (str "生成済み原稿ツリーを公開できません: "
                             (error-message error)))]))))))
