(ns clono.cli-test
  (:require
   ["node:fs" :as fs]
   ["node:os" :as os]
   ["node:path" :as path]
   [cljs.test :refer [deftest is testing]]
   [clono.cli :as cli]))

(def successful-help-result
  {:exit-code 0
   :stdout cli/usage
   :stderr nil})

(def successful-transformation-result
  {:exit-code 0
   :stdout nil
   :stderr nil})

(def valid-source
  (str ":::align{position=\"right\"}\r\n"
       "2026年8月23日\r\n"
       "\r\n"
       "Thunder Claw\r\n"
       ":::\r\n"))

(def invalid-source
  (str ":::align{position=\"center\"}\n"
       "本文です。\n"
       ":::\n"))

(defn- with-temporary-directory [f]
  (let [directory (.mkdtempSync fs (.join path (.tmpdir os) "clono-cli-test-"))]
    (try
      (f directory)
      (finally
        (.rmSync fs directory #js {:recursive true
                                    :force true})))))

(defn- write-file! [file-path content]
  (.writeFileSync fs file-path content "utf8"))

(defn- temporary-output-files [directory]
  (filter #(.startsWith % ".clono-")
          (array-seq (.readdirSync fs directory))))

(deftest argument-parsing-test
  (testing "When no arguments are given, then usage is returned successfully"
    (is (= successful-help-result
           (cli/command-result []))))

  (testing "When -h or --help is given, then usage is returned successfully without evaluating other arguments"
    (doseq [arguments [["-h"]
                       ["--help"]
                       ["input.md" "--unknown" "--help"]]]
      (is (= successful-help-result
             (cli/command-result arguments))
          (str "Unexpected result for " arguments))))

  (testing "When a valid input and output are given in either order, then a transformation command is parsed"
    (is (= {:action :transform
            :input "input.md"
            :output "output.md"}
           (cli/parse-arguments ["input.md" "--output" "output.md"])))
    (is (= {:action :transform
            :input "input.md"
            :output "output.md"}
           (cli/parse-arguments ["-o" "output.md" "input.md"])))
    (is (= {:action :transform
            :input "input.md"
            :output "./-output.md"}
           (cli/parse-arguments ["input.md" "-o" "./-output.md"]))))

  (testing "When arguments violate the command contract, then a specific argument error is returned"
    (doseq [[arguments message]
            [[["input.md"]
              "出力ファイルを指定してください。"]
             [["-o" "output.md"]
              "入力ファイルを指定してください。"]
             [["input.md" "-o"]
              "`-o`には出力ファイルの指定が必要です。"]
             [["input.md" "-o" "one.md" "--output" "two.md"]
              "出力ファイルを複数指定できません。"]
             [["one.md" "two.md" "-o" "output.md"]
              "入力ファイルを複数指定できません。"]
             [["--unknown"]
              "未知のオプションです: --unknown"]
             [["input.md" "--output=output.md"]
              "未知のオプションです: --output=output.md"]
             [["input.md" "-o" "--unknown"]
              "未知のオプションです: --unknown"]
             [["input.md" "--output" "--unknown"]
              "未知のオプションです: --unknown"]]]
      (is (= {:action :error
              :message message}
             (cli/parse-arguments arguments))
          (str "Unexpected result for " arguments)))))

(deftest file-transformation-test
  (testing "When a valid file is transformed, then the output is replaced silently with LF line endings"
    (with-temporary-directory
      (fn [directory]
        (let [input (.join path directory "input.md")
              output (.join path directory "output.md")]
          (write-file! input valid-source)
          (write-file! output "previous output\n")
          (is (= successful-transformation-result
                 (cli/command-result [input "--output" output])))
          (let [content (.readFileSync fs output "utf8")]
            (is (.includes content "<div class=\"clono-align-right\">"))
            (is (.includes content "Thunder Claw"))
            (is (not (.includes content "\r"))))
          (is (empty? (temporary-output-files directory)))))))

  (testing "When transformation reports a diagnostic, then an existing output is preserved"
    (with-temporary-directory
      (fn [directory]
        (let [input (.join path directory "invalid.md")
              output (.join path directory "output.md")]
          (write-file! input invalid-source)
          (write-file! output "previous output\n")
          (let [command-result (cli/command-result [input "-o" output])]
            (is (= 1 (:exit-code command-result)))
            (is (nil? (:stdout command-result)))
            (is (.includes (:stderr command-result)
                           (str input ":1:1: `align`の`position`属性には`right`を指定する必要があります。")))
            (is (= "previous output\n"
                   (.readFileSync fs output "utf8"))))))))

  (testing "When transformation reports a diagnostic, then a new output is not created"
    (with-temporary-directory
      (fn [directory]
        (let [input (.join path directory "invalid.md")
              output (.join path directory "output.md")]
          (write-file! input invalid-source)
          (is (= 1 (:exit-code (cli/command-result [input "-o" output]))))
          (is (false? (.existsSync fs output))))))))

(deftest file-validation-test
  (testing "When the input file does not exist, then a file error is returned without creating output"
    (with-temporary-directory
      (fn [directory]
        (let [input (.join path directory "missing.md")
              output (.join path directory "output.md")
              command-result (cli/command-result [input "-o" output])]
          (is (= 1 (:exit-code command-result)))
          (is (.includes (:stderr command-result) "入力ファイルが存在しません。"))
          (is (false? (.existsSync fs output)))))))

  (testing "When the output parent does not exist, then a file error is returned without creating the directory"
    (with-temporary-directory
      (fn [directory]
        (let [input (.join path directory "input.md")
              output-directory (.join path directory "missing")
              output (.join path output-directory "output.md")]
          (write-file! input valid-source)
          (let [command-result (cli/command-result [input "-o" output])]
            (is (= 1 (:exit-code command-result)))
            (is (.includes (:stderr command-result) "出力先の親ディレクトリが存在しません。"))
            (is (false? (.existsSync fs output-directory))))))))

  (testing "When input and output identify the same file, then an argument error is returned without changing it"
    (with-temporary-directory
      (fn [directory]
        (let [input (.join path directory "input.md")]
          (write-file! input valid-source)
          (let [command-result (cli/command-result [input "-o" input])]
            (is (= 1 (:exit-code command-result)))
            (is (.includes (:stderr command-result)
                           "入力ファイルと出力ファイルに同じファイルを指定できません。"))
            (is (.includes (:stderr command-result) cli/usage))
            (is (= valid-source (.readFileSync fs input "utf8"))))))))

  (testing "When a directory is specified as input or output, then a file error is returned"
    (with-temporary-directory
      (fn [directory]
        (let [input (.join path directory "input.md")
              output (.join path directory "output")]
          (write-file! input valid-source)
          (.mkdirSync fs output)
          (is (.includes (:stderr (cli/command-result [directory "-o" input]))
                         "入力パスにはファイルを指定してください。"))
          (is (.includes (:stderr (cli/command-result [input "-o" output]))
                         "出力パスにはファイルを指定してください。")))))))
