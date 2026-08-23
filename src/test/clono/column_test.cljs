(ns clono.column-test
  (:require
   ["node:fs" :as fs]
   [cljs.test :refer [deftest is testing]]
   [clono.markdown :as markdown]
   [clono.pipeline :as pipeline]
   [clono.test-support :as test-support]))

(def valid-column-source
  (str ":::column[A &amp; B &lt;unsafe&gt;]\n"
       "**太字**と*強調*、`コード`、[リンク][site]、画像![画像](image.png)です[^note]。\\\n"
       "次の行です。\n\n"
       "- 箇条書き\n"
       "  - 入れ子\n\n"
       "1. 番号付き\n\n"
       "> 引用です。\n\n"
       "```clojure\n"
       "(+ 1 1)\n"
       "```\n\n"
       "![参照画像][figure]\n\n"
       "| 項目 | 値 |\n"
       "| --- | --- |\n"
       "| A | 1 |\n"
       ":::\n\n"
       "[site]: https://example.com/\n"
       "[figure]: figure.png\n\n"
       "[^note]: 脚注の本文です。\n"))

(def invalid-column-cases
  [{:case "text directive"
    :source ":column[コラム]\n"
    :message "`column`はContainer directiveとして記述する必要があります。"}
   {:case "leaf directive"
    :source "::column[コラム]\n"
    :message "`column`はContainer directiveとして記述する必要があります。"}
   {:case "missing title"
    :source ":::column\n本文です。\n:::\n"
    :message "`column`にはプレーンテキストのタイトルが必要です。"}
   {:case "empty title"
    :source ":::column[]\n本文です。\n:::\n"
    :message "`column`のタイトルには空白ではないプレーンテキストが必要です。"}
   {:case "formatted title"
    :source ":::column[**強調タイトル**]\n本文です。\n:::\n"
    :message "`column`のタイトルには空白ではないプレーンテキストが必要です。"}
   {:case "attribute"
    :source ":::column[コラム]{class=\"custom\"}\n本文です。\n:::\n"
    :message "`column`には属性を指定できません。"}
   {:case "empty body"
    :source ":::column[コラム]\n:::\n"
    :message "`column`には1個以上の本文ブロックが必要です。"}
   {:case "heading"
    :source ":::column[コラム]\n# 見出し\n:::\n"
    :message "`column`内では見出しを使用できません。"}
   {:case "thematic break"
    :source ":::column[コラム]\n---\n:::\n"
    :message "`column`内では水平線を使用できません。"}
   {:case "raw HTML"
    :source ":::column[コラム]\n<div>HTML</div>\n:::\n"
    :message "`column`内ではraw HTMLを使用できません。"}
   {:case "footnote definition"
    :source (str ":::column[コラム]\n"
                 "脚注です[^note]。\n\n"
                 "[^note]: コラム内の脚注定義です。\n"
                 ":::\n")
    :message "`column`内では脚注定義を使用できません。"}
   {:case "link definition"
    :source (str ":::column[コラム]\n"
                 "[リンク][site]\n\n"
                 "[site]: https://example.com/\n"
                 ":::\n")
    :message "`column`内ではリンクまたは画像の定義を使用できません。"}
   {:case "nested column"
    :source (str "::::column[外側]\n"
                 ":::column[内側]\n"
                 "本文です。\n"
                 ":::\n"
                 "::::\n")
    :message "`column`内ではdirectiveを使用できません。"}
   {:case "align directive"
    :source (str "::::column[コラム]\n"
                 ":::align{position=\"right\"}\n"
                 "本文です。\n"
                 ":::\n"
                 "::::\n")
    :message "`column`内ではdirectiveを使用できません。"}])

(defn- normalize-line-endings [value]
  (.replace value (js/RegExp. "\\r\\n?" "g") "\n"))

