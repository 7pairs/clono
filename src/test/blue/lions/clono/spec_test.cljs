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
            [blue.lions.clono.spec.anchor :as anchor]
            [blue.lions.clono.spec :as spec]
            [blue.lions.clono.spec.catalog :as catalog]
            [blue.lions.clono.spec.common :as common]
            [blue.lions.clono.spec.directive :as directive]
            [blue.lions.clono.spec.document :as document]
            [blue.lions.clono.spec.heading :as heading]
            [blue.lions.clono.spec.index :as index]
            [blue.lions.clono.spec.log :as log]
            [blue.lions.clono.spec.manuscript :as manuscript]
            [blue.lions.clono.spec.node :as node]
            [blue.lions.clono.spec.toc :as toc]))

(t/deftest validate-test
  (t/testing "Succeeds to verify."
    (t/is (spec/validate ::common/alphabet-string
                         "alphabet"
                         "Invalid alphabet string."))
    (t/is (spec/validate ::common/alphabet-string
                         "alphabet"
                         "Invalid alphabet string."
                         :string)))

  (t/testing "Key is given."
    (try
      (spec/validate ::common/alphabet-string
                     "12345"
                     "Invalid alphabet string."
                     :string)
      (catch js/Error e
        (t/is (= "Invalid alphabet string." (ex-message e)))
        (t/is (= {:string "12345" :spec ::common/alphabet-string}
                 (ex-data e))))))

  (t/testing "Key is not given."
    (try
      (spec/validate ::common/alphabet-string
                     "12345"
                     "Invalid alphabet string.")
      (catch js/Error e
        (t/is (= "Invalid alphabet string." (ex-message e)))
        (t/is (= {:value "12345" :spec ::common/alphabet-string}
                 (ex-data e)))))))

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

(t/deftest anchor_chapter-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::anchor/chapter value)
      "valid|id"
      "日本語"
      nil))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::anchor/chapter value))
      "invalid\\id"
      "invalid/id"
      "invalid:id"
      "invalid*id"
      "invalid?id"
      "invalid\"id"
      "invalid>id"
      "invalid<id"
      ""
      :not-string)))

(t/deftest anchor_id-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::anchor/id value)
      "valid|id"
      "日本語"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::anchor/id value))
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

(t/deftest anchor-info-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/anchor-info value)
      {:chapter "chapter" :id "id"}
      {:chapter nil :id "id"}))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/anchor-info value))
      {:chapter :not-string :id "id"}
      {:chapter "chapter" :id :not-string}
      {:chapter "chapter"}
      {:id "id"}
      {:chapter "chapter" :id "id" :extra-key "extra-value"}
      {:extra-key "extra-value"}
      {}
      "not-map"
      nil)))

(t/deftest anchor-text-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/anchor-text value)
      "anchor-text"
      ""))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/anchor-text value))
      :not-string
      nil)))

(t/deftest attributes-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/attributes value)
      {:key "value"}
      {:key1 "value1" :key2 "value2"}
      {}))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/attributes value))
      {"not-keyword" "value"}
      {:key ""}
      {:key "value1" "not-keyword" "value2"}
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

(t/deftest depth-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/depth value)
      1
      2
      3
      4
      5
      6))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/depth value))
      -1
      0
      7
      3.5
      "1"
      nil)))

(t/deftest dic-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/dic value)
      {"key" "value"}
      {"key" {:type "text" :value "value"}}
      {"key1" "value1" "key2" {:type "text" :value "value2"}}
      {}))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/dic value))
      {:not-string "value"}
      {"key1" "value1" :not-string "value2"}
      "not-map"
      nil)))

(t/deftest dics-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/dics value)
      {:key {"key" "value"}}
      {:key1 {"key2" "value2"} :key3 {"key4" {:type "text"}}}
      {}))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/dics value))
      {"key1" {"key2" "value2"}}
      {:key "not-map"}
      {:key1 {"key2" "value2"} "key3" {"key4" "value4"}}
      "not-map"
      nil)))

