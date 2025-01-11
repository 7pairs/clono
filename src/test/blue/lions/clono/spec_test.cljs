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

(deftest catalog_afterwords-test
  (testing "Succeeds to verify."
    (are [target] (s/valid? :blue.lions.clono.spec.catalog/afterwords target)
      ["afterword.md"]
      ["afterword1.md" "afterword2.md"]
      []))

  (testing "Fails to verify."
    (are [target] (not (s/valid? :blue.lions.clono.spec.catalog/afterwords
                                 target))
      ["invalid/file-name"]
      [""]
      [:not-string]
      [nil]
      ["afterword.md" "invalid/file-name"]
      "not-vector"
      nil)))

(deftest catalog_appendices-test
  (testing "Succeeds to verify."
    (are [target] (s/valid? :blue.lions.clono.spec.catalog/appendices target)
      ["appendix.md"]
      ["appendix1.md" "appendix2.md"]
      []))

  (testing "Fails to verify."
    (are [target] (not (s/valid? :blue.lions.clono.spec.catalog/appendices
                                 target))
      ["invalid/file-name"]
      [""]
      [:not-string]
      [nil]
      ["appendix.md" "invalid/file-name"]
      "not-vector"
      nil)))

(deftest catalog_chapters-test
  (testing "Succeeds to verify."
    (are [target] (s/valid? :blue.lions.clono.spec.catalog/chapters target)
      ["chapter.md"]
      ["chapter1.md" "chapter2.md"]
      []))

  (testing "Fails to verify."
    (are [target] (not (s/valid? :blue.lions.clono.spec.catalog/chapters
                                 target))
      ["invalid/file-name"]
      [""]
      [:not-string]
      [nil]
      ["chapter.md" "invalid/file-name"]
      "not-vector"
      nil)))

(deftest catalog_forewords-test
  (testing "Succeeds to verify."
    (are [target] (s/valid? :blue.lions.clono.spec.catalog/forewords target)
      ["foreword.md"]
      ["foreword1.md" "foreword2.md"]
      []))

  (testing "Fails to verify."
    (are [target] (not (s/valid? :blue.lions.clono.spec.catalog/forewords
                                 target))
      ["invalid/file-name"]
      [""]
      [:not-string]
      [nil]
      ["foreword.md" "invalid/file-name"]
      "not-vector"
      nil)))

(deftest catalog-test
  (testing "Succeeds to verify."
    (are [target] (s/valid? ::spec/catalog target)
      {:forewords ["foreword1.md" "foreword2.md"]
       :chapters ["chapter1.md" "chapter2.md"]
       :appendices ["appendix1.md" "appendix2.md"]
       :afterwords ["afterword1.md" "afterword2.md"]}
      {:forewords [] :chapters [] :appendices [] :afterwords []}
      {:chapters ["chapter.md"]}
      {:chapters ["chapter.md"] :extra-key "extra-value"}))

  (testing "Fails to verify."
    (are [target] (not (s/valid? ::spec/catalog target))
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

(deftest config-test
  (testing "Succeeds to verify."
    (are [target] (s/valid? ::spec/config target)
      {:key "value"}
      {:key1 1 :key2 ["vector"]}
      {}))

  (testing "Fails to verify."
    (are [target] (not (s/valid? ::spec/config target))
      {"not-keyword" "value"}
      {:key "value1" "not-keyword" "value2"}
      "not-map"
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

(deftest file-name-test
  (testing "Succeeds to verify."
    (are [target] (s/valid? ::spec/file-name target)
      "file-name.md"
      "日本語"))

  (testing "Fails to verify."
    (are [target] (not (s/valid? ::spec/file-name target))
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

(deftest file-names-test
  (testing "Succeeds to verify."
    (are [target] (s/valid? ::spec/file-names target)
      ["file.md"]
      ["file1.md" "file2.md"]
      []))

  (testing "Fails to verify."
    (are [target] (not (s/valid? ::spec/file-names target))
      ["invalid/file-name"]
      [""]
      [:not-string]
      [nil]
      ["file.md" "invalid/file-name"]
      "not-vector"
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

(deftest markdown-test
  (testing "Succeeds to verify."
    (are [target] (s/valid? ::spec/markdown target)
      "# Markdown"
      ""))

  (testing "Fails to verify."
    (are [target] (not (s/valid? ::spec/markdown target))
      :not-string
      nil)))

(deftest name-and-markdown_name-test
  (testing "Succeeds to verify."
    (are [target] (s/valid? :blue.lions.clono.spec.name-and-markdown/name
                            target)
      "file-name.md"
      "日本語"))

  (testing "Fails to verify."
    (are [target] (not (s/valid? :blue.lions.clono.spec.name-and-markdown/name
                                 target))
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

(deftest name-and-markdown_markdown-test
  (testing "Succeeds to verify."
    (are [target] (s/valid? :blue.lions.clono.spec.name-and-markdown/markdown
                            target)
      "# Markdown"
      ""))

  (testing "Fails to verify."
    (are [target] (not (s/valid?
                        :blue.lions.clono.spec.name-and-markdown/markdown
                        target))
      :not-string
      nil)))

(deftest name-and-markdown-test
  (testing "Succeeds to verify."
    (is (s/valid? ::spec/name-and-markdown
                  {:name "markdown.md", :markdown "# Markdown"})))

  (testing "Fails to verify."
    (are [target] (not (s/valid? ::spec/name-and-markdown target))
      {:name :not-string :markdown "# Markdown"}
      {:name "markdown.md" :markdown :not-string}
      {:name "markdown.md"}
      {:markdown "# Markdown"}
      {:name "markdown.md" :markdown "# Markdown" :extra-key "extra-value"}
      {:extra-key "extra-value"}
      {}
      "not-map"
      nil)))

(deftest name-and-markdown-list-test
  (testing "Succeeds to verify."
    (are [target] (s/valid? ::spec/name-and-markdown-list target)
      [{:name "markdown.md" :markdown "# Markdown"}]
      [{:name "markdown1.md" :markdown "# Markdown1"}
       {:name "markdown2.md" :markdown "# Markdown2"}]
      []))

  (testing "Fails to verify."
    (are [target] (not (s/valid? ::spec/name-and-markdown-list target))
      [{:name :not-string :markdown "# Markdown"}]
      [{:name "markdown.md" :markdown :not-string}]
      [{:name "markdown.md"}]
      [{:markdown "# Markdown"}]
      [{:name "markdown.md" :markdown "# Markdown" :extra-key "extra-value"}]
      ["not-map"]
      [nil]
      [{:name "markdown.md" :markdown "# Markdown1"}
       {:name :not-string :markdown "# Markdown2"}]
      "not-vector"
      nil)))
