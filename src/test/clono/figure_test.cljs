(ns clono.figure-test
  (:require
   ["node:fs" :as fs]
   ["node:os" :as os]
   ["node:path" :as path]
   [cljs.test :refer [deftest is testing]]
   [clono.markdown :as markdown]
   [clono.pipeline :as pipeline]
   [clono.test-support :as test-support]
   [clono.transform :as transform]))

(def valid-figure-source
  (str ":::figure[A &amp; &quot;B&quot; &lt;C&gt;]{#architecture}\n"
       "![入力 &amp; &quot;出力&quot;](./images/a&amp;b.svg)\n"
       ":::\n\n"
       "![番号なし画像](./images/plain.svg)\n"))

(def invalid-figure-cases
  [{:case "text directive"
    :source ":figure[キャプション]{#architecture}\n"
    :message "`figure`はContainer directiveとして記述する必要があります。"}
   {:case "leaf directive"
    :source "::figure[キャプション]{#architecture}\n"
    :message "`figure`はContainer directiveとして記述する必要があります。"}
   {:case "missing caption"
    :source ":::figure{#architecture}\n![図](image.svg)\n:::\n"
    :message "`figure`にはプレーンテキストのキャプションが必要です。"}
   {:case "empty caption"
    :source ":::figure[]{#architecture}\n![図](image.svg)\n:::\n"
    :message "`figure`のキャプションには空白ではないプレーンテキストが必要です。"}
   {:case "formatted caption"
    :source ":::figure[**強調**]{#architecture}\n![図](image.svg)\n:::\n"
    :message "`figure`のキャプションには空白ではないプレーンテキストが必要です。"}
   {:case "missing id"
    :source ":::figure[キャプション]\n![図](image.svg)\n:::\n"
    :message "`figure`には`id`属性が必要です。"}
   {:case "invalid id"
    :source ":::figure[キャプション]{#Architecture}\n![図](image.svg)\n:::\n"
    :message "`figure`の`id`属性には英小文字で始まる英小文字、数字、ハイフンだけの値を指定してください。"}
   {:case "unknown attribute"
    :source ":::figure[キャプション]{#architecture class=\"custom\"}\n![図](image.svg)\n:::\n"
    :message "`figure`には`id`以外の属性を指定できません。"}
   {:case "empty body"
    :source ":::figure[キャプション]{#architecture}\n:::\n"
    :message "`figure`の直下には一つのMarkdown画像だけを含む段落を一つ記述してください。"}
   {:case "plain text with image"
    :source ":::figure[キャプション]{#architecture}\n図: ![図](image.svg)\n:::\n"
    :message "`figure`の直下には一つのMarkdown画像だけを含む段落を一つ記述してください。"}
   {:case "multiple images"
    :source ":::figure[キャプション]{#architecture}\n![図1](one.svg) ![図2](two.svg)\n:::\n"
    :message "`figure`の直下には一つのMarkdown画像だけを含む段落を一つ記述してください。"}
   {:case "image reference"
    :source (str ":::figure[キャプション]{#architecture}\n"
                 "![図][image]\n"
                 ":::\n\n"
                 "[image]: image.svg\n")
    :message "`figure`の直下には一つのMarkdown画像だけを含む段落を一つ記述してください。"}
   {:case "image title"
    :source ":::figure[キャプション]{#architecture}\n![図](image.svg \"タイトル\")\n:::\n"
    :message "`figure`のMarkdown画像にはタイトルを指定できません。"}
   {:case "empty image URL"
    :source ":::figure[キャプション]{#architecture}\n![図]()\n:::\n"
    :message "`figure`の画像URLに空の値を指定できません。"}
   {:case "root-relative image URL"
    :source ":::figure[キャプション]{#architecture}\n![図](/images/image.svg)\n:::\n"
    :message "`figure`の画像URLにはルート相対パスまたはネットワークパス参照を指定できません。"}
   {:case "image URL scheme"
    :source ":::figure[キャプション]{#architecture}\n![図](https://example.com/image.svg)\n:::\n"
    :message "`figure`の画像URLにはスキームを指定できません。"}
   {:case "image URL query"
    :source ":::figure[キャプション]{#architecture}\n![図](image.svg?size=large)\n:::\n"
    :message "`figure`の画像URLにはクエリ文字列またはフラグメントを指定できません。"}
   {:case "image URL fragment"
    :source ":::figure[キャプション]{#architecture}\n![図](image.svg#fragment)\n:::\n"
    :message "`figure`の画像URLにはクエリ文字列またはフラグメントを指定できません。"}
   {:case "image URL backslash"
    :source ":::figure[キャプション]{#architecture}\n![図](images\\image.svg)\n:::\n"
    :message "`figure`の画像URLにはバックスラッシュを使用できません。"}])

