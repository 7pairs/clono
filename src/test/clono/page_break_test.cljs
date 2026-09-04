(ns clono.page-break-test
  (:require
   ["node:fs" :as fs]
   [cljs.test :refer [deftest is testing]]
   [clono.markdown :as markdown]
   [clono.pipeline :as pipeline]
   [clono.test-support :as test-support]))

(def valid-page-break-source
  (str "改ページ前の段落です。\n\n"
       "[site]: https://example.com/\n\n"
       "::page-break\n\n"
       "[^note]: 紙面上のブロックとして数えない脚注定義です。\n\n"
       "# 次のページの見出し\n"))

(def invalid-page-break-cases
  [{:case "text directive"
    :source "前の段落。\n\n:page-break[改ページ]\n\n後の段落。\n"
    :message "`page-break`はLeaf directiveとして記述する必要があります。"}
   {:case "container directive"
    :source "前の段落。\n\n:::page-break\n本文です。\n:::\n\n後の段落。\n"
    :message "`page-break`はLeaf directiveとして記述する必要があります。"}
   {:case "label"
    :source "前の段落。\n\n::page-break[改ページ]\n\n後の段落。\n"
    :message "`page-break`にはラベルを指定できません。"}
   {:case "attribute"
    :source "前の段落。\n\n::page-break{side=\"right\"}\n\n後の段落。\n"
    :message "`page-break`には属性を指定できません。"}
   {:case "document start"
    :source "::page-break\n\n後の段落。\n"
    :message "`page-break`の前には紙面へ表示されるトップレベルブロックが必要です。"}
   {:case "document start after definitions"
    :source "[site]: https://example.com/\n\n::page-break\n\n後の段落。\n"
    :message "`page-break`の前には紙面へ表示されるトップレベルブロックが必要です。"}
   {:case "document end"
    :source "前の段落。\n\n::page-break\n"
    :message "`page-break`の後には紙面へ表示されるトップレベルブロックが必要です。"}
   {:case "document end before definitions"
    :source "前の段落。\n\n::page-break\n\n[site]: https://example.com/\n"
    :message "`page-break`の後には紙面へ表示されるトップレベルブロックが必要です。"}
   {:case "consecutive directives"
    :source "前の段落。\n\n::page-break\n\n::page-break\n\n後の段落。\n"
    :message "`page-break`を連続して記述できません。"}
   {:case "consecutive directives around definitions"
    :source (str "前の段落。\n\n"
                 "::page-break\n\n"
                 "[site]: https://example.com/\n\n"
                 "::page-break\n\n"
                 "後の段落。\n")
    :message "`page-break`を連続して記述できません。"}
   {:case "inside blockquote"
    :source "前の段落。\n\n> 引用です。\n>\n> ::page-break\n\n後の段落。\n"
    :message "`page-break`はMarkdown文書のトップレベルに記述する必要があります。"}
   {:case "inside list item"
    :source "前の段落。\n\n- 項目\n\n  ::page-break\n\n後の段落。\n"
    :message "`page-break`はMarkdown文書のトップレベルに記述する必要があります。"}
   {:case "inside footnote definition"
    :source (str "前の段落[^note]。\n\n"
                 "[^note]:\n"
                 "    ::page-break\n\n"
                 "後の段落。\n")
    :message "`page-break`はMarkdown文書のトップレベルに記述する必要があります。"}])

(defn- normalize-line-endings [value]
  (.replace value (js/RegExp. "\\r\\n?" "g") "\n"))

(deftest page-break-transformation-test
  (let [result (pipeline/run {:mode :transform :source-name "page-break.md"}
                             valid-page-break-source)
        output (:output result)
        tree (markdown/parse output)]
    (testing "When a valid page-break directive is transformed, then a fixed hidden marker and surrounding Markdown are returned"
      (is (true? (:ok? result)))
      (is (empty? (:diagnostics result)))
      (is (.includes
           output
           "<div class=\"clono-page-break\" aria-hidden=\"true\"></div>"))
      (is (nil? (test-support/directive tree "page-break")))
      (is (= 1 (count (test-support/nodes-by-type tree "html"))))
      (is (= 1 (count (test-support/nodes-by-type tree "heading"))))
      (is (= 1 (count (test-support/nodes-by-type tree "definition"))))
      (is (= 1 (count (test-support/nodes-by-type tree "footnoteDefinition"))))))

  (testing "When the clono stylesheet is inspected, then it provides the required forced-page-break rule"
    (let [stylesheet (normalize-line-endings
                      (.readFileSync fs "styles/clono.css" "utf8"))]
      (is (.includes stylesheet
                     ".clono-page-break {\n  break-before: page;\n}\n")))))

(deftest invalid-page-break-test
  (testing "When a page-break directive violates its contract, then transformation fails with a positioned diagnostic"
    (doseq [{:keys [case source message]} invalid-page-break-cases]
      (let [result (pipeline/run {:mode :transform
                                  :source-name "invalid-page-break.md"}
                                 source)
            problem (first (:diagnostics result))]
        (is (false? (:ok? result)) case)
        (is (nil? (:output result)) case)
        (is (= 1 (count (:diagnostics result))) case)
        (is (= {:file "invalid-page-break.md"
                :directive "page-break"
                :message message}
               (select-keys problem [:file :directive :message]))
            case)
        (is (pos-int? (:line problem)) case)
        (is (pos-int? (:column problem)) case)))))

(deftest page-break-diagnostic-integration-test
  (testing "When page-break is inside a known container, then only the container contract is reported"
    (let [source (str "::::column[コラム]\n"
                      "本文です。\n\n"
                      "::page-break\n"
                      "::::\n")
          result (pipeline/run {:mode :transform
                                :source-name "page-break-in-column.md"}
                               source)]
      (is (= [{:file "page-break-in-column.md"
               :line 4
               :column 1
               :directive "column"
               :message "`column`内ではdirectiveを使用できません。"}]
             (:diagnostics result)))
      (is (nil? (:output result)))))

  (testing "When page-break is inside an unknown container, then only the unknown container is reported"
    (let [source (str "::::third-party\n"
                      "::page-break\n"
                      "::::\n")
          result (pipeline/run {:mode :transform
                                :source-name "page-break-in-unknown.md"}
                               source)]
      (is (= [{:file "page-break-in-unknown.md"
               :line 1
               :column 1
               :directive "third-party"
               :message "`third-party`は登録されていないdirectiveです。"}]
             (:diagnostics result)))
      (is (nil? (:output result))))))
