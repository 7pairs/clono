; Copyright 2025 HASEBA Junya
; 
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
; 
;     http://www.apache.org/licenses/LICENSE-2.0
; 
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns blue.lions.clono.file-test
  (:require [cljs.test :as t]
            [clojure.string :as str]
            ["fs" :as fs]
            ["os" :as os]
            ["path" :as path]
            [blue.lions.clono.file :as file]
            [blue.lions.clono.log :as logger]))

(defn- setup-tmp-dir
  []
  (let [tmp-dir (path/join (os/tmpdir) "file-test")]
    (when (not (fs/existsSync tmp-dir))
      (fs/mkdirSync tmp-dir))
    tmp-dir))

(defn- teardown-tmp-dir
  [tmp-dir]
  (fs/rmdirSync tmp-dir #js {:recursive true}))

(t/use-fixtures :once
  {:before #(def tmp-dir (setup-tmp-dir))
   :after #(teardown-tmp-dir tmp-dir)})

(t/deftest read-file-test
  (t/testing "File exists."
    (let [file-path (path/join tmp-dir "exists.txt")
          file-content "I am a text file."]
      (fs/writeFileSync file-path file-content "utf8")
      (t/is (= file-content (file/read-file file-path)))))

  (t/testing "File is empty."
    (let [file-path (path/join tmp-dir "empty.txt")
          file-content ""]
      (fs/writeFileSync file-path file-content "utf8")
      (t/is (= file-content (file/read-file file-path)))))

  (t/testing "File does not exist."
    (let [file-path (path/join tmp-dir "not-exists.txt")]
      (try
        (file/read-file file-path)
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)]
            (t/is (= "Failed to read file." (ex-message e)))
            (t/is (= file-path (:file-path data)))
            (t/is (= "File does not exist." (ex-message cause)))
            (t/is (= file-path (:file-path (ex-data cause)))))))))

  (t/testing "File size is very large."
    (let [file-path (path/join tmp-dir "large.txt")
          file-content (apply str (repeat 100 "X"))]
      (logger/set-enabled! false)
      (logger/reset-entries!)
      (fs/writeFileSync file-path file-content "utf8")
      (t/is (= file-content
               (file/read-file file-path :threshold 99)))
      (t/is (= [{:level :warn
                 :message "Reading very large file."
                 :data {:file-path file-path
                        :size (/ 100 1024 1024)}}]
               (logger/get-entries))))))

(t/deftest read-edn-file-test
  (t/testing "File is valid as EDN file."
    (let [file-path (path/join tmp-dir "valid.edn")]
      (fs/writeFileSync file-path "{:key \"value\"}" "utf8")
      (t/is (= {:key "value"} (file/read-edn-file file-path)))))

  (t/testing "File is empty."
    (let [file-path (path/join tmp-dir "empty.edn")]
      (fs/writeFileSync file-path "" "utf8")
      (try
        (file/read-edn-file file-path)
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)
                cause-data (ex-data cause)]
            (t/is (= "Failed to read or parse EDN file." (ex-message e)))
            (t/is (= file-path (:file-path data)))
            (t/is (= "EDN file is empty or invalid." (ex-message cause)))
            (t/is (= file-path (:file-path cause-data)))
            (t/is (= "" (:content cause-data))))))))

  (t/testing "File does not exist."
    (let [file-path (path/join tmp-dir "not-exists.edn")]
      (try
        (file/read-edn-file file-path)
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)]
            (t/is (= "Failed to read or parse EDN file." (ex-message e)))
            (t/is (= file-path (:file-path data)))
            (t/is (= "Failed to read file." (ex-message cause)))
            (t/is (= file-path (:file-path (ex-data cause)))))))))

  (t/testing "File is invalid as EDN file."
    (let [file-path (path/join tmp-dir "invalid.edn")]
      (fs/writeFileSync file-path "{\"key\": \"value\"}" "utf8")
      (try
        (file/read-edn-file file-path)
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)]
            (t/is (= "Failed to read or parse EDN file." (ex-message e)))
            (t/is (= file-path (:file-path data)))
            (t/is (str/starts-with?
                   (ex-message (:cause data))
                   "A single colon is not a valid keyword.")))))))

  (t/testing "File size is very large."
    (let [file-path (path/join tmp-dir "large.edn")
          file-content (str "{:key \"" (apply str (repeat 100 "X")) "\"}")]
      (logger/set-enabled! false)
      (logger/reset-entries!)
      (fs/writeFileSync file-path file-content "utf8")
      (t/is (= {:key (apply str (repeat 100 "X"))}
               (file/read-edn-file file-path :threshold 100)))
      (t/is (= [{:level :warn
                 :message "Reading very large file."
                 :data {:file-path file-path
                        :size (/ (.-length file-content) 1024 1024)}}]
               (logger/get-entries)))))

  (t/testing "File does not have required keys."
    (let [file-path (path/join tmp-dir "invalid-key.edn")]
      (fs/writeFileSync file-path "{:required1 \"value\"}" "utf8")
      (try
        (file/read-edn-file file-path :required-keys [:required1 :required2])
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)
                cause-data (ex-data cause)]
            (t/is (= "Failed to read or parse EDN file." (ex-message e)))
            (t/is (= file-path (:file-path data)))
            (t/is (= "Required key is missing in EDN file."
                     (ex-message cause)))
            (t/is (= file-path (:file-path cause-data)))
            (t/is (= [:required1 :required2]
                     (:required-keys cause-data)))
            (t/is (= :required2 (:missing-key cause-data)))))))))

