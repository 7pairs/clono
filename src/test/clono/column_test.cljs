(ns clono.column-test
  (:require
   ["node:fs" :as fs]
   [cljs.test :refer [deftest is testing]]
   [clono.markdown :as markdown]
   [clono.pipeline :as pipeline]
   [clono.test-support :as test-support]
   [clono.transform :as transform]
   [clono.transform.column :as column]))

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

(deftest column-normalized-data-test
  (testing "When a validated column is normalized, then its renderer receives only the unescaped title and Markdown body"
    (let [tree (markdown/parse valid-column-source)
          input (column/normalized-data
                 (test-support/directive tree "column"))
          body (.-body input)]
      (is (= ["body" "title"]
             (sort (array-seq (js/Object.keys input)))))
      (is (true? (js/Object.isFrozen input)))
      (is (= "A & B <unsafe>" (.-title input)))
      (is (.includes body "**太字**"))
      (is (.includes body "[リンク][site]"))
      (is (.includes body "[^note]"))
      (is (not (.includes body ":::column")))
      (is (not (.includes body "[site]:")))
      (is (not (.includes body "[^note]:")))))

  (testing "When a column contains an index term, then its renderer receives the transformed marker in the body"
    (let [source (str ":::column[雑談]\n"
                      "これは:index[麻雀]{reading=\"まーじゃん\"}の話です。\n"
                      ":::\n")
          source-name "column-index.md"
          tree (markdown/parse source)
          context {:mode :transform :source-name source-name}
          entries (transform/collect-index-entries tree context)
          prepared (transform/prepare-index-entries entries [])
          column-node (test-support/directive tree "column")]
      (is (true? (:ok? prepared)))
      (transform/transform-children!
       column-node
       (transform/add-index-entries context (:entries prepared)))
      (let [body (.-body (column/normalized-data column-node))]
        (is (.includes body "<span class=\"clono-index-marker\""))
        (is (.includes body "麻雀</span>"))
        (is (not (.includes body ":index[")))))))