(defn- transform-context [source-name]
  {:mode :transform
   :source-name source-name})

(defn- with-temporary-project [f]
  (let [project (.mkdtempSync fs (.join path (.tmpdir os) "clono-figure-test-"))]
    (try
      (f project)
      (finally
        (.rmSync fs project #js {:recursive true :force true})))))

(defn- write-file! [file-path content]
  (.mkdirSync fs (.dirname path file-path) #js {:recursive true})
  (.writeFileSync fs file-path content "utf8"))

(defn- build-context [source-root input-path kind]
  {:mode :build
   :source-name (.relative path source-root input-path)
   :input-path input-path
   :source-root-path source-root
   :publication-entry (when kind
                        {:type :document
                         :path (.relative path source-root input-path)
                         :kind kind
                         :include-in-toc true})})

(defn- normalize-line-endings [value]
  (.replace value (js/RegExp. "\\r\\n?" "g") "\n"))

(deftest figure-transformation-test
  (let [result (pipeline/run (transform-context "figure.md")
                             valid-figure-source)
        output (:output result)
        tree (markdown/parse output)]
    (testing "When a valid figure directive is transformed, then fixed figure HTML and an unchanged ordinary image are returned"
      (is (:ok? result))
      (is (empty? (:diagnostics result)))
      (is (.includes output
                     (str "<figure class=\"clono-numbered-figure\" id=\"figure-architecture\">\n"
                          "<img src=\"./images/a&amp;b.svg\" alt=\"入力 &amp; &quot;出力&quot;\">\n"
                          "<figcaption class=\"clono-figure-caption\" id=\"figure-architecture-caption\">A &amp; &quot;B&quot; &lt;C&gt;</figcaption>\n"
                          "</figure>")))
      (is (nil? (test-support/directive tree "figure")))
      (is (= 1 (count (test-support/nodes-by-type tree "html"))))
      (let [ordinary-images (test-support/nodes-by-type tree "image")]
        (is (= 1 (count ordinary-images)))
        (is (= "番号なし画像" (.-alt (first ordinary-images))))))

    (testing "When a figure has empty alternative text, then an empty alt attribute is preserved"
      (let [empty-alt-result
            (pipeline/run
             (transform-context "empty-alt.md")
             ":::figure[装飾画像]{#decoration}\n![](decoration.svg)\n:::\n")]
        (is (:ok? empty-alt-result))
        (is (.includes (:output empty-alt-result) "alt=\"\"")))))

  (testing "When the clono stylesheet is inspected, then it provides the required figure counter rules"
    (let [stylesheet (normalize-line-endings
                      (.readFileSync fs "styles/clono.css" "utf8"))]
      (is (.includes stylesheet "body {\n  counter-reset: figure;\n}\n"))
      (is (.includes stylesheet
                     ".clono-numbered-figure {\n  counter-increment: figure;\n}\n"))
      (is (.includes
           stylesheet
           (str ".clono-numbered-figure > .clono-figure-caption::before {\n"
                "  content: \"図\" counter(chapter) \".\" counter(figure) \" \";\n"
                "}\n"))))))

(deftest invalid-figure-test
  (testing "When a figure directive violates its local contract, then transformation fails with a positioned diagnostic"
    (doseq [{:keys [case source message]} invalid-figure-cases]
      (let [result (pipeline/run (transform-context "invalid-figure.md") source)
            messages (mapv :message (:diagnostics result))]
        (is (false? (:ok? result)) case)
        (is (nil? (:output result)) case)
        (is (some #{message} messages) case)
        (doseq [problem (:diagnostics result)]
          (is (= "invalid-figure.md" (:file problem)) case)
          (is (= "figure" (:directive problem)) case)
          (is (pos-int? (:line problem)) case)
          (is (pos-int? (:column problem)) case))))))

(deftest figure-document-validation-test
  (testing "When a figure is nested in a non-directive block, then its top-level placement is diagnosed"
    (let [source (str "> :::figure[引用内の図]{#nested}\n"
                      "> ![図](image.svg)\n"
                      "> :::\n")
          result (pipeline/run (transform-context "nested-figure.md") source)]
      (is (= ["`figure`はMarkdown文書のトップレベルに記述する必要があります。"]
             (mapv :message (:diagnostics result))))
      (is (nil? (:output result)))))

  (testing "When a figure is inside a known container, then only the outer container contract is reported"
    (let [source (str "::::column[コラム]\n"
                      ":::figure[コラム内の図]{#nested}\n"
                      "![図](image.svg)\n"
                      ":::\n"
                      "::::\n")
          result (pipeline/run (transform-context "column-figure.md") source)]
      (is (= ["`column`内ではdirectiveを使用できません。"]
             (mapv :message (:diagnostics result))))))

  (testing "When an unknown container is inside a figure, then only the unknown directive is reported"
    (let [source (str "::::figure[全体構成]{#architecture}\n"
                      ":::third-party\n"
                      "未知の内容です。\n"
                      ":::\n"
                      "::::\n")
          result (pipeline/run (transform-context "unknown-in-figure.md") source)]
      (is (= ["`third-party`は登録されていないdirectiveです。"]
             (mapv :message (:diagnostics result))))))

  (testing "When a known figure container has no closing fence, then its opening position is diagnosed"
    (let [result (pipeline/run
                  (transform-context "unclosed-figure.md")
                  ":::figure[全体構成]{#architecture}\n![図](image.svg)\n")]
      (is (= [{:file "unclosed-figure.md"
               :line 1
               :column 1
               :directive "figure"
               :message "`figure`の終了マーカーがありません。"}]
             (:diagnostics result)))))

  (testing "When logical or generated HTML IDs collide, then the later figure is diagnosed"
    (doseq [[source message]
            [[(str ":::figure[一つ目]{#same}\n![図](one.svg)\n:::\n\n"
                   ":::figure[二つ目]{#same}\n![図](two.svg)\n:::\n")
              "`figure`の論理ID`same`が重複しています。"]
             [(str ":::figure[一つ目]{#diagram}\n![図](one.svg)\n:::\n\n"
                   ":::figure[二つ目]{#diagram-caption}\n![図](two.svg)\n:::\n")
              "`figure`から生成するHTML ID`figure-diagram-caption`が重複しています。"]]]
      (let [result (pipeline/run (transform-context "duplicate-figure.md") source)]
        (is (= [message] (mapv :message (:diagnostics result))))))))

(deftest figure-reference-target-collection-test
  (testing "When a document contains figures, then their reference targets are collected in source order"
    (let [context (transform-context "figures.md")
          tree (markdown/parse
                (str ":::figure[一つ目の図]{#first}\n"
                     "![図1](first.svg)\n"
                     ":::\n\n"
                     ":::figure[二つ目の図]{#second}\n"
                     "![図2](second.svg)\n"
                     ":::\n"))]
      (is (= [{:logical-id "first"
               :type "figure"
               :target-id "figure-first"
               :title-target-id "figure-first-caption"
               :numbered? true
               :source-name "figures.md"
               :line 1
               :column 1}
              {:logical-id "second"
               :type "figure"
               :target-id "figure-second"
               :title-target-id "figure-second-caption"
               :numbered? true
               :source-name "figures.md"
               :line 5
               :column 1}]
             (transform/collect-reference-targets tree context))))))

(deftest build-figure-validation-test
  (with-temporary-project
    (fn [project]
      (let [source-root (.join path project "manuscripts")
            input-path (.join path source-root "chapter.md")
            encoded-image (.join path source-root "images" "architecture diagram.svg")
            source (str ":::figure[全体構成]{#architecture}\n"
                        "![図](./images/architecture%20diagram.svg)\n"
                        ":::\n")]
        (write-file! input-path source)
        (write-file! encoded-image "<svg></svg>\n")

        (testing "When a chapter or appendix figure points to an existing file inside the source root, then build validation succeeds and preserves its encoded URL"
          (doseq [kind ["chapter" "appendix"]]
            (let [result (pipeline/run
                          (build-context source-root input-path kind)
                          source)]
              (is (:ok? result) kind)
              (is (.includes (:output result)
                             "src=\"./images/architecture%20diagram.svg\"")
                  kind))))

        (testing "When an unlisted Markdown file contains a figure, then it is transformed without requiring a publication kind"
          (let [result (pipeline/run
                        (build-context source-root input-path nil)
                        source)]
            (is (:ok? result))
            (is (empty? (:diagnostics result)))
            (is (.includes (:output result)
                           "id=\"figure-architecture\""))))

        (testing "When an unlisted Markdown file refers to its own figure, then book references are diagnosed as unavailable"
          (let [result (pipeline/run
                        (build-context source-root input-path nil)
                        (str source
                             "\n:xref[architecture]{type=\"figure\" format=\"number\"}\n"))]
            (is (false? (:ok? result)))
            (is (nil? (:output result)))
            (is (= [{:file "chapter.md"
                     :line 5
                     :column 1
                     :directive "xref"
                     :message (str "`publication`に掲載されていないMarkdownでは"
                                   "`xref`を使用できません。")}]
                   (:diagnostics result)))))

        (testing "When a frontmatter or backmatter document contains a figure, then its document kind is diagnosed"
          (doseq [kind ["frontmatter" "backmatter"]]
            (let [result (pipeline/run
                          (build-context source-root input-path kind)
                          source)]
              (is (= ["`figure`は本文または付録の掲載Markdownにだけ記述できます。"]
                     (mapv :message (:diagnostics result)))
                  kind))))

        (testing "When a build image path is missing, outside the source root, malformed, or not a regular file, then it is diagnosed"
          (.mkdirSync fs (.join path source-root "images" "directory"))
          (write-file! (.join path project "outside.svg") "<svg></svg>\n")
          (doseq [[url message]
                  [["./images/missing.svg"
                    "`figure`の画像ファイルが存在しません。"]
                   ["../outside.svg"
                    "`figure`の画像パスは入力原稿ルートの内側を指す必要があります。"]
                   ["..%2Foutside.svg"
                    "`figure`の画像パスは入力原稿ルートの内側を指す必要があります。"]
                   ["./images/%ZZ.svg"
                    "`figure`の画像URLを安全なファイルパスとして解決できません。"]
                   ["./images%5Carchitecture.svg"
                    "`figure`の画像URLをデコードしたパスにバックスラッシュを使用できません。"]
                   ["./images/directory"
                    "`figure`の画像パスには通常ファイルを指定してください。"]]]
            (let [invalid-source
                  (str ":::figure[検証対象]{#target}\n"
                       "![図](" url ")\n"
                       ":::\n")
                  result (pipeline/run
                          (build-context source-root input-path "chapter")
                          invalid-source)]
              (is (= [message] (mapv :message (:diagnostics result))) url))))))))