(t/deftest read-config-file-test
  (t/testing "File is valid as config file."
    (let [file-path (path/join tmp-dir "valid.edn")]
      (fs/writeFileSync file-path
                        (str "{:catalog \""
                             file-path
                             "\" :input \""
                             tmp-dir
                             "\" :output \""
                             tmp-dir
                             "\"}")
                        "utf8")
      (t/is (= {:catalog file-path :input tmp-dir :output tmp-dir}
               (file/read-edn-file file-path)))))

  (t/testing "File is empty."
    (let [file-path (path/join tmp-dir "empty.edn")]
      (fs/writeFileSync file-path "" "utf8")
      (try
        (file/read-config-file file-path)
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)]
            (t/is (= "Failed to read config file." (ex-message e)))
            (t/is (= file-path (:file-path data)))
            (t/is (= "Failed to read or parse EDN file." (ex-message cause)))
            (t/is (= file-path (:file-path (ex-data cause)))))))))

  (t/testing "File does not exist."
    (let [file-path (path/join tmp-dir "not-exists.edn")]
      (try
        (file/read-config-file file-path)
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)]
            (t/is (= "Failed to read config file." (ex-message e)))
            (t/is (= file-path (:file-path data)))
            (t/is (= "Failed to read or parse EDN file." (ex-message cause)))
            (t/is (= file-path (:file-path (ex-data cause)))))))))

  (t/testing "File is invalid as config file."
    (let [file-path (path/join tmp-dir "invalid.edn")]
      (fs/writeFileSync file-path "{\"key\": \"value\"}" "utf8")
      (try
        (file/read-config-file file-path)
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)]
            (t/is (= "Failed to read config file." (ex-message e)))
            (t/is (= file-path (:file-path data)))
            (t/is (= "Failed to read or parse EDN file." (ex-message cause)))
            (t/is (= file-path (:file-path (ex-data cause)))))))))

  (t/testing "File does not have required keys."
    (let [file-path (path/join tmp-dir "invalid-key.edn")]
      (fs/writeFileSync file-path
                        "{:catalog \"catalog\" :output \"output\"}"
                        "utf8")
      (try
        (file/read-config-file file-path)
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)
                cause-data (ex-data cause)
                cause-cause (:cause cause-data)
                cause-cause-data (ex-data cause-cause)]
            (t/is (= "Failed to read config file." (ex-message e)))
            (t/is (= file-path (:file-path data)))
            (t/is (= "Failed to read or parse EDN file." (ex-message cause)))
            (t/is (= file-path (:file-path cause-data)))
            (t/is (= "Required key is missing in EDN file."
                     (ex-message cause-cause)))
            (t/is (= file-path (:file-path cause-cause-data)))
            (t/is (= [:catalog :input :output]
                     (:required-keys cause-cause-data)))
            (t/is (= :input (:missing-key cause-cause-data))))))))

  (t/testing "File has invalid paths."
    (let [file-path (path/join tmp-dir "invalid-path.edn")]
      (fs/writeFileSync file-path
                        (str "{:catalog \""
                             file-path
                             "\" :input \""
                             tmp-dir
                             "\" :output \""
                             "not-exists-path"
                             "\"}")
                        "utf8")
      (logger/set-enabled! false)
      (logger/reset-entries!)
      (file/read-config-file file-path)
      (t/is (= [{:level :warn
                 :message "Path does not exist."
                 :data {:key :output
                        :path "not-exists-path"}}]
               (logger/get-entries))))))

