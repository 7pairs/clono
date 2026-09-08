(ns clono.table-test
  (:require
   ["node:fs" :as fs]
   [cljs.test :refer [deftest is testing]]
   [clono.ast :as ast]
   [clono.markdown :as markdown]
   [clono.pipeline :as pipeline]
   [clono.test-support :as test-support]
   [clono.transform :as transform]))

(def valid-table-source
  (str ":::table[A &amp; &quot;B&quot; &lt;C&gt;]{#runtime}\n"
       "| 左 | 中央 | 右 |\n"
       "| :--- | :---: | ---: |\n"
       "| `ClojureScript` | **Node.js** | [VFM][vfm] |\n"
       "| *推奨* | ~~旧形式~~ | 通常 |\n"
       ":::\n\n"
       "[vfm]: https://vivliostyle.github.io/vfm/\n\n"
       "| 番号なし | 表 |\n"
       "| --- | --- |\n"
       "| A | 1 |\n"))

(def invalid-table-cases
  [{:case "text directive"
    :source ":table[キャプション]{#runtime}\n"
    :message "`table`はContainer directiveとして記述する必要があります。"}
   {:case "leaf directive"
    :source "::table[キャプション]{#runtime}\n"
    :message "`table`はContainer directiveとして記述する必要があります。"}
   {:case "missing caption"
    :source (str ":::table{#runtime}\n"
                 "| 項目 | 値 |\n| --- | --- |\n| A | 1 |\n:::\n")
    :message "`table`にはプレーンテキストのキャプションが必要です。"}
   {:case "empty caption"
    :source (str ":::table[]{#runtime}\n"
                 "| 項目 | 値 |\n| --- | --- |\n| A | 1 |\n:::\n")
    :message "`table`のキャプションには空白ではないプレーンテキストが必要です。"}
   {:case "formatted caption"
    :source (str ":::table[**強調**]{#runtime}\n"
                 "| 項目 | 値 |\n| --- | --- |\n| A | 1 |\n:::\n")
    :message "`table`のキャプションには空白ではないプレーンテキストが必要です。"}
   {:case "missing id"
    :source (str ":::table[キャプション]\n"
                 "| 項目 | 値 |\n| --- | --- |\n| A | 1 |\n:::\n")
    :message "`table`には`id`属性が必要です。"}
   {:case "invalid id"
    :source (str ":::table[キャプション]{#Runtime}\n"
                 "| 項目 | 値 |\n| --- | --- |\n| A | 1 |\n:::\n")
    :message "`table`の`id`属性には英小文字で始まる英小文字、数字、ハイフンだけの値を指定してください。"}
   {:case "unknown attribute"
    :source (str ":::table[キャプション]{#runtime class=\"custom\"}\n"
                 "| 項目 | 値 |\n| --- | --- |\n| A | 1 |\n:::\n")
    :message "`table`には`id`以外の属性を指定できません。"}
   {:case "empty body"
    :source ":::table[キャプション]{#runtime}\n:::\n"
    :message "`table`の直下には一つのGFM形式のMarkdown表だけを記述してください。"}
   {:case "paragraph body"
    :source ":::table[キャプション]{#runtime}\n表ではありません。\n:::\n"
    :message "`table`の直下には一つのGFM形式のMarkdown表だけを記述してください。"}
   {:case "multiple tables"
    :source (str ":::table[キャプション]{#runtime}\n"
                 "| A |\n| --- |\n| 1 |\n\n"
                 "| B |\n| --- |\n| 2 |\n"
                 ":::\n")
    :message "`table`の直下には一つのGFM形式のMarkdown表だけを記述してください。"}
   {:case "image"
    :source (str ":::table[キャプション]{#runtime}\n"
                 "| 項目 |\n| --- |\n| ![画像](image.png) |\n:::\n")
    :message "`table`のセル内では画像を使用できません。"}
   {:case "image reference"
    :source (str ":::table[キャプション]{#runtime}\n"
                 "| 項目 |\n| --- |\n| ![画像][image] |\n:::\n\n"
                 "[image]: image.png\n")
    :message "`table`のセル内では参照形式の画像を使用できません。"}
   {:case "footnote reference"
    :source (str ":::table[キャプション]{#runtime}\n"
                 "| 項目 |\n| --- |\n| 脚注[^note] |\n:::\n\n"
                 "[^note]: 脚注です。\n")
    :message "`table`のセル内では脚注参照を使用できません。"}
   {:case "raw HTML"
    :source (str ":::table[キャプション]{#runtime}\n"
                 "| 項目 |\n| --- |\n| <span>HTML</span> |\n:::\n")
    :message "`table`のセル内ではraw HTMLを使用できません。"}
   {:case "directive"
    :source (str ":::table[キャプション]{#runtime}\n"
                 "| 項目 |\n| --- |\n"
                 "| :xref[target]{type=\"figure\" format=\"number\"} |\n"
                 ":::\n")
    :message "`table`のセル内ではdirectiveを使用できません。"}])

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