(t/deftest directive_name-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::directive/name value)
      "directivename"
      "DIRECTIVENAME"
      "DirectiveName"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::directive/name value))
      "1nvalid"
      "invalid directive name"
      ""
      :not-string
      nil)))

(t/deftest directive_type-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::directive/type value)
      "textDirective"
      "containerDirective"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::directive/type value))
      "invalid"
      ""
      :not-string
      nil)))

(t/deftest directive-node-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/directive-node value)
      {:type "textDirective" :name "name" :attributes {:key "value"}}
      {:type "containerDirective" :name "name" :attributes {:key "value"}}))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/directive-node value))
      {:type "invalid" :name "name" :attributes {:key "value"}}
      {:type "" :name "name" :attributes {:key "value"}}
      {:type :not-string :name "name" :attributes {:key "value"}}
      {:type nil :name "name" :attributes {:key "value"}}
      {:type "textDirective" :name "1nvalid" :attributes {:key "value"}}
      {:type "textDirective" :name "" :attributes {:key "value"}}
      {:type "textDirective" :name :not-string :attributes {:key "value"}}
      {:type "textDirective" :name :nil :attributes {:key "value"}}
      {:type "containerDirective" :name "1nvalid" :attributes {:key "value"}}
      {:type "containerDirective" :name "" :attributes {:key "value"}}
      {:type "containerDirective" :name :not-string :attributes {:key "value"}}
      {:type "containerDirective" :name :nil :attributes {:key "value"}}
      {:name "name" :attributes {:key "value"}}
      {:type "textDirective" :attributes {:key "value"}}
      {:type "containerDirective" :attributes {:key "value"}}
      {}
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

(t/deftest document-type-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/document-type value)
      :forewords
      :chapters
      :appendices
      :afterwords))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/document-type value))
      :invalid
      "forewords"
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

(t/deftest footnote-dic-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/footnote-dic value)
      {"ID" {:type "type"}}
      {"ID1" {:type "typeA"} "ID2" {:type "typeB"}}
      {}))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/footnote-dic value))
      {:not-string {:type "type"}}
      {"ID" "not-node"}
      {"ID1" {:type "typeA"} :not-string {:type "typeB"}}
      "not-map"
      nil)))

(t/deftest formatted-attributes-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/formatted-attributes value)
      "attr=\"value\""
      "attr1=\"value1\" attr2=\"value2\""
      ""))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/formatted-attributes value))
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

(t/deftest function-or-nil-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/function-or-nil value)
      (fn [x y] (+ x y))
      #(* % 2)
      inc
      nil))

  (t/testing "Fails to verify."
    (t/is (not (s/valid? ::spec/function-or-nil "not-function")))))

(t/deftest github-slugger-test
  (t/testing "Succeeds to verify."
    (t/is (s/valid? ::spec/github-slugger
                    #js {:slug (fn [text] (str "slug-" text))
                         :reset (fn [] nil)})))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/github-slugger value))
      #js {:slug (fn [text] (str "slug-" text))}
      #js {:reset (fn [] nil)}
      #js {:slug "not-function" :reset (fn [] nil)}
      #js {:slug (fn [text] (str "slug-" text)) :reset "not-function"}
      #js {}
      "not-object"
      nil)))

(t/deftest heading_caption-test
  (t/testing "Succeeds to verify."
    (t/is (s/valid? ::heading/caption "caption")))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::heading/caption value))
      ""
      :not-string
      nil)))

(t/deftest heading_depth-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::heading/depth value)
      1
      2
      3
      4
      5
      6))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::heading/depth value))
      -1
      0
      7
      3.5
      "1"
      nil)))

(t/deftest heading_id-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::heading/id value)
      "valid|id"
      "日本語"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::heading/id value))
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

(t/deftest heading_url-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::heading/url value)
      "file-name.html#id"
      "日本語"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::heading/url value))
      "invalid\\url"
      "invalid/url"
      "invalid:url"
      "invalid*url"
      "invalid?url"
      "invalid\"url"
      "invalid>url"
      "invalid<url"
      ""
      :not-string
      nil)))