(deftest column-transformation-test
  (let [result (pipeline/run "column.md" valid-column-source)
        output (:output result)
        tree (markdown/parse output)]
    (testing "When a valid column directive is transformed, then fixed wrapper HTML and supported Markdown are returned"
      (is (true? (:ok? result)))
      (is (empty? (:diagnostics result)))
      (is (.includes output "<aside class=\"clono-column\">"))
      (is (.includes output
                     "<p class=\"clono-column-title\">A &amp; B &lt;unsafe&gt;</p>"))
      (is (.includes output "</aside>"))
      (is (nil? (test-support/directive tree "column")))
      (is (= 3 (count (test-support/nodes-by-type tree "html"))))
      (is (= 1 (count (test-support/nodes-by-type tree "strong"))))
      (is (= 1 (count (test-support/nodes-by-type tree "emphasis"))))
      (is (= 1 (count (test-support/nodes-by-type tree "inlineCode"))))
      (is (= 1 (count (test-support/nodes-by-type tree "linkReference"))))
      (is (= 1 (count (test-support/nodes-by-type tree "break"))))
      (let [lists (test-support/nodes-by-type tree "list")]
        (is (= 3 (count lists)))
        (is (= 1 (count (filter #(true? (.-ordered %)) lists))))
        (is (= 2 (count (filter #(not (true? (.-ordered %))) lists)))))
      (is (= 1 (count (test-support/nodes-by-type tree "blockquote"))))
      (is (= 1 (count (test-support/nodes-by-type tree "code"))))
      (is (= 1 (count (test-support/nodes-by-type tree "image"))))
      (is (= 1 (count (test-support/nodes-by-type tree "imageReference"))))
      (is (= 1 (count (test-support/nodes-by-type tree "table"))))
      (is (= 1 (count (test-support/nodes-by-type tree "footnoteReference"))))
      (is (= 1 (count (test-support/nodes-by-type tree "footnoteDefinition"))))))

  (testing "When the clono stylesheet is inspected, then it provides the required column fragmentation rules"
    (let [stylesheet (normalize-line-endings
                      (.readFileSync fs "styles/clono.css" "utf8"))]
      (is (.includes stylesheet
                     ".clono-column {\n  break-inside: avoid;\n}\n"))
      (is (.includes stylesheet
                     ".clono-column-title {\n  break-after: avoid;\n}\n")))))

(deftest invalid-column-test
  (testing "When a column directive violates its contract, then transformation fails with a positioned diagnostic"
    (doseq [{:keys [case source message]} invalid-column-cases]
      (let [result (pipeline/run "invalid-column.md" source)
            problem (first (:diagnostics result))]
        (is (false? (:ok? result)) case)
        (is (nil? (:output result)) case)
        (is (= 1 (count (:diagnostics result))) case)
        (is (= {:file "invalid-column.md"
                :directive "column"
                :message message}
               (select-keys problem [:file :directive :message]))
            case)
        (is (pos-int? (:line problem)) case)
        (is (pos-int? (:column problem)) case)))))

(deftest column-diagnostic-integration-test
  (testing "When an unknown container is inside column, then only the unknown container is reported"
    (let [source (str "::::column[コラム]\n"
                      ":::third-party\n"
                      "未知の内容です。\n"
                      ":::\n"
                      "::::\n")
          result (pipeline/run "unknown-in-column.md" source)]
      (is (= [{:file "unknown-in-column.md"
               :line 2
               :column 1
               :directive "third-party"
               :message "`third-party`は登録されていないdirectiveです。"}]
             (:diagnostics result)))
      (is (nil? (:output result)))))

  (testing "When a known column container has no closing fence, then transformation fails with its opening position"
    (let [result (pipeline/run
                  "unclosed-column.md"
                  ":::column[コラム]\n本文です。\n")]
      (is (= [{:file "unclosed-column.md"
               :line 1
               :column 1
               :directive "column"
               :message "`column`の終了マーカーがありません。"}]
             (:diagnostics result)))
      (is (nil? (:output result))))))
