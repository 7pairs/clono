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

(ns blue.lions.clono.identifier-test
  (:require [cljs.test :as t]
            [clojure.string :as str]
            [blue.lions.clono.identifier :as id]
            [blue.lions.clono.log :as logger]))

(t/deftest extract-base-name-test
  (t/testing "File path has directories."
    (t/is (= "markdown01" (id/extract-base-name "/dir/markdown01.md")))
    (t/is (= "markdown02" (id/extract-base-name "./dir/markdown02.md")))
    (t/is (= "markdown03" (id/extract-base-name "../dir/markdown03.md")))
    (t/is (= "markdown04" (id/extract-base-name "/dir1/dir2/markdown04.md")))
    (t/is (= "markdown05" (id/extract-base-name "./dir1/dir2/markdown05.md")))
    (t/is (= "markdown06"
             (id/extract-base-name "../dir1/dir2/markdown06.md"))))

  (t/testing "File path does not have directories."
    (t/is (= "markdown07" (id/extract-base-name "markdown07.md")))
    (t/is (= "markdown08" (id/extract-base-name "./markdown08.md")))
    (t/is (= "markdown09" (id/extract-base-name "../markdown09.md"))))

  (t/testing "File path does not have extension."
    (t/is (= "markdown10" (id/extract-base-name "/dir/markdown10")))
    (t/is (= "markdown11" (id/extract-base-name "./dir/markdown11")))
    (t/is (= "markdown12" (id/extract-base-name "../dir/markdown12")))
    (t/is (= "markdown13" (id/extract-base-name "markdown13")))
    (t/is (= "markdown14" (id/extract-base-name "./markdown14")))
    (t/is (= "markdown15" (id/extract-base-name "../markdown15")))
    (t/is (= ".markdown16" (id/extract-base-name "/dir/.markdown16")))
    (t/is (= ".markdown17" (id/extract-base-name "./dir/.markdown17")))
    (t/is (= ".markdown18" (id/extract-base-name "../dir/.markdown18")))
    (t/is (= ".markdown19" (id/extract-base-name ".markdown19")))
    (t/is (= ".markdown20" (id/extract-base-name "./.markdown20")))
    (t/is (= ".markdown21" (id/extract-base-name "../.markdown21"))))

  (t/testing "File path is invalid."
    (let [file-paths ["invalid*file-path"
                      ""
                      :not-string
                      nil]]
      (doseq [file-path file-paths]
        (try
          (id/extract-base-name file-path)
          (catch js/Error e
            (t/is (= "Invalid file path." (ex-message e)))
            (t/is (= {:file-path file-path} (ex-data e)))))))))

(t/deftest build-url-test
  (t/testing "Base name and ID are valid."
    (t/is (= "base-name.html#id" (id/build-url "base-name" "id")))
    (t/is (= "base-name.html#id-2" (id/build-url "base-name" "id-2"))))

  (t/testing "ID contains special characters."
    (t/is (= "base-name.html#%40id" (id/build-url "base-name" "@id"))))

  (t/testing "Base name is invalid."
    (let [base-names [""
                      nil]]
      (doseq [base-name base-names]
        (try
          (id/build-url base-name "id")
          (catch js/Error e
            (t/is (= "Invalid base name." (ex-message e)))
            (t/is (= {:base-name base-name} (ex-data e))))))))

  (t/testing "ID is invalid."
    (let [ids [""
               nil]]
      (doseq [id ids]
        (try
          (id/build-url "base-name" id)
          (catch js/Error e
            (t/is (= "Invalid ID." (ex-message e)))
            (t/is (= {:id id} (ex-data e))))))))

  (t/testing "Extension is given."
    (t/is (= "base-name.htm#id"
             (id/build-url "base-name" "id" {:extension ".htm"}))))

  (t/testing "Separator is given."
    (t/is (= "base-name.html|id"
             (id/build-url "base-name" "id" {:separator "|"}))))

  (t/testing "Extension and separator are given."
    (t/is (= "base-name.htm|id"
             (id/build-url "base-name" "id" {:extension ".htm"
                                             :separator "|"})))))

(t/deftest build-dic-key-test
  (t/testing "Base name and ID are valid."
    (t/is (= "base-name|id" (id/build-dic-key "base-name" "id"))))

  (t/testing "Generated key is very long."
    (let [base-name (str/join (repeat 50 "b"))
          id (str/join (repeat 50 "i"))]
      (reset! logger/enabled? false)
      (reset! logger/entries [])
      (t/is (= (str base-name "|" id)
               (id/build-dic-key base-name id)))
      (t/is (= [{:level :debug
                 :message "Generated very long dictionary key."
                 :data {:base-name base-name :id id}}]
             @logger/entries))))

  (t/testing "Base name is invalid."
    (let [base-names [""
                      nil]]
      (doseq [base-name base-names]
        (try
          (id/build-dic-key base-name "id")
          (catch js/Error e
            (t/is (= "Invalid base name." (ex-message e)))
            (t/is (= {:base-name base-name} (ex-data e))))))))

  (t/testing "ID is invalid."
    (let [ids [""
               nil]]
      (doseq [id ids]
        (try
          (id/build-dic-key "base-name" id)
          (catch js/Error e
            (t/is (= "Invalid ID." (ex-message e)))
            (t/is (= {:id id} (ex-data e)))))))))

(t/deftest parse-dic-key-test
  (t/testing "Key has chapter."
    (t/is (= {:chapter "chapter" :id "id"} (id/parse-dic-key "chapter|id"))))

  (t/testing "Key does not have chapter."
    (t/is (= {:chapter nil :id "id"} (id/parse-dic-key "id"))))

  (t/testing "Key is invalid."
    (let [keys [""
                nil]]
      (doseq [key keys]
        (try
          (id/parse-dic-key key)
          (catch js/Error e
            (t/is (= "Invalid dictionary key." (ex-message e)))
            (t/is (= {:key key} (ex-data e)))))))))