(t/deftest heading-test
  (t/testing "Succeeds to verify."
    (t/is (s/valid? ::spec/heading {:id "ID"
                                    :depth 1
                                    :caption "caption"
                                    :url "markdown.html#id"})))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/heading value))
      {:id :not-string :depth 1 :caption "caption" :url "markdown.html#id"}
      {:id "ID" :depth 0 :caption "caption" :url "markdown.html#id"}
      {:id "ID" :depth 1 :caption :not-string :url "markdown.html#id"}
      {:id "ID" :depth 1 :caption "caption" :url :not-string}
      {:depth 1 :caption "caption" :url "markdown.html#id"}
      {:id "ID" :caption "caption" :url "markdown.html#id"}
      {:id "ID" :depth 1 :url "markdown.html#id"}
      {:id "ID" :depth 1 :caption "caption"}
      {:id "ID"
       :depth 1
       :caption "caption"
       :url "markdown.html#id"
       :extra-key "extra-value"}
      {:extra-key "extra-value"}
      {}
      "not-map"
      nil)))

(t/deftest heading-dic-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/heading-dic value)
      {"ID" {:id "ID" :depth 1 :caption "caption" :url "markdown.html#id"}}
      {"ID1" {:id "ID1" :depth 1 :caption "caption1" :url "markdown.html#id1"}
       "ID2" {:id "ID2" :depth 2 :caption "caption2" :url "markdown.html#id2"}}
      {}))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/heading-dic value))
      {:not-string {:id "ID"
                    :depth 1
                    :caption "caption"
                    :url "markdown.html#id"}}
      {"ID" {:id :not-string
             :depth 1
             :caption "caption"
             :url "markdown.html#id"}}
      {"ID1" {:id "ID1" :depth 1 :caption "caption1" :url "markdown.html#id1"}
       "ID2" {:id "ID2" :depth 2 :caption "caption2" :url :not-string}}
      "not-map"
      nil)))

(t/deftest heading-or-nil-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/heading-or-nil value)
      {:id "ID" :depth 1 :caption "caption" :url "markdown.html#id"}
      nil))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/heading value))
      {:id :not-string :depth 1 :caption "caption" :url "markdown.html#id"}
      {:id "ID" :depth 0 :caption "caption" :url "markdown.html#id"}
      {:id "ID" :depth 1 :caption :not-string :url "markdown.html#id"}
      {:id "ID" :depth 1 :caption "caption" :url :not-string}
      {:depth 1 :caption "caption" :url "markdown.html#id"}
      {:id "ID" :caption "caption" :url "markdown.html#id"}
      {:id "ID" :depth 1 :url "markdown.html#id"}
      {:id "ID" :depth 1 :caption "caption"}
      {:id "ID"
       :depth 1
       :caption "caption"
       :url "markdown.html#id"
       :extra-key "extra-value"}
      {:extra-key "extra-value"}
      {}
      "not-map"
      nil)))

(t/deftest html-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/html value)
      "<h1>HTML</h1>"
      ""))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/html value))
      :not-string
      nil)))

(t/deftest id-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/id value)
      "valid|id"
      "日本語"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/id value))
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

(t/deftest id-or-nil-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/id-or-nil value)
      "valid|id"
      "日本語"
      nil))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/id-or-nil value))
      "invalid\\id"
      "invalid/id"
      "invalid:id"
      "invalid*id"
      "invalid?id"
      "invalid\"id"
      "invalid>id"
      "invalid<id"
      ""
      :not-string)))

(t/deftest index_caption-test
  (t/testing "Succeeds to verify."
    (t/is (s/valid? ::index/caption "caption")))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::index/caption value))
      ""
      :not-string
      nil)))

(t/deftest index_default-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::index/default value)
      true
      false))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::index/default value))
      "true"
      :false
      nil)))

