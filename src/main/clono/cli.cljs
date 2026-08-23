(ns clono.cli
  (:require
   ["node:crypto" :as crypto]
   ["node:fs" :as fs]
   ["node:path" :as path]
   [clojure.string :as string]
   [clono.pipeline :as pipeline]
   [goog.object :as gobj]))

(def usage
  (str "Usage: clono <input> --output <output>\n"
       "\n"
       "Options:\n"
       "  -o, --output <output>  変換後のMarkdownを書き込むファイル\n"
       "  -h, --help             使用方法を表示\n"))

(defn parse-arguments [arguments]
  (cond
    (empty? arguments)
    {:action :help}

    (some #{"-h" "--help"} arguments)
    {:action :help}

    :else
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
           :output output})))))

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
                 :output-path output-path})))))
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
  (str file ":" line ":" column ": " message))

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

(defn- write-output [output output-path content]
  (let [temporary-path (temporary-output-path output-path)]
    (try
      (.writeFileSync fs temporary-path content #js {:encoding "utf8"
                                                      :flag "wx"})
      (.renameSync fs temporary-path output-path)
      {:ok? true}
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
                                                  (normalize-line-endings
                                                   (:output transformation)))]
                  (if (:ok? output-result)
                    (result 0 nil nil)
                    (error-result (:message output-result))))))
            (catch :default error
              (error-result
               (str input ": 変換を実行できません: "
                    (error-message error))))))))))

(defn command-result [arguments]
  (let [{:keys [action input output message]} (parse-arguments arguments)]
    (case action
      :help (help-result)
      :error (argument-error-result message)
      :transform (transform-result input output))))

(defn- write-result! [{:keys [exit-code stdout stderr]}]
  (when stdout
    (.write (.-stdout js/process) stdout))
  (when stderr
    (.write (.-stderr js/process) stderr))
  (set! (.-exitCode js/process) exit-code))

(defn main [& arguments]
  (write-result! (command-result arguments)))
