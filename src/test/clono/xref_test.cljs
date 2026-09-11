(ns clono.xref-test
  (:require
   ["node:fs" :as fs]
   [cljs.test :refer [deftest is testing]]
   [clono.markdown :as markdown]
   [clono.pipeline :as pipeline]
   [clono.test-support :as test-support]))

(defn- transform-context [source-name]
  {:mode :transform
   :source-name source-name})

(defn- normalize-line-endings [value]
  (.replace value (js/RegExp. "\\r\\n?" "g") "\n"))

(def figure-source
  (str ":::figure[全体構成]{#architecture}\n"
       "![入力から出力までの構成図](architecture.svg)\n"
       ":::\n"))

(def table-source
  (str ":::table[実行環境]{#runtime}\n"
       "| 項目 | 値 |\n"
       "| --- | --- |\n"
       "| Node.js | 24 |\n"
       ":::\n"))

(def listing-source
  (str ":::listing[挨拶を表示する関数]{#greeting}\n"
       "```kotlin\n"
       "fun greet() {}\n"
       "```\n"
       ":::\n"))

(deftest local-xref-transformation-test
  (testing "When local figure references use every format before and after their target, then each reference is resolved to the expected link structure"
    (let [source (str ":xref[architecture]{type=\"figure\" format=\"number\"}\n\n"
                      ":xref[architecture]{type=\"figure\" format=\"number-title\"}\n\n"
                      figure-source
                      "\n:xref[architecture]{type=\"figure\" format=\"title\"}\n")
          result (pipeline/run (transform-context "local-xref.md") source)
          output (:output result)
          tree (markdown/parse output)]
      (is (:ok? result))
      (is (empty? (:diagnostics result)))
      (is (.includes
           output
           (str "<a class=\"clono-xref clono-xref-figure clono-xref-number\" "
                "href=\"#figure-architecture\"></a>")))
      (is (.includes
           output
           (str "<a class=\"clono-xref clono-xref-figure clono-xref-number-title\" "
                "href=\"#figure-architecture\" "
                "data-title-href=\"#figure-architecture-caption\"></a>")))
      (is (.includes
           output
           (str "<a class=\"clono-xref clono-xref-figure clono-xref-title\" "
                "href=\"#figure-architecture\" "
                "data-title-href=\"#figure-architecture-caption\"></a>")))
      (is (nil? (test-support/directive tree "xref"))))))

