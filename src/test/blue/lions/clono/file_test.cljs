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
