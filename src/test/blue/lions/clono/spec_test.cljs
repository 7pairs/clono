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
            [blue.lions.clono.spec.catalog :as catalog]
            [blue.lions.clono.spec.common :as common]
            [blue.lions.clono.spec.document :as document]
            [blue.lions.clono.spec.manuscript :as manuscript]
            [blue.lions.clono.spec.node :as node]))

(t/deftest common_alphabet-string-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::common/alphabet-string value)
      "alphabet"
      "ALPHABET"
      "Alphabet"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::common/alphabet-string value))
      "12345"
      "alphabet12345"
      ""
      :not-string
      nil)))

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

(t/deftest catalog_afterwords-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::catalog/afterwords value)
      ["afterword.md"]
      ["afterword1.md" "afterword2.md"]
      []))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::catalog/afterwords value))
      ["invalid/file-name"]
      [""]
      [:not-string]
      [nil]
      ["afterword.md" "invalid/file-name"]
      "not-vector"
      nil)))

(t/deftest catalog_appendices-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::catalog/appendices value)
      ["appendix.md"]
      ["appendix1.md" "appendix2.md"]
      []))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::catalog/appendices value))
      ["invalid/file-name"]
      [""]
      [:not-string]
      [nil]
      ["appendix.md" "invalid/file-name"]
      "not-vector"
      nil)))

(t/deftest catalog_chapters-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::catalog/chapters value)
      ["chapter.md"]
      ["chapter1.md" "chapter2.md"]
      []))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::catalog/chapters value))
      ["invalid/file-name"]
      [""]
      [:not-string]
      [nil]
      ["chapter.md" "invalid/file-name"]
      "not-vector"
      nil)))

(t/deftest catalog_forewords-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::catalog/forewords value)
      ["foreword.md"]
      ["foreword1.md" "foreword2.md"]
      []))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::catalog/forewords value))
      ["invalid/file-name"]
      [""]
      [:not-string]
      [nil]
      ["foreword.md" "invalid/file-name"]
      "not-vector"
      nil)))

(t/deftest catalog-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/catalog value)
      {:forewords []
       :chapters ["chapter1.md"]
       :appendices ["appendix1.md" "appendix2.md"]
       :afterwords ["afterword1.md" "afterword2.md" "afterword3.md"]}
      {:forewords [] :chapters [] :appendices [] :afterwords []}
      {:chapters ["chapter.md"]}
      {:chapters ["chapter.md"] :extra-key "extra-value"}))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/catalog value))
      {:chapters "not-vector"}
      {:chapters nil}
      {:forewords ["foreword.md"]
       :chapters ["chapter.md"]
       :appendices ["appendix.md"]
       :afterwords "not-vector"}
      {:extra-key "extra-value"}
      {}
      "not-map"
      nil)))

(t/deftest caption-test
  (t/testing "Succeeds to verify."
    (t/is (s/valid? ::spec/caption "caption")))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/caption value))
      ""
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

(t/deftest directive-name-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/directive-name value)
      "directivename"
      "DIRECTIVENAME"
      "DirectiveName"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/directive-name value))
      "1nvalid"
      "invalid directive name"
      ""
      :not-string
      nil)))

(t/deftest document_ast-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::document/ast value)
      {:type "type"}
      {:type "type" :extra-key "extra-value"}))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::document/ast value))
      {:type "12345"}
      {:type ""}
      {:type :not-string}
      {:type nil}
      {:extra-key "extra-value"}
      {}
      "not-map"
      nil)))

(t/deftest document_name-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::document/name value)
      "name.md"
      "日本語"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::document/name value))
      "invalid\\name"
      "invalid/name"
      "invalid:name"
      "invalid*name"
      "invalid?name"
      "invalid\"name"
      "invalid>name"
      "invalid<name"
      "invalid|name"
      ""
      :not-string
      nil)))

(t/deftest document_type-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::document/type value)
      :forewords
      :chapters
      :appendices
      :afterwords))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::document/type value))
      :invalid
      "forewords"
      nil)))