(t/deftest read-catalog-file-test
  (t/testing "File is valid as catalog file."
    (let [file-path (path/join tmp-dir "catalog.edn")]
      (fs/writeFileSync file-path
                        "{:chapters [\"chapter1.md\" \"chapter2.md\"]}"
                        "utf8")
      (logger/set-enabled! false)
      (logger/reset-entries!)
      (t/is (= {:chapters ["chapter1.md" "chapter2.md"]}
               (file/read-catalog-file file-path)))
      (t/is (= [{:level :debug
                 :message "Catalog has 2 files."
                 :data {:type "chapters"
                        :files ["chapter1.md" "chapter2.md"]}}]
               (logger/get-entries)))))

  (t/testing "File is empty."
    (let [file-path (path/join tmp-dir "empty.edn")]
      (fs/writeFileSync file-path "" "utf8")
      (try
        (file/read-catalog-file file-path)
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)]
            (t/is (= "Failed to read catalog file." (ex-message e)))
            (t/is (= file-path (:file-path data)))
            (t/is (= "Failed to read or parse EDN file." (ex-message cause)))
            (t/is (= file-path (:file-path (ex-data cause)))))))))

  (t/testing "File does not exist."
    (let [file-path (path/join tmp-dir "not-exists.edn")]
      (try
        (file/read-catalog-file file-path)
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)]
            (t/is (= "Failed to read catalog file." (ex-message e)))
            (t/is (= file-path (:file-path data)))
            (t/is (= "Failed to read or parse EDN file." (ex-message cause)))
            (t/is (= file-path (:file-path (ex-data cause)))))))))

  (t/testing "File is invalid as catalog file."
    (let [file-path (path/join tmp-dir "invalid.edn")]
      (fs/writeFileSync file-path "{\"chapters\": [\"chapter.md\"]}" "utf8")
      (try
        (file/read-catalog-file file-path)
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)]
            (t/is (= "Failed to read catalog file." (ex-message e)))
            (t/is (= file-path (:file-path data)))
            (t/is (= "Failed to read or parse EDN file." (ex-message cause)))
            (t/is (= file-path (:file-path (ex-data cause)))))))))

  (t/testing "File does not have required keys."
    (let [file-path (path/join tmp-dir "invalid-key.edn")]
      (fs/writeFileSync file-path "{:forewords \"foreword.md\"}" "utf8")
      (try
        (file/read-catalog-file file-path)
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)
                cause-data (ex-data cause)
                cause-cause (:cause cause-data)
                cause-cause-data (ex-data cause-cause)]
            (t/is (= "Failed to read catalog file." (ex-message e)))
            (t/is (= file-path (:file-path data)))
            (t/is (= "Failed to read or parse EDN file." (ex-message cause)))
            (t/is (= file-path (:file-path cause-data)))
            (t/is (= "Required key is missing in EDN file."
                     (ex-message cause-cause)))
            (t/is (= file-path (:file-path cause-cause-data)))
            (t/is (= [:chapters]
                     (:required-keys cause-cause-data)))
            (t/is (= :chapters (:missing-key cause-cause-data)))))))))

