(ns clono.definition-list-test
  (:require
   ["node:fs" :as fs]
   [cljs.test :refer [deftest is testing]]
   [clono.markdown :as markdown]
   [clono.pipeline :as pipeline]
   [clono.test-support :as test-support]))

(def valid-definition-list-source
  (str "::::definition-list\n"
       ":::definition\n"
       "::term[A & `<READY>`]\n\n"
       "処理を開始できる**待機状態**。"
       "[仕様](https://example.com/)と[参照仕様][reference]を参照します。\n"
       ":::\n\n"
       ":::definition\n"
       "::term[DONE]\n\n"
       "終了コードは`0`です。*補足です*。\\\n次の行です。\n"
       ":::\n"
       "::::\n\n"
       "[reference]: https://example.com/reference\n"))

(def valid-definition-body
  (str ":::definition\n"
       "::term[READY]\n\n"
       "説明です。\n"
       ":::\n"))

(defn valid-list-with [body]
  (str "::::definition-list\n" body "::::\n"))

(def invalid-definition-list-cases
  [{:case "text definition list"
    :source ":definition-list[リスト]\n"
    :directive "definition-list"
    :message "`definition-list`はContainer directiveとして記述する必要があります。"}
   {:case "leaf definition list"
    :source "::definition-list\n"
    :directive "definition-list"
    :message "`definition-list`はContainer directiveとして記述する必要があります。"}
   {:case "definition list label"
    :source (str "::::definition-list[リスト]\n"
                 valid-definition-body
                 "::::\n")
    :directive "definition-list"
    :message "`definition-list`にはラベルを指定できません。"}
   {:case "definition list attribute"
    :source (str "::::definition-list{class=\"custom\"}\n"
                 valid-definition-body
                 "::::\n")
    :directive "definition-list"
    :message "`definition-list`には属性を指定できません。"}
   {:case "empty definition list"
    :source ":::definition-list\n:::\n"
    :directive "definition-list"
    :message "`definition-list`には1個以上の`definition`が必要です。"}
   {:case "paragraph in definition list"
    :source ":::definition-list\n説明です。\n:::\n"
    :directive "definition-list"
    :message (str "`definition-list`の直下には`definition`だけを記述できます"
                  "（`paragraph`を検出しました）。")}
   {:case "leaf definition"
    :source (valid-list-with "::definition\n")
    :directive "definition"
    :message "`definition`はContainer directiveとして記述する必要があります。"}
   {:case "definition label"
    :source (valid-list-with
             (str ":::definition[項目]\n"
                  "::term[READY]\n\n"
                  "説明です。\n"
                  ":::\n"))
    :directive "definition"
    :message "`definition`にはラベルを指定できません。"}
   {:case "definition attribute"
    :source (valid-list-with
             (str ":::definition{class=\"custom\"}\n"
                  "::term[READY]\n\n"
                  "説明です。\n"
                  ":::\n"))
    :directive "definition"
    :message "`definition`には属性を指定できません。"}
   {:case "missing term"
    :source (valid-list-with
             ":::definition\n説明です。\n:::\n")
    :directive "definition"
    :message (str "`definition`の直下には、1個の`term`と"
                  "1個の説明段落をこの順序で記述する必要があります。")}
   {:case "multiple descriptions"
    :source (valid-list-with
             (str ":::definition\n"
                  "::term[READY]\n\n"
                  "一つ目の説明です。\n\n"
                  "二つ目の説明です。\n"
                  ":::\n"))
    :directive "definition"
    :message (str "`definition`の直下には、1個の`term`と"
                  "1個の説明段落をこの順序で記述する必要があります。")}
   {:case "text term"
    :source ":term[READY]\n"
    :directive "term"
    :message "`term`はLeaf directiveとして記述する必要があります。"}
   {:case "container term"
    :source ":::term\nREADY\n:::\n"
    :directive "term"
    :message "`term`はLeaf directiveとして記述する必要があります。"}
   {:case "term attribute"
    :source (valid-list-with
             (str ":::definition\n"
                  "::term[READY]{class=\"custom\"}\n\n"
                  "説明です。\n"
                  ":::\n"))
    :directive "term"
    :message "`term`には属性を指定できません。"}
   {:case "empty term"
    :source (valid-list-with
             (str ":::definition\n"
                  "::term[]\n\n"
                  "説明です。\n"
                  ":::\n"))
    :directive "term"
    :message "`term`には空白ではない用語が必要です。"}
   {:case "strong term"
    :source (valid-list-with
             (str ":::definition\n"
                  "::term[**READY**]\n\n"
                  "説明です。\n"
                  ":::\n"))
    :directive "term"
    :message "`term`の用語では`strong`を使用できません。"}
   {:case "linked term"
    :source (valid-list-with
             (str ":::definition\n"
                  "::term[[READY](https://example.com/)]\n\n"
                  "説明です。\n"
                  ":::\n"))
    :directive "term"
    :message "`term`の用語では`link`を使用できません。"}
   {:case "image in description"
    :source (valid-list-with
             (str ":::definition\n"
                  "::term[READY]\n\n"
                  "説明に![画像](image.png)を含みます。\n"
                  ":::\n"))
    :directive "definition"
    :message "`definition`の説明内では画像を使用できません。"}
   {:case "footnote in description"
    :source (str (valid-list-with
                  (str ":::definition\n"
                       "::term[READY]\n\n"
                       "説明に脚注[^note]を含みます。\n"
                       ":::\n"))
                 "\n[^note]: 脚注です。\n")
    :directive "definition"
    :message "`definition`の説明内では脚注参照を使用できません。"}
   {:case "raw HTML in description"
    :source (valid-list-with
             (str ":::definition\n"
                  "::term[READY]\n\n"
                  "説明に<br>HTMLを含みます。\n"
                  ":::\n"))
    :directive "definition"
    :message "`definition`の説明内ではraw HTMLを使用できません。"}
   {:case "orphan definition"
    :source valid-definition-body
    :directive "definition"
    :message "`definition`は`definition-list`の直接の子として記述する必要があります。"}
   {:case "orphan term"
    :source "::term[READY]\n"
    :directive "term"
    :message "`term`は`definition`の直接の子として記述する必要があります。"}
   {:case "definition list in blockquote"
    :source (str "> ::::definition-list\n"
                 "> :::definition\n"
                 "> ::term[READY]\n"
                 ">\n"
                 "> 説明です。\n"
                 "> :::\n"
                 "> :::: \n")
    :directive "definition-list"
    :message "`definition-list`はMarkdown文書のトップレベルに記述する必要があります。"}])