(t/deftest index_language-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::index/language value)
      :english
      :japanese))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::index/language value))
      :invalid
      "english"
      nil)))

(t/deftest index_order-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::index/order value)
      0
      1
      100))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::index/order value))
      -1
      2.5
      "1"
      nil)))

(t/deftest index_pattern-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::index/pattern value)
      #"pattern"
      (re-pattern "pattern")))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::index/pattern value))
      "pattern"
      nil)))

(t/deftest index_ruby-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::index/ruby value)
      "ruby"
      "ルビ"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::index/ruby value))
      ""
      :not-string
      nil)))

(t/deftest index_text-test
  (t/testing "Succeeds to verify."
    (t/is (s/valid? ::index/text "text")))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::index/text value))
      ""
      :not-string
      nil)))

(t/deftest index_type-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::index/type value)
      :item
      :caption))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::index/type value))
      :invalid
      "item"
      nil)))

(t/deftest index_url-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::index/url value)
      "file-name.html#id"
      "日本語"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::index/url value))
      "invalid\\url"
      "invalid/url"
      "invalid:url"
      "invalid*url"
      "invalid?url"
      "invalid\"url"
      "invalid>url"
      "invalid<url"
      ""
      :not-string
      nil)))

(t/deftest index_urls-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::index/urls value)
      ["file-name.html#id"]
      ["file-name.html#id1" "file-name.html#id2"]))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::index/urls value))
      ["invalid\\url"]
      ["file-name.html#id" "invalid\\url"]
      []
      "not-vector"
      nil)))

(t/deftest index-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/index value)
      {:type :item :text "text" :ruby "ruby" :urls ["file-name.html#id"]}
      {:type :caption :text "text"})

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/index value))
      {:type "item" :text "text" :ruby "ruby" :urls ["file-name.html#id"]}
      {:type :item :text :not-string :ruby "ruby" :urls ["file-name.html#id"]}
      {:type :item :text "text" :ruby :not-string :urls ["file-name.html#id"]}
      {:type :item :text "text" :ruby "ruby" :urls "not-vector"}
      {:text "text" :ruby "ruby" :urls ["file-name.html#id"]}
      {:type :item :ruby "ruby" :urls ["file-name.html#id"]}
      {:type :item :text "text" :urls ["file-name.html#id"]}
      {:type :item :text "text" :ruby "ruby"}
      {:type :item
       :text "text"
       :ruby "ruby"
       :urls ["file-name.html#id"]
       :extra-key "extra-value"}
      {:type "caption" :text "text"}
      {:type :caption :text :not-string}
      {:type :caption}
      {:text "text"}
      {:type :caption :text "text" :extra-key "extra-value"}
      {:extra-key "extra-value"}
      "not-map"
      nil))))

(t/deftest index-caption-test
  (t/testing "Succeeds to verify."
    (t/is (s/valid? ::spec/index-caption {:type :caption :text "text"})))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/index-caption value))
      {:type "caption" :text "text"}
      {:type :caption :text :not-string}
      {:type :caption}
      {:text "text"}
      {:type :caption :text "text" :extra-key "extra-value"}
      {:extra-key "extra-value"}
      "not-map"
      nil)))

(t/deftest index-entry-test
  (t/testing "Succeeds to verify."
    (t/is (s/valid? ::spec/index-entry {:order 1
                                        :text "text"
                                        :ruby "ruby"
                                        :url "file-name.html#id"})))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/index-entry value))
      {:order "1" :text "text" :ruby "ruby" :url "file-name.html#id"}
      {:order 1 :text :not-string :ruby "ruby" :url "file-name.html#id"}
      {:order 1 :text "text" :ruby :not-string :url "file-name.html#id"}
      {:order 1 :text "text" :ruby "ruby" :url :not-string}
      {:text "text" :ruby "ruby" :url "file-name.html#id"}
      {:order 1 :ruby "ruby" :url "file-name.html#id"}
      {:order 1 :text "text" :url "file-name.html#id"}
      {:order 1 :text "text" :ruby "ruby"}
      {:order 1
       :text "text"
       :ruby "ruby"
       :url "file-name.html#id"
       :extra-key "extra-value"}
      {:extra-key "extra-value"}
      "not-map"
      nil)))