(deftest column-default-renderer-test
  (testing "When the default renderer receives a column, then it returns the existing wrapper and preserves body Markdown"
    (let [body "本文には**強調**がある。\n\n- 箇条書き"
          output (column/default-renderer
                  #js {:title "ちょっと休憩" :body body})
          tree (markdown/parse output)]
      (is (= (str "<aside class=\"clono-column\">\n\n"
                  "<p class=\"clono-column-title\">ちょっと休憩</p>\n\n"
                  body
                  "\n\n</aside>")
             output))
      (is (= 1 (count (test-support/nodes-by-type tree "strong"))))
      (is (= 1 (count (test-support/nodes-by-type tree "list"))))))

  (testing "When the column title contains HTML syntax, then the default renderer escapes it as text"
    (let [output (column/default-renderer
                  #js {:title "A & B <unsafe>" :body "本文。"})]
      (is (.includes output
                     "<p class=\"clono-column-title\">A &amp; B &lt;unsafe&gt;</p>"))
      (is (not (.includes output "<unsafe>"))))))

(deftest registered-column-renderer-test
  (testing "When a column renderer is registered, then its Markdown replaces the default column output"
    (let [inputs (atom [])
          renderer (fn [input]
                     (swap! inputs conj input)
                     (str "<div class=\"custom-column\">\n\n"
                          (.-body input)
                          "\n\n</div>"))
          source ":::column[雑談]\n**本文**です。\n:::\n"
          result (pipeline/run
                  {:mode :transform
                   :source-name "custom-column.md"
                   :registry {"column" {:plugin {:name "custom-column"}
                                         :renderer renderer}}}
                  source)
          output (:output result)
          tree (markdown/parse output)]
      (is (true? (:ok? result)))
      (is (= [] (:diagnostics result)))
      (is (= 1 (count @inputs)))
      (is (= "雑談" (.-title (first @inputs))))
      (is (true? (js/Object.isFrozen (first @inputs))))
      (is (.includes output "<div class=\"custom-column\">"))
      (is (not (.includes output "clono-column")))
      (is (= 1 (count (test-support/nodes-by-type tree "strong")))))))

(deftest invalid-column-renderer-output-test
  (testing "When a column renderer returns a non-string, blank string, or thenable, then a positioned diagnostic replaces all output"
    (let [source (str "先の段落。\n\n"
                      ":::column[最初]\n本文。\n:::\n\n"
                      ":::column[次]\n本文。\n:::\n")
          thenable-calls (atom 0)
          cases [{:case "number" :value 42 :reason "空白ではない文字列"}
                 {:case "nil" :value nil :reason "空白ではない文字列"}
                 {:case "empty" :value "" :reason "空白ではない文字列"}
                 {:case "whitespace" :value " \n\t" :reason "空白ではない文字列"}
                 {:case "promise" :value (js/Promise.resolve "本文。")
                  :reason "非同期結果"}
                 {:case "thenable"
                  :value #js {:then (fn [_resolve]
                                      (swap! thenable-calls inc))}
                  :reason "非同期結果"}]]
      (doseq [{:keys [case value reason]} cases]
        (let [invocations (atom 0)
              renderer (fn [_input]
                         (swap! invocations inc)
                         value)
              result (pipeline/run
                      {:mode :transform
                       :source-name "invalid-renderer.md"
                       :registry {"column" {:plugin {:name "custom-column"}
                                             :renderer renderer}}}
                      source)
              problem (first (:diagnostics result))]
          (is (false? (:ok? result)) case)
          (is (nil? (:output result)) case)
          (is (= 1 (count (:diagnostics result))) case)
          (is (= {:file "invalid-renderer.md"
                  :line 3
                  :column 1
                  :directive "column"}
                 (select-keys problem [:file :line :column :directive]))
              case)
          (is (.includes (:message problem) "custom-column") case)
          (is (.includes (:message problem) reason) case)
          (is (= 1 @invocations) case)))
      (is (zero? @thenable-calls)))))

(deftest later-column-renderer-failure-test
  (testing "When a later column renderer returns a blank value, then earlier rendered columns are not returned as partial output"
    (let [calls (atom 0)
          renderer (fn [_input]
                     (if (= 1 (swap! calls inc))
                       "<aside>最初のコラム</aside>"
                       ""))
          source (str ":::column[最初]\n本文。\n:::\n\n"
                      ":::column[次]\n本文。\n:::\n")
          result (pipeline/run
                  {:mode :transform
                   :source-name "later-column.md"
                   :registry {"column" {:plugin {:name "custom-column"}
                                         :renderer renderer}}}
                  source)]
      (is (false? (:ok? result)))
      (is (nil? (:output result)))
      (is (= 2 @calls))
      (is (= {:file "later-column.md"
              :line 5
              :column 1
              :directive "column"}
             (select-keys (first (:diagnostics result))
                          [:file :line :column :directive]))))))

(deftest column-renderer-exception-diagnostic-test
  (testing "When a custom renderer throws, then its first failure is reported at the column without exposing a stack trace or partial output"
    (let [calls (atom 0)
          renderer (fn [_input]
                     (swap! calls inc)
                     (throw (js/Error. "装飾に失敗\nat internal stack")))
          source (str "前の段落。\n\n"
                      ":::column[最初]\n本文。\n:::\n\n"
                      ":::column[次]\n本文。\n:::\n")
          result (pipeline/run
                  {:mode :transform
                   :source-name "renderer-error.md"
                   :registry {"column" {:plugin {:name "custom-column"}
                                         :renderer renderer}}}
                  source)]
      (is (false? (:ok? result)))
      (is (nil? (:output result)))
      (is (= 1 @calls))
      (is (= [{:file "renderer-error.md"
               :line 3
               :column 1
               :directive "column"
               :message (str "コラムrenderer（custom-column）の実行に失敗しました: "
                             "装飾に失敗")}]
             (:diagnostics result)))))

  (testing "When the built-in renderer throws, then the diagnostic identifies it as built-in"
    (with-redefs [column/default-renderer
                  (fn [_input] (throw (js/Error. "既定出力に失敗")))]
      (let [result (pipeline/run
                    {:mode :transform :source-name "default-error.md"}
                    ":::column[雑談]\n本文。\n:::\n")]
        (is (false? (:ok? result)))
        (is (nil? (:output result)))
        (is (= [{:file "default-error.md"
                 :line 1
                 :column 1
                 :directive "column"
                 :message (str "コラムrenderer（組み込みの既定renderer）"
                               "の実行に失敗しました: 既定出力に失敗")}]
               (:diagnostics result))))))

  (testing "When a returned then property throws, then its error is diagnosed without awaiting the value"
    (let [value (js/Object.defineProperty
                 #js {} "then"
                 #js {:get (fn [] (throw (js/Error. "thenを読めません")))})
          result (pipeline/run
                  {:mode :transform
                   :source-name "then-error.md"
                   :registry {"column" {:plugin {:name "custom-column"}
                                         :renderer (fn [_input] value)}}}
                  ":::column[雑談]\n本文。\n:::\n")]
      (is (false? (:ok? result)))
      (is (nil? (:output result)))
      (is (= [{:file "then-error.md"
               :line 1
               :column 1
               :directive "column"
               :message (str "コラムrenderer（custom-column）の戻り値を確認できません: "
                             "thenを読めません")}]
             (:diagnostics result))))))

(deftest column-existing-behavior-regression-test
  (testing "When a column is transformed without an external renderer, then its Markdown output remains unchanged"
    (let [source (str "前の段落。\n\n"
                      ":::column[A &amp; B]\n"
                      "**重要**な本文。\n"
                      ":::\n\n"
                      "後の段落。\n")
          expected (str "前の段落。\n\n"
                        "<aside class=\"clono-column\">\n\n"
                        "<p class=\"clono-column-title\">A &amp; B</p>\n\n"
                        "**重要**な本文。\n\n"
                        "</aside>\n\n"
                        "後の段落。\n")]
      (doseq [context [{:mode :transform :source-name "column.md"}
                       {:mode :build :source-name "column.md"
                        :registry {}}]]
        (let [result (pipeline/run context source)]
          (is (true? (:ok? result)))
          (is (= [] (:diagnostics result)))
          (is (= expected (:output result)))))))

  (testing "When a column is invalid, then its positioned diagnostic and absent output remain unchanged"
    (let [source ":::column\n本文。\n:::\n"
          expected [{:file "invalid-column.md"
                     :line 1
                     :column 1
                     :directive "column"
                     :message "`column`にはプレーンテキストのタイトルが必要です。"}]]
      (doseq [context [{:mode :transform :source-name "invalid-column.md"}
                       {:mode :build :source-name "invalid-column.md"
                        :registry {}}]]
        (let [result (pipeline/run context source)]
          (is (false? (:ok? result)))
          (is (nil? (:output result)))
          (is (= expected (:diagnostics result))))))))

(deftest column-transformation-test
  (let [result (pipeline/run {:mode :transform :source-name "column.md"}
                             valid-column-source)
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
      (let [result (pipeline/run {:mode :transform
                                  :source-name "invalid-column.md"}
                                 source)
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
          result (pipeline/run {:mode :transform
                                :source-name "unknown-in-column.md"}
                               source)]
      (is (= [{:file "unknown-in-column.md"
               :line 2
               :column 1
               :directive "third-party"
               :message "`third-party`は登録されていないdirectiveです。"}]
             (:diagnostics result)))
      (is (nil? (:output result)))))

  (testing "When a known column container has no closing fence, then transformation fails with its opening position"
    (let [result (pipeline/run
                  {:mode :transform
                   :source-name "unclosed-column.md"}
                  ":::column[コラム]\n本文です。\n")]
      (is (= [{:file "unclosed-column.md"
               :line 1
               :column 1
               :directive "column"
               :message "`column`の終了マーカーがありません。"}]
             (:diagnostics result)))
      (is (nil? (:output result))))))