(deftest table-transformation-test
  (let [result (pipeline/run (transform-context "table.md")
                             valid-table-source)
        output (:output result)
        tree (markdown/parse output)
        tables (vec (test-support/nodes-by-type tree "table"))
        numbered-table (first tables)]
    (testing "When a valid table directive is transformed, then fixed figure boundaries contain the preserved GFM table"
      (is (:ok? result))
      (is (empty? (:diagnostics result)))
      (is (.includes output
                     "<figure class=\"clono-numbered-table\" id=\"table-runtime\">"))
      (is (.includes output
                     (str "<figcaption class=\"clono-table-caption\" "
                          "id=\"table-runtime-caption\">"
                          "A &amp; &quot;B&quot; &lt;C&gt;</figcaption>")))
      (is (.includes output "</figure>"))
      (is (nil? (test-support/directive tree "table")))
      (is (= 2 (count tables)))
      (is (= ["left" "center" "right"]
             (vec (.-align numbered-table)))))

    (testing "When supported inline Markdown appears in a numbered table, then its semantics remain in the transformed Markdown"
      (is (= 1 (count (test-support/nodes-by-type numbered-table "inlineCode"))))
      (is (= 1 (count (test-support/nodes-by-type numbered-table "strong"))))
      (is (= 1 (count (test-support/nodes-by-type numbered-table "linkReference"))))
      (is (= 1 (count (test-support/nodes-by-type numbered-table "emphasis"))))
      (is (some #(= "~~旧形式~~" (.-value %))
                (test-support/nodes-by-type numbered-table "text"))))

    (testing "When an ordinary GFM table follows a numbered table, then it remains an unwrapped table"
      (is (= ["html" "table" "html" "definition" "table"]
             (mapv #(.-type %) (ast/children tree)))))))

(deftest table-counter-stylesheet-test
  (testing "When a numbered table is rendered, then its chapter-scoped table number is displayed before the caption"
    (let [stylesheet (normalize-line-endings
                      (.readFileSync fs "styles/clono.css" "utf8"))]
      (is (.includes stylesheet
                     "body {\n  counter-reset: figure table;\n}\n"))
      (is (.includes stylesheet
                     (str ".clono-numbered-table {\n"
                          "  counter-increment: table;\n"
                          "}\n")))
      (is (.includes
           stylesheet
           (str ".clono-numbered-table > .clono-table-caption::before {\n"
                "  content: \"表\" counter(chapter) \".\" "
                "counter(table) \" \";\n"
                "}\n"))))))

(deftest invalid-table-test
  (testing "When a table directive violates its local contract, then transformation fails with a positioned diagnostic"
    (doseq [{:keys [case source message]} invalid-table-cases]
      (let [result (pipeline/run (transform-context "invalid-table.md") source)
            messages (mapv :message (:diagnostics result))]
        (is (false? (:ok? result)) case)
        (is (nil? (:output result)) case)
        (is (some #{message} messages) case)
        (doseq [problem (:diagnostics result)]
          (is (= "invalid-table.md" (:file problem)) case)
          (is (= "table" (:directive problem)) case)
          (is (pos-int? (:line problem)) case)
          (is (pos-int? (:column problem)) case))))))

(deftest table-reference-target-collection-test
  (testing "When a document contains numbered and ordinary tables, then only numbered table targets are collected in source order"
    (let [context (transform-context "tables.md")
          tree
          (markdown/parse
           (str ":::table[一つ目の表]{#first}\n"
                "| 項目 |\n"
                "| --- |\n"
                "| A |\n"
                ":::\n\n"
                ":::table[二つ目の表]{#second}\n"
                "| 項目 |\n"
                "| --- |\n"
                "| B |\n"
                ":::\n\n"
                "| 番号なし |\n"
                "| --- |\n"
                "| C |\n"))]
      (is (= [{:logical-id "first"
               :type "table"
               :target-id "table-first"
               :title-target-id "table-first-caption"
               :numbered? true
               :source-name "tables.md"
               :line 1
               :column 1}
              {:logical-id "second"
               :type "table"
               :target-id "table-second"
               :title-target-id "table-second-caption"
               :numbered? true
               :source-name "tables.md"
               :line 7
               :column 1}]
             (transform/collect-reference-targets tree context))))))

(deftest table-reference-target-collision-test
  (testing "When table logical IDs are repeated, then the later table is diagnosed in the shared namespace"
    (let [result
          (pipeline/run
           (transform-context "duplicate-tables.md")
           (str ":::table[最初の表]{#same}\n"
                "| 項目 |\n"
                "| --- |\n"
                "| A |\n"
                ":::\n\n"
                ":::table[次の表]{#same}\n"
                "| 項目 |\n"
                "| --- |\n"
                "| B |\n"
                ":::\n"))]
      (is (false? (:ok? result)))
      (is (nil? (:output result)))
      (is (= [{:file "duplicate-tables.md"
               :line 7
               :column 1
               :directive "table"
               :message "`table`の論理ID`same`が重複しています。"}]
             (:diagnostics result)))))

  (testing "When a table repeats an earlier figure logical ID, then the table is diagnosed in the shared namespace"
    (let [result
          (pipeline/run
           (transform-context "figure-before-table.md")
           (str ":::figure[構成図]{#architecture}\n"
                "![図](architecture.svg)\n"
                ":::\n\n"
                ":::table[構成表]{#architecture}\n"
                "| 項目 |\n"
                "| --- |\n"
                "| A |\n"
                ":::\n"))]
      (is (= [{:file "figure-before-table.md"
               :line 5
               :column 1
               :directive "table"
               :message "`table`の論理ID`architecture`が重複しています。"}]
             (:diagnostics result)))))

  (testing "When a table repeats an earlier heading logical ID, then the table is diagnosed in the shared namespace"
    (let [result
          (pipeline/run
           (transform-context "heading-before-table.md")
           (str "# 実行環境 {#runtime}\n\n"
                ":::table[実行環境]{#runtime}\n"
                "| 項目 |\n"
                "| --- |\n"
                "| Node.js |\n"
                ":::\n"))]
      (is (= [{:file "heading-before-table.md"
               :line 3
               :column 1
               :directive "table"
               :message "`table`の論理ID`runtime`が重複しています。"}]
             (:diagnostics result)))))

  (testing "When a table HTML ID collides with an earlier heading ID, then the table is diagnosed in the shared namespace"
    (let [result
          (pipeline/run
           (transform-context "heading-id-before-table.md")
           (str "# 実行環境 {#table-runtime}\n\n"
                ":::table[実行環境]{#runtime}\n"
                "| 項目 |\n"
                "| --- |\n"
                "| Node.js |\n"
                ":::\n"))]
      (is (= [{:file "heading-id-before-table.md"
               :line 3
               :column 1
               :directive "table"
               :message (str "`table`から生成するHTML ID`table-runtime"
                             "`が重複しています。")}]
             (:diagnostics result))))))

(deftest table-document-validation-test
  (testing "When a table is nested in a non-directive block, then its top-level placement is diagnosed"
    (let [source (str "> :::table[引用内の表]{#nested}\n"
                      "> | 項目 | 値 |\n"
                      "> | --- | --- |\n"
                      "> | A | 1 |\n"
                      "> :::\n")
          result (pipeline/run (transform-context "nested-table.md") source)]
      (is (= ["`table`はMarkdown文書のトップレベルに記述する必要があります。"]
             (mapv :message (:diagnostics result))))
      (is (nil? (:output result)))))

  (testing "When a table is inside a known container, then only the outer container contract is reported"
    (let [source (str "::::column[コラム]\n"
                      ":::table[コラム内の表]{#nested}\n"
                      "| 項目 | 値 |\n"
                      "| --- | --- |\n"
                      "| A | 1 |\n"
                      ":::\n"
                      "::::\n")
          result (pipeline/run (transform-context "column-table.md") source)]
      (is (= ["`column`内ではdirectiveを使用できません。"]
             (mapv :message (:diagnostics result))))))

  (testing "When an unknown directive appears in a table cell, then only the unknown directive is reported"
    (let [source (str ":::table[実行環境]{#runtime}\n"
                      "| 項目 |\n"
                      "| --- |\n"
                      "| :third-party[値] |\n"
                      ":::\n")
          result (pipeline/run (transform-context "unknown-in-table.md") source)]
      (is (= [{:file "unknown-in-table.md"
               :line 4
               :column 3
               :directive "third-party"
               :message "`third-party`は登録されていないdirectiveです。"}]
             (:diagnostics result)))))

  (testing "When unknown and disallowed table cell content coexist, then both independent problems are reported in source order"
    (let [source (str ":::table[実行環境]{#runtime}\n"
                      "| 項目 | 値 |\n"
                      "| --- | --- |\n"
                      "| :third-party[値] | ![画像](image.png) |\n"
                      ":::\n")
          result (pipeline/run (transform-context "multiple-table-problems.md")
                               source)]
      (is (= ["`third-party`は登録されていないdirectiveです。"
              "`table`のセル内では画像を使用できません。"]
             (mapv :message (:diagnostics result))))))

  (testing "When a known table container has no closing fence, then its opening position is diagnosed"
    (let [result (pipeline/run
                  (transform-context "unclosed-table.md")
                  (str ":::table[実行環境]{#runtime}\n"
                       "| 項目 | 値 |\n"
                       "| --- | --- |\n"
                       "| A | 1 |\n"))]
      (is (= [{:file "unclosed-table.md"
               :line 1
               :column 1
               :directive "table"
               :message "`table`の終了マーカーがありません。"}]
             (:diagnostics result)))))

  (testing "When a published frontmatter or backmatter document contains a numbered table, then its document kind is diagnosed"
    (doseq [kind ["frontmatter" "backmatter"]]
      (let [result (pipeline/run
                    (build-context kind)
                    (str ":::table[実行環境]{#runtime}\n"
                         "| 項目 | 値 |\n"
                         "| --- | --- |\n"
                         "| A | 1 |\n"
                         ":::\n"))]
        (is (= ["`table`は本文または付録の掲載Markdownにだけ記述できます。"]
               (mapv :message (:diagnostics result)))
            kind)))))

(deftest build-table-validation-test
  (let [source (str ":::table[実行環境]{#runtime}\n"
                    "| 項目 | 値 |\n"
                    "| --- | --- |\n"
                    "| A | 1 |\n"
                    ":::\n")]
    (testing "When a chapter or appendix contains a numbered table, then build validation succeeds"
      (doseq [kind ["chapter" "appendix"]]
        (let [result (pipeline/run (build-context kind) source)]
          (is (:ok? result) kind)
          (is (.includes (:output result) "id=\"table-runtime\"") kind))))

    (testing "When an unlisted Markdown file contains a numbered table, then it is transformed without requiring a publication kind"
      (let [result (pipeline/run (build-context nil) source)]
        (is (:ok? result))
        (is (empty? (:diagnostics result)))
        (is (.includes (:output result) "id=\"table-runtime\""))))))