(t/deftest index-group-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/index-group value)
      {:caption "caption"
       :pattern #"pattern"
       :language :english
       :default true}
      {:caption "caption" :language :english :default true}
      {:caption "caption" :pattern #"pattern" :default true}
      {:caption "caption" :pattern #"pattern" :language :english}
      {:caption "caption" :pattern #"pattern"}
      {:caption "caption" :default true}))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/index-group value))
      {:caption :not-string
       :pattern #"pattern"
       :language :english
       :default true}
      {:caption "caption"
       :pattern "pattern"
       :language :english
       :default true}
      {:caption "caption"
       :pattern #"pattern"
       :language "english"
       :default true}
      {:caption "caption"
       :pattern #"pattern"
       :language :english
       :default "true"}
      {:caption "caption" :language :english}
      {:pattern #"pattern" :language :english :default true}
      {:caption "caption"
       :pattern #"pattern"
       :language :english
       :default true
       :extra-key "extra-value"}
      {:extra-key "extra-value"}
      {}
      "not-map"
      nil)))

(t/deftest index-groups-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/index-groups value)
      [{:caption "caption"
        :pattern #"pattern"
        :language :english
        :default true}]
      [{:caption "caption" :language :english :default true}
       {:caption "caption" :pattern #"pattern" :default true}]
      []))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/index-groups value))
      [{:caption :not-string
        :pattern #"pattern"
        :language :english
        :default true}]
      [{:caption "caption"
        :pattern "pattern"
        :language :english
        :default true}]
      [{:caption "caption"
        :pattern #"pattern"
        :language "english"
        :default true}]
      [{:caption "caption"
        :pattern #"pattern"
        :language :english
        :default "true"}]
      [{:caption "caption"
        :pattern #"pattern"
        :language :english
        :default true}
       {:caption :not-string
        :pattern #"pattern"
        :language :english
        :default true}]
      "not-vector"
      nil)))

(t/deftest index-item-test
  (t/testing "Succeeds to verify."
    (t/is (s/valid? ::spec/index-item {:type :item
                                       :text "text"
                                       :ruby "ruby"
                                       :urls ["file-name.html#id"]})))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/index-item value))
      {:type "item" :text "text" :ruby "ruby" :urls ["file-name.html#id"]}
      {:type :item :text :not-string :ruby "ruby" :urls ["file-name.html#id"]}
      {:type :item :text "text" :ruby :not-string :urls ["file-name.html#id"]}
      {:type :item :text "text" :ruby "ruby" :urls "not-vector"}
      {:text "text" :ruby "ruby" :urls ["file-name.html#id"]}
      {:type :item :ruby "ruby" :urls ["file-name.html#id"]}
      {:type :item :text "text" :urls ["file-name.html#id"]}
      {:type :item :text "text" :ruby "ruby"}
      {:type :item
       :text "text"
       :ruby "ruby"
       :urls ["file-name.html#id"]
       :extra-key "extra-value"}
      {:extra-key "extra-value"}
      "not-map"
      nil)))