(t/deftest document-test
  (t/testing "Succeeds to verify."
    (t/is (s/valid? ::spec/document
                    {:name "name.md" :type :chapters :ast {:type "type"}})))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/document value))
      {:name :not-string :type :chapters :ast {:type "type"}}
      {:name "name.md" :type :invalid :ast {:type "type"}}
      {:name "name.md" :type :chapters :ast "not-map"}
      {:name "name.md" :type :chapters}
      {:name "name.md" :ast {:type "type"}}
      {:type :chapters :ast {:type "type"}}
      {:name "name.md"
       :type :chapters
       :ast {:type "type"}
       :extra-key "extra-value"}
      {:extra-key "extra-value"}
      {}
      "not-map"
      nil)))

(t/deftest documents-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/documents value)
      [{:name "name.md" :type :chapters :ast {:type "type"}}]
      [{:name "name1.md" :type :chapters :ast {:type "typeA"}}
       {:name "name2.md" :type :appendices :ast {:type "typeB"}}]
      []))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/documents value))
      [{:name :not-string :type :chapters :ast {:type "type"}}]
      [{:name "name.md" :type :invalid :ast {:type "type"}}]
      [{:name "name.md" :type :chapters :ast "not-map"}]
      [{:name "name.md" :type :chapters}]
      [{:name "name.md" :ast {:type "type"}}]
      [{:type :chapters :ast {:type "type"}}]
      [{:name "name.md"
        :type :chapters
        :ast {:type "type"}
        :extra-key "extra-value"}]
      [{:extra-key "extra-value"}]
      ["not-map"]
      [nil]
      [{:name "name1.md" :type :chapters :ast {:type "typeA"}}
       {:name :not-string :type :chapters :ast {:type "typeB"}}]
      "not-vector"
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

(t/deftest function-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/function value)
      (fn [x y] (+ x y))
      #(* % 2)
      inc))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/function value))
      "not-function"
      nil)))

(t/deftest log-data-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/log-data value)
      {:key "value"}
      {:key1 1 :key2 ["vector"]}
      {}
      nil))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/log-data value))
      {"not-keyword" "value"}
      {:key "value1" "not-keyword" "value2"}
      "not-map")))

(t/deftest log-level-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/log-level value)
      :info
      :warn
      :error))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/log-level value))
      :invalid
      "info"
      nil)))

(t/deftest log-message-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/log-message value)
      "log-message"
      ""))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/log-message value))
      :not-string
      nil)))

(t/deftest manuscript_markdown-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::manuscript/markdown value)
      "# Markdown"
      ""))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::manuscript/markdown value))
      :not-string
      nil)))

(t/deftest manuscript_name-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::manuscript/name value)
      "name.md"
      "日本語"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::manuscript/name value))
      "invalid\\name"
      "invalid/name"
      "invalid:name"
      "invalid*name"
      "invalid?name"
      "invalid\"name"
      "invalid>name"
      "invalid<name"
      "invalid|name"
      ""
      :not-string
      nil)))

(t/deftest manuscript_type-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::manuscript/type value)
      :forewords
      :chapters
      :appendices
      :afterwords))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::manuscript/type value))
      :invalid
      "forewords"
      nil)))

(t/deftest manuscript-test
  (t/testing "Succeeds to verify."
    (t/is (s/valid? ::spec/manuscript
                    {:name "name.md" :type :chapters :markdown "# Markdown"})))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/manuscript value))
      {:name :not-string :type :chapters :markdown "# Markdown"}
      {:name "name.md" :type :invalid :markdown "# Markdown"}
      {:name "name.md" :type :chapters :markdown :not-string}
      {:name "name.md" :type :chapters}
      {:name "name.md" :markdown "# Markdown"}
      {:type :chapters :markdown "# Markdown"}
      {:name "name.md"
       :type :chapters
       :markdown "# Markdown"
       :extra-key "extra-value"}
      {:extra-key "extra-value"}
      {}
      "not-map"
      nil)))

