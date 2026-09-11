(ns clono.listing-test
  (:require
   ["node:fs" :as fs]
   [cljs.test :refer [deftest is testing]]
   [clono.ast :as ast]
   [clono.markdown :as markdown]
   [clono.pipeline :as pipeline]
   [clono.test-support :as test-support]
   [clono.transform :as transform]
   [clono.transform.listing :as listing-transform]))

(def valid-listing-source
  (str ":::listing[A &amp; &quot;B&quot; &lt;C&gt;]{#greeting}\n"
       "```kotlin\n"
       "fun greet(name: String): String {\n"
       "    return \"Hello, $name!\"\n"
       "}\n"
       "```\n"
       ":::\n\n"
       "```text\n"
       "番号なしコード\n"
       "```\n"))

(def invalid-listing-cases
  [{:case "text directive"
    :source ":listing[キャプション]{#greeting}\n"
    :message "`listing`はContainer directiveとして記述する必要があります。"}
   {:case "leaf directive"
    :source "::listing[キャプション]{#greeting}\n"
    :message "`listing`はContainer directiveとして記述する必要があります。"}
   {:case "missing caption"
    :source ":::listing{#greeting}\n```\ncode\n```\n:::\n"
    :message "`listing`にはプレーンテキストのキャプションが必要です。"}
   {:case "empty caption"
    :source ":::listing[]{#greeting}\n```\ncode\n```\n:::\n"
    :message "`listing`のキャプションには空白ではないプレーンテキストが必要です。"}
   {:case "formatted caption"
    :source ":::listing[**強調**]{#greeting}\n```\ncode\n```\n:::\n"
    :message "`listing`のキャプションには空白ではないプレーンテキストが必要です。"}
   {:case "missing id"
    :source ":::listing[キャプション]\n```\ncode\n```\n:::\n"
    :message "`listing`には`id`属性が必要です。"}
   {:case "invalid id"
    :source ":::listing[キャプション]{#Greeting}\n```\ncode\n```\n:::\n"
    :message "`listing`の`id`属性には英小文字で始まる英小文字、数字、ハイフンだけの値を指定してください。"}
   {:case "unknown attribute"
    :source ":::listing[キャプション]{#greeting class=\"custom\"}\n```\ncode\n```\n:::\n"
    :message "`listing`には`id`以外の属性を指定できません。"}
   {:case "empty body"
    :source ":::listing[キャプション]{#greeting}\n:::\n"
    :message "`listing`の直下には一つのフェンス付きコードブロックだけを記述してください。"}
   {:case "paragraph body"
    :source ":::listing[キャプション]{#greeting}\nコードではありません。\n:::\n"
    :message "`listing`の直下には一つのフェンス付きコードブロックだけを記述してください。"}
   {:case "multiple code blocks"
    :source (str ":::listing[キャプション]{#greeting}\n"
                 "```\none\n```\n\n"
                 "```\ntwo\n```\n"
                 ":::\n")
    :message "`listing`の直下には一つのフェンス付きコードブロックだけを記述してください。"}
   {:case "indented code block"
    :source ":::listing[キャプション]{#greeting}\n\n    code\n\n:::\n"
    :message "`listing`の直下には一つのフェンス付きコードブロックだけを記述してください。"}
   {:case "language with colon"
    :source ":::listing[キャプション]{#greeting}\n```kotlin:Main.kt\nfun main() {}\n```\n:::\n"
    :message "`listing`の言語指定にはコロンを使用できません。"}
   {:case "code fence metadata"
    :source ":::listing[キャプション]{#greeting}\n```kotlin title=Main.kt\nfun main() {}\n```\n:::\n"
    :message "`listing`のコードフェンスにはメタ情報を指定できません。"}])

(defn- transform-context [source-name]
  {:mode :transform
   :source-name source-name})

(defn- build-context [kind]
  {:mode :build
   :source-name "chapter.md"
   :publication-entry (when kind
                        {:type :document
                         :path "chapter.md"
                         :kind kind
                         :include-in-toc true})})

(defn- normalize-line-endings [value]
  (.replace value (js/RegExp. "\\r\\n?" "g") "\n"))

