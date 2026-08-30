(ns clono.cli
  (:require
   ["node:crypto" :as crypto]
   ["node:fs" :as fs]
   ["node:path" :as path]
   [clojure.string :as string]
   [clono.book.config :as book-config]
   [clono.book.plan :as book-plan]
   [clono.book.publish :as book-publish]
   [clono.book.transform :as book-transform]
   [clono.pipeline :as pipeline]
   [goog.object :as gobj]))

(def usage
  (str "Usage:\n"
       "  clono transform <input> --output <output>\n"
       "  clono build [project]\n"
       "\n"
       "Options:\n"
       "  -o, --output <output>  変換後のMarkdownを書き込むファイル\n"
       "  -h, --help             使用方法を表示\n"))

(def ^:private private-file-mode 8r600)
(def ^:private permission-mask 8r777)

(defn- parse-transform-arguments [arguments]
  (loop [remaining (seq arguments)
         input nil
         output nil]
    (if-let [argument (first remaining)]
      (cond
        (#{"-o" "--output"} argument)
        (if output
          {:action :error
           :message "出力ファイルを複数指定できません。"}
          (if-let [value (second remaining)]
            (if (.startsWith value "-")
              {:action :error
               :message (str "未知のオプションです: " value)}
              (recur (nnext remaining) input value))
            {:action :error
             :message (str "`" argument "`には出力ファイルの指定が必要です。")}))

        (.startsWith argument "-")
        {:action :error
         :message (str "未知のオプションです: " argument)}

        input
        {:action :error
         :message "入力ファイルを複数指定できません。"}

        :else
        (recur (next remaining) argument output))
      (cond
        (nil? input)
        {:action :error
         :message "入力ファイルを指定してください。"}

        (nil? output)
        {:action :error
         :message "出力ファイルを指定してください。"}

        :else
        {:action :transform
         :input input
         :output output}))))

(defn- parse-build-arguments [arguments]
  (cond
    (empty? arguments)
    {:action :build
     :project "."}

    (.startsWith (first arguments) "-")
    {:action :error
     :message (str "未知のオプションです: " (first arguments))}

    (next arguments)
    {:action :error
     :message "書籍プロジェクトを複数指定できません。"}

    :else
    {:action :build
     :project (first arguments)}))

(defn parse-arguments [arguments]
  (cond
    (empty? arguments)
    {:action :help}

    (some #{"-h" "--help"} arguments)
    {:action :help}

    (= "transform" (first arguments))
    (parse-transform-arguments (next arguments))

    (= "build" (first arguments))
    (parse-build-arguments (next arguments))

    (.startsWith (first arguments) "-")
    {:action :error
     :message (str "未知のオプションです: " (first arguments))}

    :else
    {:action :error
     :message (str "未知のサブコマンドです: " (first arguments))}))

(defn- result [exit-code stdout stderr]
  {:exit-code exit-code
   :stdout stdout
   :stderr stderr})

(defn- help-result []
  (result 0 usage nil))

(defn- argument-error-result [message]
  (result 1 nil (str message "\n\n" usage)))

(defn- error-result [message]
  (result 1 nil (str message "\n")))

(defn- error-message [error]
  (or (.-message error) (str error)))

(defn- same-file? [input-path output-path input-stat output-stat]
  (let [input-device (gobj/get input-stat "dev")
        input-inode (gobj/get input-stat "ino")]
    (or (= input-path output-path)
        (and output-stat
             (not= 0 input-inode)
             (= input-device (gobj/get output-stat "dev"))
             (= input-inode (gobj/get output-stat "ino"))))))

(defn- validate-paths [input output]
  (let [input-path (.resolve path input)
        output-path (.resolve path output)
        output-parent (.dirname path output-path)]
    (try
      (cond
        (not (.existsSync fs input-path))
        {:ok? false
         :kind :file
         :message (str input ": 入力ファイルが存在しません。")}

        :else
        (let [input-stat (.statSync fs input-path)]
          (cond
            (not (.isFile input-stat))
            {:ok? false
             :kind :file
             :message (str input ": 入力パスにはファイルを指定してください。")}

            (not (.existsSync fs output-parent))
            {:ok? false
             :kind :file
             :message (str output ": 出力先の親ディレクトリが存在しません。")}

            :else
            (let [output-parent-stat (.statSync fs output-parent)
                  output-stat (when (.existsSync fs output-path)
                                (.statSync fs output-path))]
              (cond
                (not (.isDirectory output-parent-stat))
                {:ok? false
                 :kind :file
                 :message (str output ": 出力先の親パスにはディレクトリを指定してください。")}

                (and output-stat (not (.isFile output-stat)))
                {:ok? false
                 :kind :file
                 :message (str output ": 出力パスにはファイルを指定してください。")}

                (same-file? input-path output-path input-stat output-stat)
                {:ok? false
                 :kind :argument
                 :message "入力ファイルと出力ファイルに同じファイルを指定できません。"}

                :else
                {:ok? true
                 :input-path input-path
                 :output-path output-path
                 :output-mode (when output-stat
                                (bit-and permission-mask
                                         (gobj/get output-stat "mode")))})))))
      (catch :default error
        {:ok? false
         :kind :file
         :message (str "入出力ファイルを確認できません: "
                       (error-message error))}))))

(defn- read-input [input input-path]
  (try
    {:ok? true
     :source (.readFileSync fs input-path "utf8")}
    (catch :default error
      {:ok? false
       :message (str input ": ファイルを読み込めません: "
                     (error-message error))})))

(defn- format-diagnostic [{:keys [file line column message]}]
  (if (and (some? line) (some? column))
    (str file ":" line ":" column ": " message)
    (str file ": " message)))

(defn- diagnostics-result [diagnostics]
  (result 1 nil (str (string/join "\n" (map format-diagnostic diagnostics)) "\n")))

(defn- normalize-line-endings [content]
  (.replace content (js/RegExp. "\\r\\n?" "g") "\n"))

(defn- temporary-output-path [output-path]
  (.join path
         (.dirname path output-path)
         (str ".clono-"
              (.randomUUID crypto)
              ".tmp")))

(defn- remove-temporary-file! [temporary-path]
  (when (and temporary-path (.existsSync fs temporary-path))
    (try
      (.unlinkSync fs temporary-path)
      (catch :default _ nil))))

(defn ^:no-doc restore-output-mode! [output-path output-mode]
  (.chmodSync fs output-path output-mode))

(defn- write-output [output output-path output-mode content]
  (let [temporary-path (temporary-output-path output-path)]
    (try
      (.writeFileSync fs temporary-path content #js {:encoding "utf8"
                                                      :flag "wx"
                                                      :mode private-file-mode})
      (.chmodSync fs temporary-path private-file-mode)
      (.renameSync fs temporary-path output-path)
      (if output-mode
        (try
          (restore-output-mode! output-path output-mode)
          {:ok? true}
          (catch :default error
            {:ok? false
             :message (str output
                           ": 内容は更新しましたが、既存の許可ビットを復元できません: "
                           (error-message error))}))
        {:ok? true})
      (catch :default error
        {:ok? false
         :message (str output ": ファイルを書き込めません: "
                       (error-message error))})
      (finally
        (remove-temporary-file! temporary-path)))))

(defn- transform-result [input output]
  (let [paths (validate-paths input output)]
    (if-not (:ok? paths)
      (if (= :argument (:kind paths))
        (argument-error-result (:message paths))
        (error-result (:message paths)))
      (let [input-result (read-input input (:input-path paths))]
        (if-not (:ok? input-result)
          (error-result (:message input-result))
          (try
            (let [transformation (pipeline/run input (:source input-result))]
              (if-not (:ok? transformation)
                (diagnostics-result (:diagnostics transformation))
                (let [output-result (write-output output
                                                  (:output-path paths)
                                                  (:output-mode paths)
                                                  (normalize-line-endings
                                                   (:output transformation)))]
                  (if (:ok? output-result)
                    (result 0 nil nil)
                    (error-result (:message output-result))))))
            (catch :default error
              (error-result
               (str input ": 変換を実行できません: "
                    (error-message error))))))))))

(defn- build-with-config [config]
  (let [plan-result (book-plan/create config)]
    (if-not (:ok? plan-result)
      (diagnostics-result (:diagnostics plan-result))
      (let [transformation (book-transform/run (:plan plan-result))]
        (if-not (:ok? transformation)
          (diagnostics-result (:diagnostics transformation))
          (let [publication (book-publish/run (:plan transformation))]
            (if (:ok? publication)
              (result 0 nil nil)
              (diagnostics-result (:diagnostics publication)))))))))

(defn- build-result [project]
  (-> (book-config/load-project-config project)
      (.then (fn [config-result]
               (if (:ok? config-result)
                 (build-with-config (:config config-result))
                 (diagnostics-result (:diagnostics config-result)))))
      (.catch (fn [error]
                (error-result
                 (str project ": 書籍プロジェクトを変換できません: "
                      (error-message error)))))))

(defn command-result [arguments]
  (let [{:keys [action input output project message]} (parse-arguments arguments)]
    (case action
      :help (help-result)
      :error (argument-error-result message)
      :transform (transform-result input output)
      :build (build-result project))))

(defn- write-result! [{:keys [exit-code stdout stderr]}]
  (when stdout
    (.write (.-stdout js/process) stdout))
  (when stderr
    (.write (.-stderr js/process) stderr))
  (set! (.-exitCode js/process) exit-code))

(defn main [& arguments]
  (-> (js/Promise.resolve (command-result arguments))
      (.then write-result!)
      (.catch (fn [error]
                (write-result!
                 (error-result
                  (str "clonoを実行できません: "
                       (error-message error))))))))