(defn- normalize-line-endings [value]
  (.replace value (js/RegExp. "\\r\\n?" "g") "\n"))

(deftest definition-list-transformation-test
  (doseq [mode [:transform :build]]
    (let [context (cond-> {:mode mode
                           :source-name "definition-list.md"}
                    (= :build mode)
                    (assoc :publication-entry
                           {:type :document
                            :path "definition-list.md"
                            :kind "chapter"
                            :include-in-toc true}))
          result (pipeline/run context valid-definition-list-source)
          output (:output result)
          tree (markdown/parse output)]
      (testing (str "When a valid definition list is transformed in "
                    (name mode)
                    " mode, then semantic wrappers and supported Markdown are returned")
        (is (true? (:ok? result)))
        (is (empty? (:diagnostics result)))
        (is (.includes output "<dl class=\"clono-definition-list\">"))
        (is (.includes output "<div class=\"clono-definition-item\">"))
        (is (.includes output
                       "<dt>A &amp; <code>&lt;READY&gt;</code></dt>"))
        (is (.includes output "<dt>DONE</dt>"))
        (is (= 2 (count (re-seq #"<dd>" output))))
        (is (= 2 (count (re-seq #"</dd>" output))))
        (is (= 2 (count (re-seq #"</div>" output))))
        (is (.includes output "**待機状態**"))
        (is (.includes output "[仕様](https://example.com/)"))
        (is (nil? (test-support/directive tree "definition-list")))
        (is (nil? (test-support/directive tree "definition")))
        (is (nil? (test-support/directive tree "term")))
        (is (= 12 (count (test-support/nodes-by-type tree "html"))))
        (is (= 1 (count (test-support/nodes-by-type tree "strong"))))
        (is (= 1 (count (test-support/nodes-by-type tree "emphasis"))))
        (is (= 1 (count (test-support/nodes-by-type tree "link"))))
        (is (= 1 (count (test-support/nodes-by-type tree "linkReference"))))
        (is (= 1 (count (test-support/nodes-by-type tree "break"))))
        (is (= 1 (count (test-support/nodes-by-type tree "definition"))))
        (is (= 1 (count (test-support/nodes-by-type tree "inlineCode")))))))

  (testing "When the clono stylesheet is used, then definition items avoid page fragmentation"
    (let [stylesheet (normalize-line-endings
                      (.readFileSync fs "styles/clono.css" "utf8"))]
      (is (.includes stylesheet
                     ".clono-definition-item {\n  break-inside: avoid;\n}\n")))))

(deftest invalid-definition-list-test
  (testing "When a definition-list directive violates its contract, then transformation fails with a positioned diagnostic"
    (doseq [{:keys [case source directive message]}
            invalid-definition-list-cases]
      (let [result (pipeline/run {:mode :transform
                                  :source-name "invalid-definition-list.md"}
                                 source)
            problem (first (:diagnostics result))]
        (is (false? (:ok? result)) case)
        (is (nil? (:output result)) case)
        (is (= 1 (count (:diagnostics result)))
            (str case ": " (pr-str (:diagnostics result))))
        (is (= {:file "invalid-definition-list.md"
                :directive directive
                :message message}
               (select-keys problem [:file :directive :message]))
            case)
        (is (pos-int? (:line problem)) case)
        (is (pos-int? (:column problem)) case)))))

(deftest definition-list-diagnostic-integration-test
  (testing "When an unknown directive is inside a definition list, then only the unknown directive is reported"
    (let [source (str "::::definition-list\n"
                      ":::third-party\n"
                      "未知の項目です。\n"
                      ":::\n"
                      "::::\n")
          result (pipeline/run {:mode :transform
                                :source-name "unknown-in-definition-list.md"}
                               source)]
      (is (= [{:file "unknown-in-definition-list.md"
               :line 2
               :column 1
               :directive "third-party"
               :message "`third-party`は登録されていないdirectiveです。"}]
             (:diagnostics result)))
      (is (nil? (:output result)))))

  (testing "When an unknown directive is inside a definition, then only the unknown directive is reported"
    (let [source (str "::::definition-list\n"
                      ":::definition\n"
                      "::term[READY]\n\n"
                      "説明です。\n\n"
                      "::third-party\n"
                      ":::\n"
                      "::::\n")
          result (pipeline/run {:mode :transform
                                :source-name "unknown-in-definition.md"}
                               source)]
      (is (= [{:file "unknown-in-definition.md"
               :line 7
               :column 1
               :directive "third-party"
               :message "`third-party`は登録されていないdirectiveです。"}]
             (:diagnostics result)))
      (is (nil? (:output result)))))

  (testing "When a definition list has no closing fence, then transformation fails with its opening position"
    (let [result (pipeline/run
                  {:mode :transform
                   :source-name "unclosed-definition-list.md"}
                  (str "::::definition-list\n"
                       valid-definition-body))]
      (is (= [{:file "unclosed-definition-list.md"
               :line 1
               :column 1
               :directive "definition-list"
               :message "`definition-list`の終了マーカーがありません。"}]
             (:diagnostics result)))
      (is (nil? (:output result))))))
