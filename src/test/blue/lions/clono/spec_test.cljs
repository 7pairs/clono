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

(ns blue.lions.clono.spec-test
  (:require [cljs.spec.alpha :as s]
            [cljs.test :refer [are deftest is testing]]
            [blue.lions.clono.spec :as spec]))

(deftest common_non-blank-string-test
  (testing "Succeeds to verify."
    (is (s/valid? :blue.lions.clono.spec.common/non-blank-string
                  "non-blank-string")))

  (testing "Fails to verify."
    (are [target] (not (s/valid? :blue.lions.clono.spec.common/non-blank-string
                                 target))
      ""
      :not-string
      nil)))

(deftest common_non-nil-string-test
  (testing "Succeeds to verify."
    (are [target] (s/valid? :blue.lions.clono.spec.common/non-nil-string target)
      "non-nil-string"
      ""))

  (testing "Fails to verify."
    (are [target] (not (s/valid? :blue.lions.clono.spec.common/non-nil-string
                                 target))
      :not-string
      nil)))

(deftest edn-test
  (testing "Succeeds to verify."
    (are [target] (s/valid? ::spec/edn target)
      {:key "value"}
      {:key1 1 :key2 ["vector"]}
      {}))

  (testing "Fails to verify."
    (are [target] (not (s/valid? ::spec/edn target))
      {"not-keyword" "value"}
      {:key "value1" "not-keyword" "value2"}
      "not-map"
      nil)))

(deftest file-content-test
  (testing "Succeeds to verify."
    (are [target] (s/valid? ::spec/file-content target)
      "file-content"
      ""))

  (testing "Fails to verify."
    (are [target] (not (s/valid? ::spec/file-content target))
      :not-string
      nil)))

(deftest file-path-test
  (testing "Succeeds to verify."
    (are [target] (s/valid? ::spec/file-path target)
      "/valid/file-path"
      "C:\\valid\\file-path"
      "日本語"))

  (testing "Fails to verify."
    (are [target] (not (s/valid? ::spec/file-path target))
      "invalid*file-path"
      "invalid?file-path"
      "invalid\"file-path"
      "invalid>file-path"
      "invalid<file-path"
      "invalid|file-path"
      ""
      :not-string
      nil)))

(deftest id-test
  (testing "Succeeds to verify."
    (are [target] (s/valid? ::spec/id target)
      "valid|id"
      "日本語"))

  (testing "Fails to verify."
    (are [target] (not (s/valid? ::spec/id target))
      "invalid\\id"
      "invalid/id"
      "invalid:id"
      "invalid*id"
      "invalid?id"
      "invalid\"id"
      "invalid>id"
      "invalid<id"
      ""
      :not-string
      nil)))
