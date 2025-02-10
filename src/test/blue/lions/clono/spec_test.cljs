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
            [cljs.test :as t]
            [blue.lions.clono.spec :as spec]
            [blue.lions.clono.spec.common :as common]))

(t/deftest common_non-blank-string-test
  (t/testing "Succeeds to verify."
    (t/is (s/valid? ::common/non-blank-string "non-blank-string")))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::common/non-blank-string value))
      ""
      :not-string
      nil)))

(t/deftest common_non-nil-string-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::common/non-nil-string value)
      "non-nil-string"
      ""))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::common/non-nil-string value))
      :not-string
      nil)))

(t/deftest config-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/config value)
      {:key "value"}
      {:key1 1 :key2 ["vector"]}
      {}))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/config value))
      {"not-keyword" "value"}
      {:key "value1" "not-keyword" "value2"}
      "not-map"
      nil)))

(t/deftest edn-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/edn value)
      {:key "value"}
      {:key1 1 :key2 ["vector"]}
      {}))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/edn value))
      {"not-keyword" "value"}
      {:key "value1" "not-keyword" "value2"}
      "not-map"
      nil)))

(t/deftest file-content-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/file-content value)
      "file-content"
      ""))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/file-content value))
      :not-string
      nil)))

(t/deftest file-name-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/file-name value)
      "file-name.md"
      "日本語ファイル名"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/file-name value))
      "invalid\\file-name"
      "invalid/file-name"
      "invalid:file-name"
      "invalid*file-name"
      "invalid?file-name"
      "invalid\"file-name"
      "invalid>file-name"
      "invalid<file-name"
      "invalid|file-name"
      ""
      :not-string
      nil)))

(t/deftest file-path-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/file-path value)
      "/valid/file-path"
      "./valid/file-path"
      "../valid/file-path"
      "/valid-file-path"
      "./valid-file-path"
      "../valid-file-path"
      "C:\\valid\\file-path"
      ".\\valid\\file-path"
      "..\\valid\\file-path"
      "C:\\valid-file-path"
      ".\\valid-file-path"
      "..\\valid-file-path"
      "valid-file-path"
      "/日本語/ファイルパス"
      "./日本語/ファイルパス"
      "../日本語/ファイルパス"
      "/日本語ファイルパス"
      "./日本語ファイルパス"
      "../日本語ファイルパス"
      "C:\\日本語\\ファイルパス"
      ".\\日本語\\ファイルパス"
      "..\\日本語\\ファイルパス"
      "C:\\日本語ファイルパス"
      ".\\日本語ファイルパス"
      "..\\日本語ファイルパス"
      "日本語ファイルパス"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/file-path value))
      "invalid*file-path"
      "invalid?file-path"
      "invalid\"file-path"
      "invalid>file-path"
      "invalid<file-path"
      "invalid|file-path"
      ""
      :not-string
      nil)))
