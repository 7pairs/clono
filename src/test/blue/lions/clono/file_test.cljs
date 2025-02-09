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
            [blue.lions.clono.file :as file]))

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

(t/deftest extract-base-name-test
  (t/testing "File path has directories."
    (t/is (= "markdown01" (file/extract-base-name "/dir/markdown01.md")))
    (t/is (= "markdown02" (file/extract-base-name "./dir/markdown02.md")))
    (t/is (= "markdown03" (file/extract-base-name "../dir/markdown03.md")))
    (t/is (= "markdown04" (file/extract-base-name "/dir1/dir2/markdown04.md")))
    (t/is (= "markdown05"
             (file/extract-base-name "./dir1/dir2/markdown05.md")))
    (t/is (= "markdown06"
             (file/extract-base-name "../dir1/dir2/markdown06.md"))))

  (t/testing "File path does not have directories."
    (t/is (= "markdown07" (file/extract-base-name "markdown07.md")))
    (t/is (= "markdown08" (file/extract-base-name "./markdown08.md")))
    (t/is (= "markdown09" (file/extract-base-name "../markdown09.md"))))

  (t/testing "File path does not have extension."
    (t/is (= "markdown10" (file/extract-base-name "/dir/markdown10")))
    (t/is (= "markdown11" (file/extract-base-name "./dir/markdown11")))
    (t/is (= "markdown12" (file/extract-base-name "../dir/markdown12")))
    (t/is (= "markdown13" (file/extract-base-name "markdown13")))
    (t/is (= "markdown14" (file/extract-base-name "./markdown14")))
    (t/is (= "markdown15" (file/extract-base-name "../markdown15")))
    (t/is (= ".markdown16" (file/extract-base-name "/dir/.markdown16")))
    (t/is (= ".markdown17" (file/extract-base-name "./dir/.markdown17")))
    (t/is (= ".markdown18" (file/extract-base-name "../dir/.markdown18")))
    (t/is (= ".markdown19" (file/extract-base-name ".markdown19")))
    (t/is (= ".markdown20" (file/extract-base-name "./.markdown20")))
    (t/is (= ".markdown21" (file/extract-base-name "../.markdown21"))))

  (t/testing "File path is invalid."
    (t/are [file-path] (thrown-with-msg? js/Error #"Assert failed:"
                                         (file/extract-base-name file-path))
      "invalid*file-path"
      ""
      :not-string
      nil)))

(t/deftest read-file-test
  (t/testing "File exists."
    (let [file-path (path/join tmp-dir "exists.txt")
          file-content "I am a text file."]
      (fs/writeFileSync file-path file-content)
      (t/is (= file-content (file/read-file file-path)))))

  (t/testing "File is empty."
    (let [file-path (path/join tmp-dir "empty.txt")
          file-content ""]
      (fs/writeFileSync file-path file-content)
      (t/is (= file-content (file/read-file file-path)))))

  (t/testing "File does not exist."
    (let [file-path (path/join tmp-dir "not-exists.txt")]
      (try
        (file/read-file file-path)
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)]
            (t/is (str/starts-with? (ex-message e) "Failed to read file."))
            (t/is (= file-path (:file-path data)))
            (t/is (= "File does not exist." (ex-message cause)))
            (t/is (= file-path (:file-path (ex-data cause))))))))))