(deftest listing-transformation-test
  (let [result (pipeline/run (transform-context "listing.md")
                             valid-listing-source)
        output (:output result)
        tree (markdown/parse output)
        codes (vec (test-support/nodes-by-type tree "code"))
        numbered-code (first codes)]
    (testing "When a valid listing directive is transformed, then fixed figure boundaries contain the preserved fenced code block"
      (is (:ok? result))
      (is (empty? (:diagnostics result)))
      (is (.includes
           output
           (str "<figure class=\"clono-numbered-listing\" id=\"listing-greeting\">\n"
                "<figcaption class=\"clono-listing-caption\" "
                "id=\"listing-greeting-caption\">"
                "A &amp; &quot;B&quot; &lt;C&gt;</figcaption>")))
      (is (.includes output "</figure>"))
      (is (nil? (test-support/directive tree "listing")))
      (is (= 2 (count codes)))
      (is (= "kotlin" (aget numbered-code "lang")))
      (is (nil? (aget numbered-code "meta")))
      (is (= (str "fun greet(name: String): String {\n"
                  "    return \"Hello, $name!\"\n"
                  "}")
             (.-value numbered-code))))

    (testing "When an ordinary fenced code block follows a numbered listing, then it remains outside the generated figure"
      (is (= ["html" "code" "html" "code"]
             (mapv #(.-type %) (ast/children tree))))))

  (testing "When a listing omits its language, then clono does not add one"
    (let [result
          (pipeline/run
           (transform-context "plain-listing.md")
           ":::listing[プレーンテキスト]{#plain}\n~~~\nplain text\n~~~\n:::\n")
          code (first (test-support/nodes-by-type
                       (markdown/parse (:output result))
                       "code"))]
      (is (:ok? result))
      (is (nil? (.-lang code)))
      (is (= "plain text" (.-value code)))))

  (testing "When directive-like text appears in listing code, then it remains literal code content"
    (let [source (str ":::listing[記法の例]{#syntax-example}\n"
                      "~~~markdown\n"
                      ":::listing[内側]{#inner}\n"
                      ":xref[target]{type=\"listing\" format=\"number\"}\n"
                      "~~~\n"
                      ":::\n")
          result (pipeline/run (transform-context "syntax-example.md") source)
          code (first (test-support/nodes-by-type
                       (markdown/parse (:output result))
                       "code"))]
      (is (:ok? result))
      (is (= (str ":::listing[内側]{#inner}\n"
                  ":xref[target]{type=\"listing\" format=\"number\"}")
             (.-value code))))))

(deftest listing-counter-stylesheet-test
  (testing "When a numbered listing is rendered, then its chapter-scoped listing number is displayed before a caption kept with the code start"
    (let [stylesheet (normalize-line-endings
                      (.readFileSync fs "styles/clono.css" "utf8"))]
      (is (.includes stylesheet
                     "body {\n  counter-reset: figure table listing;\n}\n"))
      (is (.includes stylesheet
                     (str ".clono-numbered-listing {\n"
                          "  counter-increment: listing;\n"
                          "}\n")))
      (is (.includes
           stylesheet
           (str ".clono-numbered-listing > .clono-listing-caption {\n"
                "  break-after: avoid;\n"
                "}\n")))
      (is (.includes
           stylesheet
           (str ".clono-numbered-listing > .clono-listing-caption::before {\n"
                "  content: \"リスト\" counter(chapter) \".\" "
                "counter(listing) \" \";\n"
                "}\n"))))))

(deftest invalid-listing-test
  (testing "When a listing directive violates its local contract, then transformation fails with a positioned diagnostic"
    (doseq [{:keys [case source message]} invalid-listing-cases]
      (let [result (pipeline/run (transform-context "invalid-listing.md") source)
            messages (mapv :message (:diagnostics result))]
        (is (false? (:ok? result)) case)
        (is (nil? (:output result)) case)
        (is (some #{message} messages) case)
        (doseq [problem (:diagnostics result)]
          (is (= "invalid-listing.md" (:file problem)) case)
          (is (= "listing" (:directive problem)) case)
          (is (pos-int? (:line problem)) case)
          (is (pos-int? (:column problem)) case))))))

(deftest listing-reference-target-collection-test
  (testing "When a numbered listing target is collected, then stable listing and caption IDs are returned at its source position"
    (let [context (transform-context "unit-listing.md")
          tree (markdown/parse
                (str "前置きです。\n\n"
                     ":::listing[挨拶を表示する関数]{#greeting}\n"
                     "```kotlin\n"
                     "fun greet() {}\n"
                     "```\n"
                     ":::\n"))]
      (is (= [{:logical-id "greeting"
               :type "listing"
               :target-id "listing-greeting"
               :title-target-id "listing-greeting-caption"
               :numbered? true
               :source-name "unit-listing.md"
               :line 3
               :column 1}]
             (listing-transform/collect-reference-targets
              (test-support/directive tree "listing")
              context)))))

  (testing "When a document contains numbered and ordinary code blocks, then only numbered listing targets are collected in source order"
    (let [context (transform-context "listings.md")
          tree
          (markdown/parse
           (str ":::listing[一つ目のコード]{#first}\n"
                "```kotlin\n"
                "fun first() {}\n"
                "```\n"
                ":::\n\n"
                "```text\n"
                "番号なしコード\n"
                "```\n\n"
                ":::listing[二つ目のコード]{#second}\n"
                "```javascript\n"
                "function second() {}\n"
                "```\n"
                ":::\n"))]
      (is (= [{:logical-id "first"
               :type "listing"
               :target-id "listing-first"
               :title-target-id "listing-first-caption"
               :numbered? true
               :source-name "listings.md"
               :line 1
               :column 1}
              {:logical-id "second"
               :type "listing"
               :target-id "listing-second"
               :title-target-id "listing-second-caption"
               :numbered? true
               :source-name "listings.md"
               :line 11
               :column 1}]
             (transform/collect-reference-targets tree context))))))

(deftest listing-reference-target-collision-test
  (testing "When listing logical IDs are repeated, then the later listing is diagnosed in the shared namespace"
    (let [result
          (pipeline/run
           (transform-context "duplicate-listings.md")
           (str ":::listing[最初のコード]{#same}\n"
                "```kotlin\n"
                "fun first() {}\n"
                "```\n"
                ":::\n\n"
                ":::listing[次のコード]{#same}\n"
                "```kotlin\n"
                "fun second() {}\n"
                "```\n"
                ":::\n"))]
      (is (false? (:ok? result)))
      (is (nil? (:output result)))
      (is (= [{:file "duplicate-listings.md"
               :line 7
               :column 1
               :directive "listing"
               :message "`listing`の論理ID`same`が重複しています。"}]
             (:diagnostics result)))))

  (testing "When a listing repeats an earlier figure logical ID, then the listing is diagnosed in the shared namespace"
    (let [result
          (pipeline/run
           (transform-context "figure-before-listing.md")
           (str ":::figure[構成図]{#architecture}\n"
                "![図](architecture.svg)\n"
                ":::\n\n"
                ":::listing[構成コード]{#architecture}\n"
                "```kotlin\n"
                "fun architecture() {}\n"
                "```\n"
                ":::\n"))]
      (is (= [{:file "figure-before-listing.md"
               :line 5
               :column 1
               :directive "listing"
               :message "`listing`の論理ID`architecture`が重複しています。"}]
             (:diagnostics result)))))

  (testing "When a listing repeats an earlier heading logical ID, then the listing is diagnosed in the shared namespace"
    (let [result
          (pipeline/run
           (transform-context "heading-before-listing.md")
           (str "# 実行例 {#example}\n\n"
                ":::listing[実行例]{#example}\n"
                "```kotlin\n"
                "fun example() {}\n"
                "```\n"
                ":::\n"))]
      (is (= [{:file "heading-before-listing.md"
               :line 3
               :column 1
               :directive "listing"
               :message "`listing`の論理ID`example`が重複しています。"}]
             (:diagnostics result)))))

  (testing "When a listing repeats an earlier table logical ID, then the listing is diagnosed in the shared namespace"
    (let [result
          (pipeline/run
           (transform-context "table-before-listing.md")
           (str ":::table[実行環境]{#runtime}\n"
                "| 項目 |\n"
                "| --- |\n"
                "| Node.js |\n"
                ":::\n\n"
                ":::listing[実行環境の確認]{#runtime}\n"
                "```text\n"
                "node --version\n"
                "```\n"
                ":::\n"))]
      (is (= [{:file "table-before-listing.md"
               :line 7
               :column 1
               :directive "listing"
               :message "`listing`の論理ID`runtime`が重複しています。"}]
             (:diagnostics result)))))

  (testing "When a generated listing HTML ID collides with an earlier heading ID, then the listing is diagnosed in the shared namespace"
    (let [result
          (pipeline/run
           (transform-context "heading-id-before-listing.md")
           (str "# 衝突する見出し {#listing-greeting-caption}\n\n"
                ":::listing[挨拶]{#greeting}\n"
                "```kotlin\n"
                "fun greet() {}\n"
                "```\n"
                ":::\n"))]
      (is (= [{:file "heading-id-before-listing.md"
               :line 3
               :column 1
               :directive "listing"
               :message (str "`listing`から生成するHTML ID`"
                             "listing-greeting-caption`が重複しています。")}]
             (:diagnostics result))))))

(deftest listing-document-validation-test
  (testing "When a listing is nested in a non-directive block, then its top-level placement is diagnosed"
    (let [source (str "> :::listing[引用内のコード]{#nested}\n"
                      "> ```kotlin\n"
                      "> fun main() {}\n"
                      "> ```\n"
                      "> :::\n")
          result (pipeline/run (transform-context "nested-listing.md") source)]
      (is (= ["`listing`はMarkdown文書のトップレベルに記述する必要があります。"]
             (mapv :message (:diagnostics result))))
      (is (nil? (:output result)))))

  (testing "When a listing is inside a known container, then only the outer container contract is reported"
    (let [source (str "::::column[コラム]\n"
                      ":::listing[コラム内のコード]{#nested}\n"
                      "```kotlin\n"
                      "fun main() {}\n"
                      "```\n"
                      ":::\n"
                      "::::\n")
          result (pipeline/run (transform-context "column-listing.md") source)]
      (is (= ["`column`内ではdirectiveを使用できません。"]
             (mapv :message (:diagnostics result))))))

  (testing "When an unknown container replaces listing code, then only the unknown directive is reported"
    (let [source (str "::::listing[コード]{#greeting}\n"
                      ":::third-party\n"
                      "未知の内容です。\n"
                      ":::\n"
                      "::::\n")
          result (pipeline/run (transform-context "unknown-in-listing.md") source)]
      (is (= ["`third-party`は登録されていないdirectiveです。"]
             (mapv :message (:diagnostics result))))))

  (testing "When a known listing container has no closing fence, then its opening position is diagnosed"
    (let [result (pipeline/run
                  (transform-context "unclosed-listing.md")
                  ":::listing[挨拶]{#greeting}\n```kotlin\nfun greet() {}\n```\n")]
      (is (= [{:file "unclosed-listing.md"
               :line 1
               :column 1
               :directive "listing"
               :message "`listing`の終了マーカーがありません。"}]
             (:diagnostics result)))))

  (testing "When a published frontmatter or backmatter document contains a numbered listing, then its document kind is diagnosed"
    (doseq [kind ["frontmatter" "backmatter"]]
      (let [result (pipeline/run
                    (build-context kind)
                    ":::listing[挨拶]{#greeting}\n```kotlin\nfun greet() {}\n```\n:::\n")]
        (is (= ["`listing`は本文または付録の掲載Markdownにだけ記述できます。"]
               (mapv :message (:diagnostics result)))
            kind)))))

(deftest build-listing-validation-test
  (let [source ":::listing[挨拶]{#greeting}\n```kotlin\nfun greet() {}\n```\n:::\n"]
    (testing "When a chapter or appendix contains a numbered listing, then build validation succeeds"
      (doseq [kind ["chapter" "appendix"]]
        (let [result (pipeline/run (build-context kind) source)]
          (is (:ok? result) kind)
          (is (.includes (:output result) "id=\"listing-greeting\"") kind))))

    (testing "When an unlisted Markdown file contains a numbered listing, then it is transformed without requiring a publication kind"
      (let [result (pipeline/run (build-context nil) source)]
        (is (:ok? result))
        (is (empty? (:diagnostics result)))
        (is (.includes (:output result) "id=\"listing-greeting\""))))))
