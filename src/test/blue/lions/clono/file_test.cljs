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
  (:require [cljs.test :refer [are deftest is testing use-fixtures]]
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

(use-fixtures :once
  {:before #(def tmp-dir (setup-tmp-dir))
   :after #(teardown-tmp-dir tmp-dir)})

(deftest extract-base-name-test
  (testing "Target file path has directories."
    (is (= "markdown01" (file/extract-base-name "/dir/markdown01.md")))
    (is (= "markdown02" (file/extract-base-name "../dir/markdown02.md")))
    (is (= "markdown03" (file/extract-base-name "/dir1/dir2/markdown03.md")))
    (is (= "markdown04" (file/extract-base-name "../dir1/dir2/markdown04.md"))))

  (testing "Target file path does not have directories."
    (is (= "markdown05" (file/extract-base-name "markdown05.md")))
    (is (= "markdown06" (file/extract-base-name "./markdown06.md")))
    (is (= "markdown07" (file/extract-base-name "../markdown07.md"))))

  (testing "Target file path does not have extension."
    (is (= "markdown08" (file/extract-base-name "/dir/markdown08")))
    (is (= "markdown09" (file/extract-base-name "markdown09")))
    (is (= ".markdown10" (file/extract-base-name "/dir/.markdown10")))
    (is (= ".markdown11" (file/extract-base-name ".markdown11"))))

  (testing "Target file path is invalid."
    (are [target] (thrown-with-msg? js/Error #"Assert failed:"
                                    (file/extract-base-name target))
      "invalid*file-path"
      ""
      nil)))

(deftest read-file-test
  (testing "Target file exists."
    (let [file-path (path/join tmp-dir "exists.txt")
          file-content "I am a text file."]
      (fs/writeFileSync file-path file-content)
      (is (= file-content (file/read-file file-path)))))

  (testing "Target file is empty."
    (let [file-path (path/join tmp-dir "empty.txt")]
      (fs/writeFileSync file-path "")
      (is (= "" (file/read-file file-path)))))

  (testing "Target file does not exist."
    (let [file-path (path/join tmp-dir "not-exists.txt")]
      (is (thrown-with-msg? js/Error
                            #"Failed to read file\."
                            (file/read-file file-path)))
      (try
        (file/read-file file-path)
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)]
            (is (= file-path (:file-path data)))
            (is (= "File does not exist." (ex-message cause)))
            (is (= file-path (:file-path (ex-data cause))))))))))