(t/deftest read-markdown-file-test
  (t/testing "File is valid as Markdown file."
    (let [file-path (path/join tmp-dir "markdown.md")
          file-content "# Markdown"]
      (fs/writeFileSync file-path file-content "utf8")
      (t/is (= file-content (file/read-markdown-file file-path)))))

  (t/testing "File is empty."
    (let [file-path (path/join tmp-dir "empty.md")
          file-content ""]
      (fs/writeFileSync file-path file-content "utf8")
      (t/is (= file-content (file/read-markdown-file file-path)))))

  (t/testing "File does not exist."
    (let [file-path (path/join tmp-dir "not-exists.md")]
      (try
        (file/read-markdown-file file-path)
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)]
            (t/is (= "Failed to read Markdown file." (ex-message e)))
            (t/is (= file-path (:file-path data)))
            (t/is (= "Failed to read file." (ex-message cause)))
            (t/is (= file-path (:file-path (ex-data cause)))))))))

  (t/testing "File size is very large."
    (let [file-path (path/join tmp-dir "large.md")
          file-content (apply str (repeat 100 "X"))]
      (logger/set-enabled! false)
      (logger/reset-entries!)
      (fs/writeFileSync file-path file-content "utf8")
      (t/is (= file-content
               (file/read-markdown-file file-path :threshold 99)))
      (t/is (= [{:level :warn
                 :message "Reading very large file."
                 :data {:file-path file-path
                        :size (/ 100 1024 1024)}}]
               (logger/get-entries))))))

(t/deftest read-markdown-files-test
  (let [foreword-name "foreword.md"
        foreword-content "# Foreword"
        chapter-name "chapter.md"
        chapter-content "# Chapter"
        appendix-name "appendix.md"
        appendix-content "# Appendix"
        afterword-name "afterword.md"
        afterword-content "# Afterword"
        empty-name "empty.md"
        empty-content ""]
    (fs/writeFileSync (path/join tmp-dir foreword-name)
                      foreword-content
                      "utf8")
    (fs/writeFileSync (path/join tmp-dir chapter-name)
                      chapter-content
                      "utf8")
    (fs/writeFileSync (path/join tmp-dir appendix-name)
                      appendix-content
                      "utf8")
    (fs/writeFileSync (path/join tmp-dir afterword-name)
                      afterword-content
                      "utf8")
    (fs/writeFileSync (path/join tmp-dir empty-name)
                      empty-content
                      "utf8")

    (t/testing "All files exist."
      (t/is (= [{:name foreword-name
                 :type :forewords
                 :markdown foreword-content}
                {:name chapter-name
                 :type :chapters
                 :markdown chapter-content}
                {:name appendix-name
                 :type :appendices
                 :markdown appendix-content}
                {:name afterword-name
                 :type :afterwords
                 :markdown afterword-content}]
               (file/read-markdown-files tmp-dir
                                         {:forewords [foreword-name]
                                          :chapters [chapter-name]
                                          :appendices [appendix-name]
                                          :afterwords [afterword-name]}))))

    (t/testing "Some files are empty."
      (t/is (= [{:name foreword-name
                 :type :forewords
                 :markdown foreword-content}
                {:name empty-name
                 :type :chapters
                 :markdown empty-content}
                {:name appendix-name
                 :type :appendices
                 :markdown appendix-content}
                {:name empty-name
                 :type :afterwords
                 :markdown empty-content}]
               (file/read-markdown-files tmp-dir
                                         {:forewords [foreword-name]
                                          :chapters [empty-name]
                                          :appendices [appendix-name]
                                          :afterwords [empty-name]}))))

    (t/testing "Some files do not exist."
      (logger/set-enabled! false)
      (logger/reset-entries!)
      (t/is (= [{:name chapter-name
                 :type :chapters
                 :markdown chapter-content}
                {:name afterword-name
                 :type :afterwords
                 :markdown afterword-content}]
               (file/read-markdown-files tmp-dir
                                         {:forewords ["not-exists1.md"]
                                          :chapters [chapter-name]
                                          :appendices ["not-exists2.md"]
                                          :afterwords [afterword-name]})))
      (t/is (= [{:level :error
                 :message "Failed to read Markdown file: not-exists1.md"
                 :data {:file-path (path/join tmp-dir "not-exists1.md")
                        :type :forewords
                        :cause "Failed to read Markdown file."}}
                {:level :error
                 :message "Failed to read Markdown file: not-exists2.md"
                 :data {:file-path (path/join tmp-dir "not-exists2.md")
                        :type :appendices
                        :cause "Failed to read Markdown file."}}]
               (logger/get-entries))))

    (t/testing "All files do not exist."
      (logger/set-enabled! false)
      (logger/reset-entries!)
      (t/is (= []
               (file/read-markdown-files tmp-dir
                                         {:forewords ["not-exists1.md"]
                                          :chapters ["not-exists2.md"]
                                          :appendices ["not-exists3.md"]
                                          :afterwords ["not-exists4.md"]})))
      (t/is (= [{:level :error
                 :message "Failed to read Markdown file: not-exists1.md"
                 :data {:file-path (path/join tmp-dir "not-exists1.md")
                        :type :forewords
                        :cause "Failed to read Markdown file."}}
                {:level :error
                 :message "Failed to read Markdown file: not-exists2.md"
                 :data {:file-path (path/join tmp-dir "not-exists2.md")
                        :type :chapters
                        :cause "Failed to read Markdown file."}}
                {:level :error
                 :message "Failed to read Markdown file: not-exists3.md"
                 :data {:file-path (path/join tmp-dir "not-exists3.md")
                        :type :appendices
                        :cause "Failed to read Markdown file."}}
                {:level :error
                 :message "Failed to read Markdown file: not-exists4.md"
                 :data {:file-path (path/join tmp-dir "not-exists4.md")
                        :type :afterwords
                        :cause "Failed to read Markdown file."}}]
               (logger/get-entries))))))

