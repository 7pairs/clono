(ns clono.book.generate
  (:require
   ["node:fs" :as fs]
   ["node:path" :as path]
   [clojure.string :as string]))

(def ^:private marker-name ".clono-output.json")
(def ^:private stylesheet-relative-path "_clono/styles/clono.css")

(defn- error-message [error]
  (or (.-message error) (str error)))

(defn- diagnostic [file-path message]
  {:file file-path
   :message message})

(defn ^:no-doc stylesheet-path []
  (.resolve path js/__dirname ".." "styles" "clono.css"))

(defn- portable-to-native [portable-path]
  (string/replace portable-path "/" (.-sep path)))

(defn- descendant-path [root portable-path]
  (let [candidate (.resolve path root (portable-to-native portable-path))
        relative (.relative path root candidate)]
    (when (and (not (empty? relative))
               (not= relative "..")
               (not (.startsWith relative (str ".." (.-sep path))))
               (not (.isAbsolute path relative)))
      candidate)))

(defn- prepare-generation-root [generation-path]
  (try
    (if (.existsSync fs generation-path)
      (let [generation-stat (.lstatSync fs generation-path)]
        (cond
          (.isSymbolicLink generation-stat)
          {:ok? false
           :diagnostics
           [(diagnostic generation-path
                        "生成先にシンボリックリンクは使用できません。")]}

          (not (.isDirectory generation-stat))
          {:ok? false
           :diagnostics
           [(diagnostic generation-path
                        "生成先にはディレクトリを指定してください。")]}

          (seq (array-seq (.readdirSync fs generation-path)))
          {:ok? false
           :diagnostics
           [(diagnostic generation-path
                        "生成先には存在しないパスまたは空のディレクトリを指定してください。")]}

          :else
          {:ok? true
           :diagnostics []}))
      (do
        (.mkdirSync fs generation-path #js {:recursive true})
        {:ok? true
         :diagnostics []}))
    (catch :default error
      {:ok? false
       :diagnostics
       [(diagnostic generation-path
                    (str "生成先を準備できません: "
                         (error-message error)))]})))

(defn- operation-destination [generation-path operation]
  (if-let [destination (descendant-path generation-path (:path operation))]
    {:ok? true
     :destination destination
     :diagnostics []}
    {:ok? false
     :diagnostics
     [(diagnostic (:path operation)
                  "変換計画の出力パスは生成先の内側でなければなりません。")]}))

(defn- execute-operation [generation-path operation]
  (let [destination-result (operation-destination generation-path operation)]
    (if-not (:ok? destination-result)
      destination-result
      (let [destination (:destination destination-result)]
        (try
          (case (:action operation)
            :create-directory
            (.mkdirSync fs destination)

            :transform-markdown
            (.writeFileSync fs
                            destination
                            (:content operation)
                            #js {:encoding "utf8" :flag "wx"})

            :copy-file
            (.copyFileSync fs
                           (:source-path operation)
                           destination
                           (.-COPYFILE_EXCL (.-constants fs)))

            (throw (js/Error.
                    (str "対応していない変換計画の操作です: "
                         (name (:action operation))))))
          {:ok? true
           :diagnostics []}
          (catch :default error
            {:ok? false
             :diagnostics
             [(diagnostic (:path operation)
                          (str "生成物を作成できません: "
                               (error-message error)))]}))))))

(defn- execute-operations [generation-path operations]
  (loop [remaining (seq operations)]
    (if-let [operation (first remaining)]
      (let [result (execute-operation generation-path operation)]
        (if (:ok? result)
          (recur (next remaining))
          result))
      {:ok? true
       :diagnostics []})))

(defn- copy-stylesheet [generation-path]
  (let [source (stylesheet-path)
        destination (descendant-path generation-path stylesheet-relative-path)]
    (try
      (.mkdirSync fs (.dirname path destination) #js {:recursive true})
      (.copyFileSync fs
                     source
                     destination
                     (.-COPYFILE_EXCL (.-constants fs)))
      {:ok? true
       :diagnostics []}
      (catch :default error
        {:ok? false
         :diagnostics
         [(diagnostic source
                      (str "clono基盤CSSを生成できません: "
                           (error-message error)))]}))))

(defn- publication-diagnostics [generation-path publication]
  (reduce
   (fn [diagnostics entry]
     (let [entry-path (:path entry)
           generated-path (descendant-path generation-path entry-path)]
       (if-not generated-path
         (conj diagnostics
               (diagnostic entry-path
                           "`publication`の原稿パスが生成先の外側を指しています。"))
         (try
           (let [entry-stat (when (.existsSync fs generated-path)
                              (.lstatSync fs generated-path))]
             (if (and entry-stat (.isFile entry-stat))
               diagnostics
               (conj diagnostics
                     (diagnostic entry-path
                                 "`publication`の原稿を生成できませんでした。"))))
           (catch :default error
             (conj diagnostics
                   (diagnostic entry-path
                               (str "`publication`の生成済み原稿を確認できません: "
                                    (error-message error)))))))))
   []
   publication))

(defn- verify-publication [generation-path publication]
  (let [diagnostics (publication-diagnostics generation-path publication)]
    {:ok? (empty? diagnostics)
     :diagnostics diagnostics}))

(defn- marker-content [plan]
  (str
   (js/JSON.stringify
    (clj->js
     (array-map
      "format" 1
      "producer" "clono"
      "sourceRoot" (:source-root plan)
      "outputRoot" (:output-root plan)))
    nil
    2)
   "\n"))

(defn- write-marker [generation-path plan]
  (let [marker-path (.join path generation-path marker-name)]
    (try
      (.writeFileSync fs
                      marker-path
                      (marker-content plan)
                      #js {:encoding "utf8" :flag "wx"})
      {:ok? true
       :diagnostics []}
      (catch :default error
        {:ok? false
         :diagnostics
         [(diagnostic marker-path
                      (str "所有マーカーを生成できません: "
                           (error-message error)))]}))))

(defn- failure-result [diagnostics]
  {:ok? false
   :generated-path nil
   :diagnostics diagnostics})

(defn run [plan generation-root]
  (let [generation-path (.resolve path generation-root)
        root-result (prepare-generation-root generation-path)]
    (if-not (:ok? root-result)
      (failure-result (:diagnostics root-result))
      (loop [remaining [(fn []
                         (execute-operations generation-path
                                             (:operations plan)))
                        (fn [] (copy-stylesheet generation-path))
                        (fn []
                          (verify-publication generation-path
                                              (:publication plan)))
                        (fn [] (write-marker generation-path plan))]]
        (if-let [step (first remaining)]
          (let [result (step)]
            (if (:ok? result)
              (recur (next remaining))
              (failure-result (:diagnostics result))))
          {:ok? true
           :generated-path generation-path
           :diagnostics []})))))