(deftest invalid-xref-test
  (testing "When an xref directive violates its local contract, then transformation fails with a positioned diagnostic"
    (doseq [{:keys [case source message]}
            [{:case "leaf directive"
              :source (str "::xref[architecture]"
                           "{type=\"figure\" format=\"number\"}\n")
              :message "`xref`はText directiveとして記述する必要があります。"}
             {:case "container directive"
              :source (str ":::xref[architecture]"
                           "{type=\"figure\" format=\"number\"}\n"
                           ":::\n")
              :message "`xref`はText directiveとして記述する必要があります。"}
             {:case "empty label"
              :source ":xref[]{type=\"figure\" format=\"number\"}\n"
              :message "`xref`のラベルには有効な参照先の論理IDが必要です。"}
             {:case "formatted label"
              :source ":xref[**architecture**]{type=\"figure\" format=\"number\"}\n"
              :message "`xref`のラベルには有効な参照先の論理IDが必要です。"}
             {:case "invalid logical ID"
              :source ":xref[Architecture]{type=\"figure\" format=\"number\"}\n"
              :message "`xref`のラベルには有効な参照先の論理IDが必要です。"}
             {:case "missing type"
              :source ":xref[architecture]{format=\"number\"}\n"
              :message "`xref`には`type`属性が必要です。"}
             {:case "unsupported type"
              :source ":xref[architecture]{type=\"code\" format=\"number\"}\n"
              :message (str "`xref`の`type`属性には`figure`、`heading`、"
                            "`table`または`listing`を指定してください。")}
             {:case "missing format"
              :source ":xref[architecture]{type=\"figure\"}\n"
              :message "`xref`には`format`属性が必要です。"}
             {:case "unsupported format"
              :source ":xref[architecture]{type=\"figure\" format=\"page\"}\n"
              :message (str "`xref`の`format`属性には`number`、"
                            "`number-title`または`title`を指定してください。")}
             {:case "unknown attribute"
              :source (str ":xref[architecture]"
                           "{type=\"figure\" format=\"number\" class=\"custom\"}\n")
              :message "`xref`には`type`と`format`以外の属性を指定できません。"}
             {:case "malformed attributes"
              :source (str ":xref[architecture]"
                           "{type=\"figure\" format=\"number\"\n")
              :message "`xref`の属性を解析できません。"}]]
      (let [result (pipeline/run (transform-context "invalid-xref.md") source)
            messages (mapv :message (:diagnostics result))]
        (is (false? (:ok? result)) case)
        (is (nil? (:output result)) case)
        (is (some #{message} messages) case)
        (doseq [problem (:diagnostics result)]
          (is (= "invalid-xref.md" (:file problem)) case)
          (is (= "xref" (:directive problem)) case)
          (is (pos-int? (:line problem)) case)
          (is (pos-int? (:column problem)) case))))))

(deftest heading-xref-type-validation-test
  (testing "When heading references use a supported format, then analysis accepts their reference type"
    (doseq [format ["number" "number-title" "title"]]
      (let [result
            (pipeline/analyze
             (transform-context "heading-xref.md")
             (str ":xref[introduction]{type=\"heading\" format=\""
                  format
                  "\"}\n"))]
        (is (:ok? result) format)
        (is (empty? (:diagnostics result)) format)))))

(deftest table-xref-type-validation-test
  (testing "When table references use a supported format, then analysis accepts their reference type"
    (doseq [format ["number" "number-title" "title"]]
      (let [result
            (pipeline/analyze
             (transform-context "table-xref.md")
             (str ":xref[runtime]{type=\"table\" format=\""
                  format
                  "\"}\n"))]
        (is (:ok? result) format)
        (is (empty? (:diagnostics result)) format)))))

(deftest listing-xref-type-validation-test
  (testing "When listing references use a supported format, then analysis accepts their reference type"
    (doseq [format ["number" "number-title" "title"]]
      (let [result
            (pipeline/analyze
             (transform-context "listing-xref.md")
             (str ":xref[greeting]{type=\"listing\" format=\""
                  format
                  "\"}\n"))]
        (is (:ok? result) format)
        (is (empty? (:diagnostics result)) format)))))

(deftest local-table-xref-transformation-test
  (testing "When local table references use every format before and after their target, then each reference is resolved to the expected link structure"
    (let [source
          (str ":xref[runtime]{type=\"table\" format=\"number\"}\n\n"
               ":xref[runtime]{type=\"table\" format=\"number-title\"}\n\n"
               table-source
               "\n:xref[runtime]{type=\"table\" format=\"title\"}\n")
          result (pipeline/run (transform-context "table-xref.md") source)
          output (:output result)
          tree (markdown/parse output)]
      (is (:ok? result))
      (is (empty? (:diagnostics result)))
      (is (.includes
           output
           (str "<a class=\"clono-xref clono-xref-table clono-xref-number\" "
                "href=\"#table-runtime\"></a>")))
      (is (.includes
           output
           (str "<a class=\"clono-xref clono-xref-table "
                "clono-xref-number-title\" href=\"#table-runtime\" "
                "data-title-href=\"#table-runtime-caption\"></a>")))
      (is (.includes
           output
           (str "<a class=\"clono-xref clono-xref-table clono-xref-title\" "
                "href=\"#table-runtime\" "
                "data-title-href=\"#table-runtime-caption\"></a>")))
      (is (nil? (test-support/directive tree "xref"))))))

(deftest local-listing-xref-transformation-test
  (testing "When local listing references use every format before and after their target, then each reference is resolved to the expected link structure"
    (let [source
          (str ":xref[greeting]{type=\"listing\" format=\"number\"}\n\n"
               ":xref[greeting]{type=\"listing\" format=\"number-title\"}\n\n"
               listing-source
               "\n:xref[greeting]{type=\"listing\" format=\"title\"}\n")
          result (pipeline/run (transform-context "listing-xref.md") source)
          output (:output result)
          tree (markdown/parse output)]
      (is (:ok? result))
      (is (empty? (:diagnostics result)))
      (is (.includes
           output
           (str "<a class=\"clono-xref clono-xref-listing "
                "clono-xref-number\" href=\"#listing-greeting\"></a>")))
      (is (.includes
           output
           (str "<a class=\"clono-xref clono-xref-listing "
                "clono-xref-number-title\" href=\"#listing-greeting\" "
                "data-title-href=\"#listing-greeting-caption\"></a>")))
      (is (.includes
           output
           (str "<a class=\"clono-xref clono-xref-listing "
                "clono-xref-title\" href=\"#listing-greeting\" "
                "data-title-href=\"#listing-greeting-caption\"></a>")))
      (is (nil? (test-support/directive tree "xref"))))))

(deftest table-xref-failure-test
  (testing "When table and figure references name targets of the other type, then both type mismatches are diagnosed without output"
    (let [source
          (str figure-source
               "\n"
               table-source
               "\n:xref[architecture]{type=\"table\" format=\"number\"}\n\n"
               ":xref[runtime]{type=\"figure\" format=\"title\"}\n")
          result (pipeline/run (transform-context "mismatched-table-xref.md")
                               source)]
      (is (false? (:ok? result)))
      (is (nil? (:output result)))
      (is (= [{:file "mismatched-table-xref.md"
               :line 11
               :column 1
               :directive "xref"
               :message "`xref`の参照種別が参照先と一致しません。"}
              {:file "mismatched-table-xref.md"
               :line 13
               :column 1
               :directive "xref"
               :message "`xref`の参照種別が参照先と一致しません。"}]
             (:diagnostics result)))))

  (testing "When build cannot find a table target, then the unresolved reference is diagnosed instead of becoming a placeholder"
    (let [result
          (pipeline/run
           {:mode :build
            :source-name "chapter.md"
            :publication-entry {:type :document
                                :path "chapter.md"
                                :kind "chapter"
                                :include-in-toc true}}
           ":xref[missing-table]{type=\"table\" format=\"number-title\"}\n")]
      (is (false? (:ok? result)))
      (is (nil? (:output result)))
      (is (= [{:file "chapter.md"
               :line 1
               :column 1
               :directive "xref"
               :message "`xref`の参照先`missing-table`を解決できません。"}]
             (:diagnostics result)))))

  (testing "When unlisted Markdown contains a table reference, then build rejects it even when the target is local"
    (let [result
          (pipeline/run
           {:mode :build
            :source-name "notes.md"}
           (str table-source
                "\n:xref[runtime]{type=\"table\" format=\"title\"}\n"))]
      (is (false? (:ok? result)))
      (is (nil? (:output result)))
      (is (= [{:file "notes.md"
               :line 7
               :column 1
               :directive "xref"
               :message (str "`publication`に掲載されていないMarkdownでは"
                             "`xref`を使用できません。")}]
             (:diagnostics result))))))

(deftest local-heading-xref-transformation-test
  (testing "When local heading references use every format before and after their targets, then each reference is resolved with heading metadata"
    (let [source
          (str ":xref[introduction]{type=\"heading\" format=\"number\"}\n\n"
               "# はじめに {#introduction}\n\n"
               ":xref[structure]{type=\"heading\" format=\"number-title\"}\n\n"
               "## 全体構造 {#structure}\n\n"
               "### 変換処理 {#transformation}\n\n"
               ":xref[transformation]{type=\"heading\" format=\"title\"}\n")
          result (pipeline/run (transform-context "heading-xref.md") source)
          output (:output result)
          tree (markdown/parse output)]
      (is (:ok? result))
      (is (empty? (:diagnostics result)))
      (is (.includes
           output
           (str "<a class=\"clono-xref clono-xref-heading "
                "clono-xref-heading-h1 clono-xref-heading-chapter "
                "clono-xref-number\" href=\"#introduction\"></a>")))
      (is (.includes
           output
           (str "<a class=\"clono-xref clono-xref-heading "
                "clono-xref-heading-h2 clono-xref-heading-chapter "
                "clono-xref-number-title\" href=\"#structure\" "
                "data-title-href=\"#structure\"></a>")))
      (is (.includes
           output
           (str "<a class=\"clono-xref clono-xref-heading "
                "clono-xref-heading-h3 clono-xref-heading-chapter "
                "clono-xref-title\" href=\"#transformation\" "
                "data-title-href=\"#transformation\"></a>")))
      (is (nil? (test-support/directive tree "xref"))))))

(deftest build-heading-xref-class-test
  (testing "When build resolves H1 through H3 in each document kind, then allowed level and document classes are generated"
    (doseq [{:keys [kind document-class numbered?]}
            [{:kind "chapter"
              :document-class "clono-xref-heading-chapter"
              :numbered? true}
             {:kind "appendix"
              :document-class "clono-xref-heading-appendix"
              :numbered? true}
             {:kind "frontmatter"
              :document-class "clono-xref-heading-unnumbered"
              :numbered? false}
             {:kind "backmatter"
              :document-class "clono-xref-heading-unnumbered"
              :numbered? false}]
            depth [1 2 3]]
      (let [format (if numbered? "number-title" "title")
            source-name (str kind "-h" depth ".md")
            source (str (apply str (repeat depth "#"))
                        " 対象 {#target}\n\n"
                        ":xref[target]{type=\"heading\" format=\""
                        format
                        "\"}\n")
            result
            (pipeline/run
             {:mode :build
              :source-name source-name
              :publication-entry {:type :document
                                  :path source-name
                                  :kind kind
                                  :include-in-toc true}}
             source)
            expected-classes
            (str "clono-xref clono-xref-heading "
                 "clono-xref-heading-h" depth " "
                 document-class " clono-xref-" format)]
        (is (:ok? result) (str kind " h" depth))
        (is (empty? (:diagnostics result)) (str kind " h" depth))
        (is (.includes (:output result)
                       (str "<a class=\"" expected-classes "\""))
            (str kind " h" depth))))))

(deftest unresolved-local-heading-xref-test
  (testing "When transform cannot find a local heading target, then each format becomes its fixed heading placeholder"
    (doseq [[format expected-text]
            [["number" "見出し番号未解決"]
             ["number-title" "見出し参照先未解決"]
             ["title" "参照先未解決"]]]
      (let [source (str ":xref[external-heading]"
                        "{type=\"heading\" format=\"" format "\"}\n")
            result (pipeline/run
                    (transform-context "unresolved-heading-xref.md")
                    source)
            output (:output result)
            expected (str "<span class=\"clono-xref clono-xref-heading "
                          "clono-xref-" format " clono-xref-placeholder\">"
                          expected-text
                          "</span>")]
        (is (:ok? result) format)
        (is (empty? (:diagnostics result)) format)
        (is (.includes output expected) format)
        (is (not (.includes output "external-heading")) format)
        (is (not (.includes output "href=")) format)
        (is (not (.includes output "clono-xref-heading-h")) format)
        (is (not (.includes output "clono-xref-heading-chapter")) format)
        (is (not (.includes output "clono-xref-heading-unnumbered"))
            format)
        (is (nil? (test-support/directive (markdown/parse output) "xref"))
            format)))))

(deftest unresolved-local-table-xref-test
  (testing "When transform cannot find a local table target, then each format becomes its fixed table placeholder"
    (doseq [[format expected-text]
            [["number" "表X.X"]
             ["number-title" "表X.X 参照先未解決"]
             ["title" "参照先未解決"]]]
      (let [source (str ":xref[external-table]"
                        "{type=\"table\" format=\"" format "\"}\n")
            result (pipeline/run
                    (transform-context "unresolved-table-xref.md")
                    source)
            output (:output result)
            expected (str "<span class=\"clono-xref clono-xref-table "
                          "clono-xref-" format " clono-xref-placeholder\">"
                          expected-text
                          "</span>")]
        (is (:ok? result) format)
        (is (empty? (:diagnostics result)) format)
        (is (.includes output expected) format)
        (is (not (.includes output "external-table")) format)
        (is (not (.includes output "href=")) format)
        (is (nil? (test-support/directive (markdown/parse output) "xref"))
            format)))))

(deftest unresolved-local-xref-test
  (testing "When transform cannot find a local xref target, then each format becomes a fixed placeholder without link attributes or author input"
    (doseq [[format expected-text]
            [["number" "図X.X"]
             ["number-title" "図X.X 参照先未解決"]
             ["title" "参照先未解決"]]]
      (let [source (str ":xref[external-figure]"
                        "{type=\"figure\" format=\"" format "\"}\n")
            result (pipeline/run
                    (transform-context "unresolved-xref.md")
                    source)
            output (:output result)
            expected (str "<span class=\"clono-xref clono-xref-figure "
                          "clono-xref-" format " clono-xref-placeholder\">"
                          expected-text
                          "</span>")]
        (is (:ok? result) format)
        (is (empty? (:diagnostics result)) format)
        (is (.includes output expected) format)
        (is (not (.includes output "external-figure")) format)
        (is (not (.includes output "href=")) format)
        (is (nil? (test-support/directive (markdown/parse output) "xref"))
            format))))

  (testing "When build cannot find an xref target, then it reports a diagnostic instead of generating a placeholder"
    (let [result
          (pipeline/run
           {:mode :build
            :source-name "chapter.md"
            :publication-entry {:type :document
                                :path "chapter.md"
                                :kind "chapter"
                                :include-in-toc true}}
           ":xref[missing-figure]{type=\"figure\" format=\"number\"}\n")]
      (is (false? (:ok? result)))
      (is (nil? (:output result)))
      (is (= [{:file "chapter.md"
               :line 1
               :column 1
               :directive "xref"
               :message "`xref`の参照先`missing-figure`を解決できません。"}]
             (:diagnostics result))))))

(deftest xref-stylesheet-test
  (testing "When the clono stylesheet is inspected, then resolved figure references receive number and title content without targeting placeholder spans"
    (let [stylesheet (normalize-line-endings
                      (.readFileSync fs "styles/clono.css" "utf8"))]
      (is (.includes
           stylesheet
           (str "a.clono-xref-figure.clono-xref-number::before,\n"
                "a.clono-xref-figure.clono-xref-number-title::before {\n"
                "  content: \"図\" target-counter(attr(href url), chapter) \".\" "
                "target-counter(attr(href url), figure);\n"
                "}\n")))
      (is (.includes
           stylesheet
           (str "a.clono-xref-figure.clono-xref-number-title::after {\n"
                "  content: \" \" target-text(attr(data-title-href url), content);\n"
                "}\n")))
      (is (.includes
           stylesheet
           (str "a.clono-xref-figure.clono-xref-title::before {\n"
                "  content: target-text(attr(data-title-href url), content);\n"
                "}\n")))
      (is (not (.includes stylesheet ".clono-xref-placeholder::"))))))

(deftest table-xref-stylesheet-test
  (testing "When a resolved table reference requests its number, then the target chapter and table counters are displayed"
    (let [stylesheet (normalize-line-endings
                      (.readFileSync fs "styles/clono.css" "utf8"))]
      (is (.includes
           stylesheet
           (str "a.clono-xref-table.clono-xref-number::before,\n"
                "a.clono-xref-table.clono-xref-number-title::before {\n"
                "  content: \"表\" "
                "target-counter(attr(href url), chapter) \".\" "
                "target-counter(attr(href url), table);\n"
                "}\n")))))

  (testing "When a resolved table reference requests its title, then the target caption text is displayed"
    (let [stylesheet (normalize-line-endings
                      (.readFileSync fs "styles/clono.css" "utf8"))]
      (is (.includes
           stylesheet
           (str "a.clono-xref-table.clono-xref-number-title::after {\n"
                "  content: \" \" "
                "target-text(attr(data-title-href url), content);\n"
                "}\n")))
      (is (.includes
           stylesheet
           (str "a.clono-xref-table.clono-xref-title::before {\n"
                "  content: "
                "target-text(attr(data-title-href url), content);\n"
                "}\n")))
      (is (not (.includes stylesheet
                          "span.clono-xref-table.clono-xref-number::before")))
      (is (not (.includes stylesheet
                          "span.clono-xref-table.clono-xref-title::before"))))))

(deftest heading-xref-stylesheet-test
  (testing "When a numbered chapter or appendix heading is referenced, then its formatted heading number is displayed"
    (let [stylesheet (normalize-line-endings
                      (.readFileSync fs "styles/clono.css" "utf8"))]
      (doseq [[document level content]
              [["chapter"
                "h1"
                "\"第\" target-counter(attr(href url), chapter) \"章\""]
               ["chapter"
                "h2"
                (str "target-counter(attr(href url), chapter) \".\" "
                     "target-counter(attr(href url), section)")]
               ["chapter"
                "h3"
                (str "target-counter(attr(href url), chapter) \".\" "
                     "target-counter(attr(href url), section) \".\" "
                     "target-counter(attr(href url), subsection)")]
               ["appendix"
                "h1"
                (str "\"付録\" target-counter(attr(href url), appendix, "
                     "upper-alpha)")]
               ["appendix"
                "h2"
                (str "target-counter(attr(href url), appendix, upper-alpha) "
                     "\".\" target-counter(attr(href url), section)")]
               ["appendix"
                "h3"
                (str "target-counter(attr(href url), appendix, upper-alpha) "
                     "\".\" target-counter(attr(href url), section) \".\" "
                     "target-counter(attr(href url), subsection)")]]]
        (let [selector (str "a.clono-xref-heading-" document
                            ".clono-xref-heading-" level)]
          (is (.includes
               stylesheet
               (str selector ".clono-xref-number::before,\n"
                    selector ".clono-xref-number-title::before {\n"
                    "  content: " content ";\n"
                    "}\n"))
              (str document " " level))))))

  (testing "When a heading reference requests its title, then the target title is displayed without altering unresolved placeholders"
    (let [stylesheet (normalize-line-endings
                      (.readFileSync fs "styles/clono.css" "utf8"))]
      (is (.includes
           stylesheet
           (str "a.clono-xref-heading.clono-xref-number-title::after {\n"
                "  content: \" \" "
                "target-text(attr(data-title-href url), content);\n"
                "}\n")))
      (is (.includes
           stylesheet
           (str "a.clono-xref-heading.clono-xref-title::before {\n"
                "  content: "
                "target-text(attr(data-title-href url), content);\n"
                "}\n")))
      (is (not (.includes stylesheet
                          ".clono-xref-placeholder::"))))))