(t/deftest indices-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/indices value)
      [{:type :item :text "text" :ruby "ruby" :urls ["file-name.html#id"]}]
      [{:type :caption :text "text"}]
      [{:type :item :text "text1" :ruby "ruby1" :urls ["file-name.html#id1"]}
       {:type :item :text "text2" :ruby "ruby2" :urls ["file-name.html#id2"]}]
      [{:type :caption :text "text1"} {:type :caption :text "text2"}]
      [{:type :item :text "text1" :ruby "ruby" :urls ["file-name.html#id"]}
       {:type :caption :text "text2"}]
      []))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/indices value))
      [{:type "item" :text "text" :ruby "ruby" :urls ["url"]}]
      [{:type :item :text :not-string :ruby "ruby" :urls ["url"]}]
      [{:type :item :text "text" :ruby :not-string :urls ["url"]}]
      [{:type :item :text "text" :ruby "ruby" :urls "not-vector"}]
      [{:text "text" :ruby "ruby" :urls ["url"]}]
      [{:type :item :ruby "ruby" :urls ["url"]}]
      [{:type :item :text "text" :urls ["url"]}]
      [{:type :item :text "text" :ruby "ruby"}]
      [{:type :item
        :text "text"
        :ruby "ruby"
        :urls ["url"]
        :extra-key "extra-value"}]
      [{:type "caption" :text "text"}]
      [{:type :caption :text :not-string}]
      [{:type :caption}]
      [{:text "text"}]
      [{:type :caption :text "text" :extra-key "extra-value"}]
      [{:extra-key "extra-value"}]
      ["not-vector"]
      [{:type :item :text "text" :ruby "ruby" :urls ["url"]}
       {:type "caption" :text "text"}]
      [{:type "item" :text "text" :ruby "ruby" :urls ["url"]}
       {:type :caption :text "text"}]
      nil)))

(t/deftest log_data-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::log/data value)
      {:key "value"}
      {:key1 1 :key2 ["vector"]}
      {}
      nil))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::log/data value))
      {"not-keyword" "value"}
      {:key "value1" "not-keyword" "value2"}
      "not-map")))

(t/deftest log_level-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::log/level value)
      :debug
      :info
      :warn
      :error))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::log/level value))
      :invalid
      "info"
      nil)))

(t/deftest log_message-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::log/message value)
      "message"
      ""))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::log/message value))
      :not-string
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

(t/deftest log-entries-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/log-entries value)
      [{:level :info :message "message" :data {:key "value"}}]
      [{:level :info :message "message1" :data {:key "value1"}}
       {:level :error :message "message2" :data {:key "value2"}}]
      []))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/log-entries value))
      [{:level :invalid :message "message" :data {:key "value"}}]
      [{:level :info :message :not-string :data {:key "value"}}]
      [{:level :info :message "message" :data "not-map"}]
      [{:message "message" :data {:key "value"}}]
      [{:level :info :data {:key "value"}}]
      [{:level :info :message "message"}]
      [{:level :info
        :message "message"
        :data {:key "value"}
        :extra-key "extra-value"}]
      [{:extra-key "extra-value"}]
      [{:level :info :message "message" :data {:key "value"}}
       {:level :invalid :message "message" :data {:key "value"}}]
      "not-vector"
      nil)))

(t/deftest log-entry-test
  (t/testing "Succeeds to verify."
    (t/is (s/valid? ::spec/log-entry {:level :info
                                      :message "message"
                                      :data {:key "value"}})))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/log-entry value))
      {:level :invalid :message "message" :data {:key "value"}}
      {:level :info :message :not-string :data {:key "value"}}
      {:level :info :message "message" :data "not-map"}
      {:message "message" :data {:key "value"}}
      {:level :info :data {:key "value"}}
      {:level :info :message "message"}
      {:level :info
       :message "message"
       :data {:key "value"}
       :extra-key "extra-value"}
      {:extra-key "extra-value"}
      {}
      "not-map"
      nil)))

(t/deftest log-level-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/log-level value)
      :debug
      :info
      :warn
      :error))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/log-level value))
      :invalid
      "info"
      nil)))

(t/deftest log-level-value-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/log-level-value value)
      0
      1
      2
      3))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/log-level-value value))
      -1
      4
      2.5
      "1"
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

(t/deftest node-or-nil-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/node-or-nil value)
      {:type "type"}
      {:type "type" :extra-key "extra-value"}
      nil))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/node-or-nil value))
      {:type "12345"}
      {:type ""}
      {:type :not-string}
      {:type nil}
      {:extra-key "extra-value"}
      {}
      "not-map")))

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
      2.5
      "1"
      nil)))