(t/deftest manuscripts-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/manuscripts value)
      [{:name "name.md" :type :chapters :markdown "# Markdown"}]
      [{:name "name1.md" :type :chapters :markdown "# Markdown1"}
       {:name "name2.md" :type :appendices :markdown "# Markdown2"}]
      []))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/manuscripts value))
      [{:name :not-string :type :chapters :markdown "# Markdown"}]
      [{:name "name.md" :type :invalid :markdown "# Markdown"}]
      [{:name "name.md" :type :chapters :markdown :not-string}]
      [{:name "name.md" :type :chapters}]
      [{:name "name.md" :markdown "# Markdown"}]
      [{:type :chapters :markdown "# Markdown"}]
      [{:name "name.md"
        :type :chapters
        :markdown "# Markdown"
        :extra-key "extra-value"}]
      [{:extra-key "extra-value"}]
      ["not-map"]
      [nil]
      [{:name "name1.md" :type :chapters :markdown "# Markdown1"}
       {:name :not-string :type :chapters :markdown "# Markdown2"}]
      "not-vector"
      nil)))

(t/deftest markdown-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/markdown value)
      "# Markdown"
      ""))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/markdown value))
      :not-string
      nil)))

(t/deftest module-name-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/module-name value)
      "package"
      "@scope/package"
      "./local"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/module-name value))
      "invalid module name"
      ""
      :not-string
      nil)))

(t/deftest node_type-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::node/type value)
      "type"
      "TYPE"
      "Type"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::node/type value))
      "12345"
      "type12345"
      ""
      :not-string
      nil)))

(t/deftest node-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/node value)
      {:type "type"}
      {:type "type" :extra-key "extra-value"}))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/node value))
      {:type "12345"}
      {:type ""}
      {:type :not-string}
      {:type nil}
      {:extra-key "extra-value"}
      {}
      "not-map"
      nil)))

(t/deftest node-type-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/node-type value)
      "nodetype"
      "NODETYPE"
      "nodeType"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/node-type value))
      "12345"
      "nodetype12345"
      ""
      :not-string
      nil)))

(t/deftest nodes-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/nodes value)
      [{:type "type"}]
      [{:type "typeOne"} {:type "typeTwo"}]
      []))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/nodes value))
      [{:type "12345"}]
      [{:type ""}]
      [{:type :not-string}]
      [{:type nil}]
      [{:extra-key "extra-value"}]
      ["not-map"]
      [nil]
      [{:type "type"} {:type ""}]
      "not-vector"
      nil)))

(t/deftest order-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/order value)
      0
      1
      100))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/order value))
      -1
      "1"
      nil)))

(t/deftest pred-result-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/pred-result value)
      true
      false))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/pred-result value))
      "true"
      :false
      nil)))

(t/deftest property-name-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/property-name value)
      "property"
      "_property"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/property-name value))
      "1nvalid"
      "invalid property name"
      ""
      :not-string
      nil)))

(t/deftest slug-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/slug value)
      "valid-slug"
      "日本語"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/slug value))
      "invalid!slug"
      "invalid\"slug"
      "invalid#slug"
      "invalid$slug"
      "invalid%slug"
      "invalid&slug"
      "invalid'slug"
      "invalid(slug"
      "invalid)slug"
      "invalid*slug"
      "invalid+slug"
      "invalid,slug"
      "invalid.slug"
      "invalid/slug"
      "invalid:slug"
      "invalid;slug"
      "invalid<slug"
      "invalid=slug"
      "invalid>slug"
      "invalid?slug"
      "invalid@slug"
      "invalid[slug"
      "invalid\\slug"
      "invalid]slug"
      "invalid^slug"
      "invalid`slug"
      "invalid{slug"
      "invalid|slug"
      "invalid}slug"
      "invalid~slug"
      "invalid slug"
      ""
      :not-string
      nil)))