(t/deftest write-file-test
  (t/testing "Content is not empty."
    (let [file-path (path/join tmp-dir "text.txt")
          file-content "I am a text file."]
      (file/write-file file-path file-content)
      (t/is (= file-content (fs/readFileSync file-path "utf8")))))

  (t/testing "Content is empty."
    (let [file-path (path/join tmp-dir "empty.txt")
          file-content ""]
      (file/write-file file-path file-content)
      (t/is (= file-content (fs/readFileSync file-path "utf8")))))

  (t/testing "File already exists."
    (let [file-path (path/join tmp-dir "exists.txt")
          file-content "I am a text file."]
      (fs/writeFileSync file-path file-content "utf8")
      (logger/set-enabled! false)
      (logger/reset-entries!)
      (file/write-file file-path file-content)
      (t/is (= [{:level :warn
                 :message "File already exists and will be overwritten."
                 :data {:file-path file-path}}]
               (logger/get-entries))))))

(t/deftest write-markdown-files-test
  (let [foreword-name "foreword.md"
        foreword-content "# Foreword"
        chapter-name "chapter.md"
        chapter-content "# Chapter"
        appendix-name "appendix.md"
        appendix-content "# Appendix"
        afterword-name "afterword.md"
        afterword-content "# Afterword"
        empty-content ""]
    (t/testing "All files are not empty."
      (logger/set-enabled! false)
      (logger/reset-entries!)
      (file/write-markdown-files tmp-dir
                                 [{:name foreword-name
                                   :type :forewords
                                   :markdown foreword-content}
                                  {:name chapter-name
                                   :type :chapters
                                   :markdown chapter-content}
                                  {:name appendix-name
                                   :type :appendices
                                   :markdown appendix-content}
                                  {:name afterword-name
                                   :type :afterwords
                                   :markdown afterword-content}]
                                 :force? true)
      (t/is (= foreword-content
               (fs/readFileSync (path/join tmp-dir foreword-name) "utf8")))
      (t/is (= chapter-content
               (fs/readFileSync (path/join tmp-dir chapter-name) "utf8")))
      (t/is (= appendix-content
               (fs/readFileSync (path/join tmp-dir appendix-name) "utf8")))
      (t/is (= afterword-content
               (fs/readFileSync (path/join tmp-dir afterword-name) "utf8")))
      (t/is (= [{:level :info
                 :message (str "Wrote Markdown file (1/4): " foreword-name)
                 :data nil}
                {:level :info
                 :message (str "Wrote Markdown file (2/4): " chapter-name)
                 :data nil}
                {:level :info
                 :message (str "Wrote Markdown file (3/4): " appendix-name)
                 :data nil}
                {:level :info
                 :message (str "Wrote Markdown file (4/4): " afterword-name)
                 :data nil}]
               (logger/get-entries))))

    (t/testing "Some files are empty."
      (logger/set-enabled! false)
      (logger/reset-entries!)
      (file/write-markdown-files tmp-dir
                                 [{:name foreword-name
                                   :type :forewords
                                   :markdown foreword-content}
                                  {:name chapter-name
                                   :type :chapters
                                   :markdown empty-content}
                                  {:name appendix-name
                                   :type :appendices
                                   :markdown appendix-content}
                                  {:name afterword-name
                                   :type :afterwords
                                   :markdown empty-content}]
                                 :force? true)
      (t/is (= foreword-content
               (fs/readFileSync (path/join tmp-dir foreword-name) "utf8")))
      (t/is (= empty-content
               (fs/readFileSync (path/join tmp-dir chapter-name) "utf8")))
      (t/is (= appendix-content
               (fs/readFileSync (path/join tmp-dir appendix-name) "utf8")))
      (t/is (= empty-content
               (fs/readFileSync (path/join tmp-dir afterword-name) "utf8")))
      (t/is (= [{:level :info
                 :message (str "Wrote Markdown file (1/4): " foreword-name)
                 :data nil}
                {:level :info
                 :message (str "Wrote Markdown file (2/4): " chapter-name)
                 :data nil}
                {:level :info
                 :message (str "Wrote Markdown file (3/4): " appendix-name)
                 :data nil}
                {:level :info
                 :message (str "Wrote Markdown file (4/4): " afterword-name)
                 :data nil}]
               (logger/get-entries))))

    (t/testing "All files are empty."
      (logger/set-enabled! false)
      (logger/reset-entries!)
      (file/write-markdown-files tmp-dir
                                 [{:name foreword-name
                                   :type :forewords
                                   :markdown empty-content}
                                  {:name chapter-name
                                   :type :chapters
                                   :markdown empty-content}
                                  {:name appendix-name
                                   :type :appendices
                                   :markdown empty-content}
                                  {:name afterword-name
                                   :type :afterwords
                                   :markdown empty-content}]
                                 :force? true)
      (t/is (= empty-content
               (fs/readFileSync (path/join tmp-dir foreword-name) "utf8")))
      (t/is (= empty-content
               (fs/readFileSync (path/join tmp-dir chapter-name) "utf8")))
      (t/is (= empty-content
               (fs/readFileSync (path/join tmp-dir appendix-name) "utf8")))
      (t/is (= empty-content
               (fs/readFileSync (path/join tmp-dir afterword-name) "utf8")))
      (t/is (= [{:level :info
                 :message (str "Wrote Markdown file (1/4): " foreword-name)
                 :data nil}
                {:level :info
                 :message (str "Wrote Markdown file (2/4): " chapter-name)
                 :data nil}
                {:level :info
                 :message (str "Wrote Markdown file (3/4): " appendix-name)
                 :data nil}
                {:level :info
                 :message (str "Wrote Markdown file (4/4): " afterword-name)
                 :data nil}]
               (logger/get-entries))))

    (t/testing "Files already exist."
      (let [exists-name "exists.md"
            exists-content "# Exists"
            not-exists-name "not-exists.md"
            not-exists-content "# Not exists"]
        (fs/writeFileSync (path/join tmp-dir exists-name) "I exist." "utf8")
        (logger/set-enabled! false)
        (logger/reset-entries!)
        (file/write-markdown-files tmp-dir
                                   [{:name exists-name
                                     :type :chapters
                                     :markdown exists-content}
                                    {:name not-exists-name
                                     :type :chapters
                                     :markdown not-exists-content}]
                                   :force? false)
        (t/is (= exists-content
                 (fs/readFileSync (path/join tmp-dir exists-name) "utf8")))
        (t/is (= not-exists-content
                 (fs/readFileSync (path/join tmp-dir not-exists-name) "utf8")))
        (t/is (= [{:level :warn
                   :message "File already exists and will be overwritten."
                   :data {:file-path (path/join tmp-dir exists-name)}}
                  {:level :info
                   :message (str "Wrote Markdown file (1/2): " exists-name)
                   :data nil}
                  {:level :info
                   :message (str "Wrote Markdown file (2/2): " not-exists-name)
                   :data nil}]
                 (logger/get-entries)))))))