(t/deftest pattern-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/pattern value)
      #"pattern"
      (re-pattern "pattern")))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/pattern value))
      "pattern"
      nil)))

(t/deftest pattern-string-test
  (t/testing "Succeeds to verify."
    (t/is (s/valid? ::spec/pattern-string "pattern")))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/pattern-string value))
      ""
      :not-string
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

(t/deftest ruby-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/ruby value)
      "ruby"
      "ルビ"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/ruby value))
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

(t/deftest toc_caption-test
  (t/testing "Succeeds to verify."
    (t/is (s/valid? ::toc/caption "caption")))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::toc/caption value))
      ""
      :not-string
      nil)))

(t/deftest toc_depth-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::toc/depth value)
      1
      2
      3
      4
      5
      6))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::toc/depth value))
      -1
      0
      7
      3.5
      "1"
      nil)))

(t/deftest toc_url-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::toc/url value)
      "file-name.html#id"
      "日本語"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::toc/url value))
      "invalid\\url"
      "invalid/url"
      "invalid:url"
      "invalid*url"
      "invalid?url"
      "invalid\"url"
      "invalid>url"
      "invalid<url"
      ""
      :not-string
      nil)))

(t/deftest toc-item-test
  (t/testing "Succeeds to verify."
    (t/is (s/valid? ::spec/toc-item {:depth 1
                                     :caption "caption"
                                     :url "markdown.html#id"})))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/toc-item value))
      {:depth 0 :caption "caption" :url "markdown.html#id"}
      {:depth 1 :caption :not-string :url "markdown.html#id"}
      {:depth 1 :caption "caption" :url :not-string}
      {:caption "caption" :url "markdown.html#id"}
      {:depth 1 :url "markdown.html#id"}
      {:depth 1 :caption "caption"}
      {:depth 1
       :caption "caption"
       :url "markdown.html#id"
       :extra-key "extra-value"}
      {:extra-key "extra-value"}
      {}
      "not-map"
      nil)))

(t/deftest toc-items-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/toc-items value)
      [{:depth 1 :caption "caption" :url "markdown.html#id"}]
      [{:depth 1 :caption "caption1" :url "markdown.html#id1"}
       {:depth 2 :caption "caption2" :url "markdown.html#id2"}]
      []))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/toc-items value))
      [{:depth 0 :caption "caption" :url "markdown.html#id"}]
      [{:depth 1 :caption :not-string :url "markdown.html#id"}]
      [{:depth 1 :caption "caption" :url :not-string}]
      [{:caption "caption" :url "markdown.html#id"}]
      [{:depth 1 :url "markdown.html#id"}]
      [{:depth 1 :caption "caption"}]
      [{:depth 1
        :caption "caption"
        :url "markdown.html#id"
        :extra-key "extra-value"}]
      [{:extra-key "extra-value"}]
      ["not-map"]
      [nil]
      [{:depth 1 :caption "caption1" :url "markdown.html#id1"}
       {:depth 0 :caption "caption2" :url "markdown.html#id2"}]
      "not-vector"
      nil)))

(t/deftest url-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/url value)
      "file-name.html#id"
      "日本語"))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/url value))
      "invalid\\url"
      "invalid/url"
      "invalid:url"
      "invalid*url"
      "invalid?url"
      "invalid\"url"
      "invalid>url"
      "invalid<url"
      ""
      :not-string
      nil)))

(t/deftest urls-test
  (t/testing "Succeeds to verify."
    (t/are [value] (s/valid? ::spec/urls value)
      ["file-name.html#id"]
      ["file-name.html#id" "日本語"]
      []))

  (t/testing "Fails to verify."
    (t/are [value] (not (s/valid? ::spec/urls value))
      ["invalid\\url"]
      ["invalid/url"]
      ["invalid:url"]
      ["invalid*url"]
      ["invalid?url"]
      ["invalid\"url"]
      ["invalid>url"]
      ["invalid<url"]
      [:not-string]
      [nil]
      ["file-name.html#id" "invalid\\url"]
      "not-vector"
      nil)))
