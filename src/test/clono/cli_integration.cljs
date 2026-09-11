(ns clono.cli-integration
  (:require
   ["node:child_process" :as child-process]
   ["node:fs" :as fs]
   ["node:os" :as os]
   ["node:path" :as path]))

(def cli-path
  (.resolve path js/__dirname ".." "dist" "clono.js"))

(defn- write-file! [file-path content]
  (.mkdirSync fs (.dirname path file-path) #js {:recursive true})
  (.writeFileSync fs file-path content "utf8"))

(defn- normalize-line-endings [value]
  (.replace value (js/RegExp. "\\r\\n?" "g") "\n"))

(defn- ensure! [condition message]
  (when-not condition
    (throw (js/Error. message))))

(defn- run-cli [arguments cwd]
  (let [result (.spawnSync child-process
                           (.-execPath js/process)
                           (clj->js (into [cli-path] arguments))
                           #js {:cwd cwd
                                :encoding "utf8"
                                :timeout 30000})]
    (when-let [error (.-error result)]
      (throw error))
    result))

(defn- valid-config []
  (str "export default {\n"
       "  sourceRoot: 'manuscripts',\n"
       "  outputRoot: 'build/manuscripts',\n"
       "  publication: [\n"
       "    { type: 'blank-page' },\n"
       "    { type: 'document', path: 'chapter.md', kind: 'chapter', includeInToc: true },\n"
       "    { type: 'blank-page' },\n"
       "  ],\n"
       "};\n"))

(defn- invalid-config []
  (str "export default {\n"
       "  sourceRoot: 'manuscripts',\n"
       "  outputRoot: 'build/manuscripts',\n"
       "  publication: [\n"
       "    { type: 'document', path: 'a.md', kind: 'chapter', includeInToc: true },\n"
       "    { type: 'document', path: 'b.md', kind: 'chapter', includeInToc: true },\n"
       "  ],\n"
       "};\n"))

(defn- reference-config []
  (str "export default {\n"
       "  sourceRoot: 'manuscripts',\n"
       "  outputRoot: 'build/manuscripts',\n"
       "  publication: [\n"
       "    { type: 'document', path: 'chapters/one.md', kind: 'chapter', includeInToc: true },\n"
       "    { type: 'document', path: 'appendices/two.MD', kind: 'appendix', includeInToc: true },\n"
       "  ],\n"
       "};\n"))

(defn- heading-reference-config []
  (str "export default {\n"
       "  sourceRoot: 'manuscripts',\n"
       "  outputRoot: 'build/manuscripts',\n"
       "  publication: [\n"
       "    { type: 'document', path: 'frontmatter.md', kind: 'frontmatter', includeInToc: true },\n"
       "    { type: 'document', path: 'chapters/main.md', kind: 'chapter', includeInToc: true },\n"
       "    { type: 'document', path: 'appendices/details.md', kind: 'appendix', includeInToc: true },\n"
       "    { type: 'document', path: 'backmatter.md', kind: 'backmatter', includeInToc: true },\n"
       "  ],\n"
       "};\n"))

(defn- chapter-source [reference-id figure-id]
  (str ":xref[" reference-id "]"
       "{type=\"figure\" format=\"number-title\"}\n\n"
       ":::figure[概要図]{#" figure-id "}\n"
       "![概要](../images/overview.svg)\n"
       ":::\n"))

(defn- appendix-source [figure-id]
  (str ":xref[overview]{type=\"figure\" format=\"title\"}\n\n"
       ":::figure[処理フロー]{#" figure-id "}\n"
       "![処理](../images/workflow.svg)\n"
       ":::\n"))

(defn- table-chapter-source []
  (str ":xref[workflow]{type=\"table\" format=\"number-title\"}\n\n"
       ":::table[実行環境]{#runtime}\n"
       "| 項目 | 値 |\n"
       "| --- | --- |\n"
       "| Node.js | 24 |\n"
       ":::\n"))

(defn- table-appendix-source []
  (str ":xref[runtime]{type=\"table\" format=\"title\"}\n\n"
       ":::table[処理フロー]{#workflow}\n"
       "| 工程 | 状態 |\n"
       "| --- | --- |\n"
       "| 変換 | 完了 |\n"
       ":::\n"))

(defn- listing-chapter-source [reference-id]
  (str ":xref[" reference-id "]"
       "{type=\"listing\" format=\"number-title\"}\n\n"
       ":::listing[起動処理]{#startup}\n"
       "```kotlin\n"
       "fun main() {}\n"
       "```\n"
       ":::\n"))

(defn- listing-appendix-source []
  (str ":xref[startup]{type=\"listing\" format=\"title\"}\n\n"
       ":::listing[処理フロー]{#workflow}\n"
       "```\n"
       "prepare\nexecute\n"
       "```\n"
       ":::\n"))

(defn- verify-success! [^js result context]
  (ensure! (= 0 (.-status result))
           (str context " failed: " (.-stderr result)))
  (ensure! (= "" (.-stdout result))
           (str context " wrote to stdout"))
  (ensure! (= "" (.-stderr result))
           (str context " wrote to stderr")))

(defn- verify-transform! [root]
  (let [input (.join path root "single.md")
        output (.join path root "single-output.md")]
    (write-file! input
                 (str ":::align{position=\"right\"}\n署名\n:::\n\n"
                      ":xref[external-figure]"
                      "{type=\"figure\" format=\"number-title\"}\n"))
    (verify-success!
     (run-cli ["transform" input "--output" output] root)
     "Release transform command")
    (let [content (.readFileSync fs output "utf8")]
      (ensure! (.includes content "<div class=\"clono-align-right\">")
               "Release transform command did not transform the manuscript")
      (ensure! (.includes
                content
                (str "<span class=\"clono-xref clono-xref-figure "
                     "clono-xref-number-title clono-xref-placeholder\">"
                     "図X.X 参照先未解決</span>"))
               "Release transform command did not generate the xref placeholder")
      (ensure! (not (.includes content "external-figure"))
               "Release transform command exposed the unresolved logical ID"))))

(defn- verify-heading-transform! [root]
  (let [input (.join path root "single-heading.md")
        output (.join path root "single-heading-output.md")]
    (write-file!
     input
     (str ":xref[introduction]{type=\"heading\" format=\"number\"}\n\n"
          "# はじめに {#introduction}\n\n"
          "## 全体構造 {#structure}\n\n"
          ":xref[structure]{type=\"heading\" format=\"number-title\"}\n\n"
          ":xref[external-heading]{type=\"heading\" format=\"title\"}\n"))
    (verify-success!
     (run-cli ["transform" input "--output" output] root)
     "Release transform command with heading references")
    (let [content (.readFileSync fs output "utf8")]
      (ensure!
       (.includes
        content
        (str "<a class=\"clono-xref clono-xref-heading "
             "clono-xref-heading-h1 clono-xref-heading-chapter "
             "clono-xref-number\" href=\"#introduction\"></a>"))
       "Release transform command did not resolve the H1 reference")
      (ensure!
       (.includes
        content
        (str "<a class=\"clono-xref clono-xref-heading "
             "clono-xref-heading-h2 clono-xref-heading-chapter "
             "clono-xref-number-title\" href=\"#structure\" "
             "data-title-href=\"#structure\"></a>"))
       "Release transform command did not resolve the H2 reference")
      (ensure!
       (.includes
        content
        (str "<span class=\"clono-xref clono-xref-heading "
             "clono-xref-title clono-xref-placeholder\">"
             "参照先未解決</span>"))
       "Release transform command did not generate the heading placeholder")
      (ensure! (not (.includes content "external-heading"))
               "Release transform command exposed the unresolved heading ID"))))

(defn- verify-listing-transform! [root]
  (let [input (.join path root "single-listing.md")
        output (.join path root "single-listing-output.md")]
    (write-file!
     input
     (str ":xref[greeting]{type=\"listing\" format=\"number\"}\n\n"
          ":xref[greeting]{type=\"listing\" format=\"number-title\"}\n\n"
          ":::listing[挨拶を表示する関数]{#greeting}\n"
          "```kotlin\n"
          "fun greet() {}\n"
          "```\n"
          ":::\n\n"
          ":xref[greeting]{type=\"listing\" format=\"title\"}\n\n"
          ":xref[external-listing]"
          "{type=\"listing\" format=\"number-title\"}\n"))
    (verify-success!
     (run-cli ["transform" input "--output" output] root)
     "Release transform command with listing references")
    (let [content (.readFileSync fs output "utf8")]
      (ensure!
       (.includes content
                  (str "<figure class=\"clono-numbered-listing\" "
                       "id=\"listing-greeting\">"))
       "Release transform command did not transform the numbered listing")
      (ensure! (.includes content "```kotlin\nfun greet() {}\n```")
               "Release transform command did not preserve the fenced code")
      (ensure!
       (.includes
        content
        (str "class=\"clono-xref clono-xref-listing "
             "clono-xref-number\" href=\"#listing-greeting\""))
       "Release transform command did not resolve the listing number")
      (ensure!
       (.includes
        content
        (str "class=\"clono-xref clono-xref-listing "
             "clono-xref-number-title\" href=\"#listing-greeting\" "
             "data-title-href=\"#listing-greeting-caption\""))
       "Release transform command did not resolve the listing number and title")
      (ensure!
       (.includes
        content
        (str "class=\"clono-xref clono-xref-listing "
             "clono-xref-title\" href=\"#listing-greeting\" "
             "data-title-href=\"#listing-greeting-caption\""))
       "Release transform command did not resolve the listing title")
      (ensure!
       (.includes
        content
        (str "<span class=\"clono-xref clono-xref-listing "
             "clono-xref-number-title clono-xref-placeholder\">"
             "リストX.X 参照先未解決</span>"))
       "Release transform command did not generate the listing placeholder")
      (ensure! (not (.includes content "external-listing"))
               "Release transform command exposed the unresolved listing ID"))))

(defn- verify-build! [root]
  (let [project (.join path root "book")
        output (.join path project "build" "manuscripts")]
    (write-file! (.join path project "clono.config.mjs") (valid-config))
    (write-file! (.join path project "manuscripts" "chapter.md")
                 ":::align{position=\"right\"}\nThunder Claw\n:::\n")
    (write-file! (.join path project "manuscripts" "images" "logo.txt")
                 "static asset\n")

    (verify-success! (run-cli ["build" project] root)
                     "Release build command with an explicit project")
    (ensure! (.includes (.readFileSync fs (.join path output "chapter.md") "utf8")
                        "<div class=\"clono-align-right\">")
             "Release build command did not transform the manuscript")
    (ensure! (= "static asset\n"
                (.readFileSync fs (.join path output "images" "logo.txt") "utf8"))
             "Release build command did not copy a static file")
    (let [stylesheet (.join path output "_clono" "styles" "clono.css")
          blank-page (.join path output "_clono" "pages" "blank-page.html")]
      (ensure! (.existsSync fs stylesheet)
               "Release build command did not copy the clono stylesheet")
      (ensure! (.includes (.readFileSync fs stylesheet "utf8")
                          ".clono-blank-page")
               "Release build command copied a stylesheet without the blank page rule")
      (ensure! (.existsSync fs blank-page)
               "Release build command did not generate the blank page resource")
      (ensure! (.includes (.readFileSync fs blank-page "utf8")
                          "<div class=\"clono-blank-page\" aria-hidden=\"true\"></div>")
               "Release build command generated an invalid blank page resource"))
    (ensure! (.existsSync fs (.join path output ".clono-output.json"))
             "Release build command did not create the ownership marker")

    (write-file! (.join path output "stale.txt") "stale\n")
    (verify-success! (run-cli ["build"] project)
                     "Release build command with the default project")
    (ensure! (false? (.existsSync fs (.join path output "stale.txt")))
             "Release build command did not replace owned output")))

(defn- verify-diagnostics! [root]
  (let [project (.join path root "invalid-book")
        output (.join path project "build" "manuscripts")]
    (write-file! (.join path project "clono.config.mjs") (invalid-config))
    (write-file! (.join path project "manuscripts" "a.md")
                 ":first[未知]\n")
    (write-file! (.join path project "manuscripts" "b.md")
                 ":second[未知]\n")
    (let [result (run-cli ["build" project] root)
          expected-stderr
          (str "a.md:1:1: `first`は登録されていないdirectiveです。\n"
               "b.md:1:1: `second`は登録されていないdirectiveです。\n")]
      (ensure! (= 1 (.-status result))
               "Release build command did not fail for invalid manuscripts")
      (ensure! (= "" (.-stdout result))
               "Failed release build command wrote to stdout")
      (ensure! (= expected-stderr (.-stderr result))
               (str "Release build diagnostics were incorrectly formatted or ordered: "
                    (.-stderr result)))
      (ensure! (false? (.existsSync fs output))
               "Failed release build command published partial output"))))

(defn- verify-unchanged-output! [output expected-files context]
  (doseq [[relative-path expected-content] expected-files]
    (let [actual (.readFileSync fs (.join path output relative-path) "utf8")]
      (ensure! (= expected-content actual)
               (str context " changed existing output: " relative-path))))
  (ensure! (= "keep\n"
              (.readFileSync fs (.join path output "keep.txt") "utf8"))
           (str context " replaced the existing output directory")))

(defn- verify-reference-build! [root]
  (let [project (.join path root "reference-book")
        source (.join path project "manuscripts")
        output (.join path project "build" "manuscripts")
        chapter-input (.join path source "chapters" "one.md")
        appendix-input (.join path source "appendices" "two.MD")
        chapter-output (.join path output "chapters" "one.md")
        appendix-output (.join path output "appendices" "two.MD")
        marker-output (.join path output ".clono-output.json")]
    (write-file! (.join path project "clono.config.mjs")
                 (reference-config))
    (write-file! chapter-input (chapter-source "workflow" "overview"))
    (write-file! appendix-input (appendix-source "workflow"))
    (write-file! (.join path source "images" "overview.svg")
                 "<svg></svg>\n")
    (write-file! (.join path source "images" "workflow.svg")
                 "<svg></svg>\n")

    (verify-success! (run-cli ["build" project] root)
                     "Release build command with cross-document references")
    (let [chapter-content (.readFileSync fs chapter-output "utf8")
          appendix-content (.readFileSync fs appendix-output "utf8")
          marker-content (.readFileSync fs marker-output "utf8")
          expected-files {"chapters/one.md" chapter-content
                          "appendices/two.MD" appendix-content
                          ".clono-output.json" marker-content}]
      (ensure! (.includes
                chapter-content
                (str "href=\"../appendices/two.html#figure-workflow\" "
                     "data-title-href=\"../appendices/two.html"
                     "#figure-workflow-caption\""))
               "Release build command did not resolve the appendix reference")
      (ensure! (.includes
                appendix-content
                (str "href=\"../chapters/one.html#figure-overview\" "
                     "data-title-href=\"../chapters/one.html"
                     "#figure-overview-caption\""))
               "Release build command did not resolve the chapter reference")
      (ensure! (not (.includes chapter-content "clono-xref-placeholder"))
               "Release build command emitted a chapter xref placeholder")
      (ensure! (not (.includes appendix-content "clono-xref-placeholder"))
               "Release build command emitted an appendix xref placeholder")

      (write-file! (.join path output "keep.txt") "keep\n")
      (write-file! chapter-input (chapter-source "workflow" "workflow"))
      (let [result (run-cli ["build" project] root)]
        (ensure! (= 1 (.-status result))
                 "Release build command did not fail for a duplicate book ID")
        (ensure! (= "" (.-stdout result))
                 "Duplicate-ID build failure wrote to stdout")
        (ensure! (= (str "chapters/one.md:3:1: `figure`の論理ID"
                         "`workflow`が重複しています。\n")
                    (.-stderr result))
                 (str "Duplicate-ID diagnostics were incorrect: "
                      (.-stderr result)))
        (verify-unchanged-output! output
                                  expected-files
                                  "Duplicate-ID build failure"))

      (write-file! chapter-input (chapter-source "missing" "overview"))
      (let [result (run-cli ["build" project] root)]
        (ensure! (= 1 (.-status result))
                 "Release build command did not fail for an undefined reference")
        (ensure! (= "" (.-stdout result))
                 "Undefined-reference build failure wrote to stdout")
        (ensure! (= (str "chapters/one.md:1:1: `xref`の参照先"
                         "`missing`を解決できません。\n")
                    (.-stderr result))
                 (str "Undefined-reference diagnostics were incorrect: "
                      (.-stderr result)))
        (verify-unchanged-output! output
                                  expected-files
                                  "Undefined-reference build failure")))))

(defn- verify-table-reference-build! [root]
  (let [project (.join path root "table-reference-book")
        source (.join path project "manuscripts")
        output (.join path project "build" "manuscripts")
        chapter-output (.join path output "chapters" "one.md")
        appendix-output (.join path output "appendices" "two.MD")
        stylesheet-output (.join path output "_clono" "styles" "clono.css")]
    (write-file! (.join path project "clono.config.mjs")
                 (reference-config))
    (write-file! (.join path source "chapters" "one.md")
                 (table-chapter-source))
    (write-file! (.join path source "appendices" "two.MD")
                 (table-appendix-source))

    (verify-success! (run-cli ["build" project] root)
                     "Release build command with table references")
    (let [chapter-content (.readFileSync fs chapter-output "utf8")
          appendix-content (.readFileSync fs appendix-output "utf8")
          stylesheet-content
          (normalize-line-endings
           (.readFileSync fs stylesheet-output "utf8"))]
      (ensure!
       (.includes chapter-content
                  (str "<figure class=\"clono-numbered-table\" "
                       "id=\"table-runtime\">"))
       "Release build command did not transform the chapter table")
      (ensure!
       (.includes
        chapter-content
        (str "class=\"clono-xref clono-xref-table "
             "clono-xref-number-title\" "
             "href=\"../appendices/two.html#table-workflow\" "
             "data-title-href=\"../appendices/two.html"
             "#table-workflow-caption\""))
       "Release build command did not resolve the appendix table reference")
      (ensure!
       (.includes appendix-content
                  (str "<figure class=\"clono-numbered-table\" "
                       "id=\"table-workflow\">"))
       "Release build command did not transform the appendix table")
      (ensure!
       (.includes
        appendix-content
        (str "class=\"clono-xref clono-xref-table clono-xref-title\" "
             "href=\"../chapters/one.html#table-runtime\" "
             "data-title-href=\"../chapters/one.html"
             "#table-runtime-caption\""))
       "Release build command did not resolve the chapter table reference")
      (doseq [content [chapter-content appendix-content]]
        (ensure! (not (.includes content "clono-xref-placeholder"))
                 "Release build command emitted a table placeholder"))
      (ensure!
       (.includes
        stylesheet-content
        (str "a.clono-xref-table.clono-xref-number::before,\n"
             "a.clono-xref-table.clono-xref-number-title::before"))
       "Release build command copied a stylesheet without table numbers")
      (ensure!
       (.includes
        stylesheet-content
        "a.clono-xref-table.clono-xref-title::before")
       "Release build command copied a stylesheet without table titles"))))

(defn- verify-listing-reference-build! [root]
  (let [project (.join path root "listing-reference-book")
        source (.join path project "manuscripts")
        output (.join path project "build" "manuscripts")
        chapter-input (.join path source "chapters" "one.md")
        chapter-output (.join path output "chapters" "one.md")
        appendix-output (.join path output "appendices" "two.MD")
        marker-output (.join path output ".clono-output.json")
        stylesheet-output (.join path output "_clono" "styles" "clono.css")]
    (write-file! (.join path project "clono.config.mjs")
                 (reference-config))
    (write-file! chapter-input (listing-chapter-source "workflow"))
    (write-file! (.join path source "appendices" "two.MD")
                 (listing-appendix-source))

    (verify-success! (run-cli ["build" project] root)
                     "Release build command with listing references")
    (let [chapter-content (.readFileSync fs chapter-output "utf8")
          appendix-content (.readFileSync fs appendix-output "utf8")
          marker-content (.readFileSync fs marker-output "utf8")
          stylesheet-content (.readFileSync fs stylesheet-output "utf8")
          normalized-stylesheet-content
          (normalize-line-endings stylesheet-content)
          expected-files {"chapters/one.md" chapter-content
                          "appendices/two.MD" appendix-content
                          "_clono/styles/clono.css" stylesheet-content
                          ".clono-output.json" marker-content}]
      (ensure!
       (.includes chapter-content
                  (str "<figure class=\"clono-numbered-listing\" "
                       "id=\"listing-startup\">"))
       "Release build command did not transform the chapter listing")
      (ensure! (.includes chapter-content "```kotlin\nfun main() {}\n```")
               "Release build command did not preserve the chapter code")
      (ensure!
       (.includes
        chapter-content
        (str "class=\"clono-xref clono-xref-listing "
             "clono-xref-number-title\" "
             "href=\"../appendices/two.html#listing-workflow\" "
             "data-title-href=\"../appendices/two.html"
             "#listing-workflow-caption\""))
       "Release build command did not resolve the appendix listing reference")
      (ensure!
       (.includes appendix-content
                  (str "<figure class=\"clono-numbered-listing\" "
                       "id=\"listing-workflow\">"))
       "Release build command did not transform the appendix listing")
      (ensure! (.includes appendix-content "```\nprepare\nexecute\n```")
               "Release build command did not preserve the appendix code")
      (ensure!
       (.includes
        appendix-content
        (str "class=\"clono-xref clono-xref-listing clono-xref-title\" "
             "href=\"../chapters/one.html#listing-startup\" "
             "data-title-href=\"../chapters/one.html"
             "#listing-startup-caption\""))
       "Release build command did not resolve the chapter listing reference")
      (doseq [content [chapter-content appendix-content]]
        (ensure! (not (.includes content "clono-xref-placeholder"))
                 "Release build command emitted a listing placeholder"))
      (ensure!
       (.includes
        normalized-stylesheet-content
        (str "a.clono-xref-listing.clono-xref-number::before,\n"
             "a.clono-xref-listing.clono-xref-number-title::before"))
       "Release build command copied a stylesheet without listing numbers")
      (ensure!
       (.includes
        normalized-stylesheet-content
        "a.clono-xref-listing.clono-xref-title::before")
       "Release build command copied a stylesheet without listing titles")

      (write-file! (.join path output "keep.txt") "keep\n")
      (write-file! chapter-input (listing-chapter-source "missing-listing"))
      (let [result (run-cli ["build" project] root)]
        (ensure! (= 1 (.-status result))
                 "Release build command did not fail for an undefined listing reference")
        (ensure! (= "" (.-stdout result))
                 "Undefined listing reference build failure wrote to stdout")
        (ensure! (= (str "chapters/one.md:1:1: `xref`の参照先"
                         "`missing-listing`を解決できません。\n")
                    (.-stderr result))
                 (str "Undefined listing reference diagnostics were incorrect: "
                      (.-stderr result)))
        (verify-unchanged-output! output
                                  expected-files
                                  "Undefined listing reference build failure")))))

(defn- verify-heading-reference-build! [root]
  (let [project (.join path root "heading-reference-book")
        source (.join path project "manuscripts")
        output (.join path project "build" "manuscripts")
        frontmatter-input (.join path source "frontmatter.md")
        chapter-input (.join path source "chapters" "main.md")
        appendix-input (.join path source "appendices" "details.md")
        backmatter-input (.join path source "backmatter.md")
        frontmatter-output (.join path output "frontmatter.md")
        chapter-output (.join path output "chapters" "main.md")
        appendix-output (.join path output "appendices" "details.md")
        backmatter-output (.join path output "backmatter.md")
        marker-output (.join path output ".clono-output.json")
        stylesheet-output (.join path output "_clono" "styles" "clono.css")]
    (write-file! (.join path project "clono.config.mjs")
                 (heading-reference-config))
    (write-file!
     frontmatter-input
     (str "# はじめに {#preface}\n\n"
          ":xref[appendix-details]{type=\"heading\" format=\"title\"}\n"))
    (write-file!
     chapter-input
     (str ":xref[appendix-details]"
          "{type=\"heading\" format=\"number-title\"}\n\n"
          ":xref[preface]{type=\"heading\" format=\"title\"}\n\n"
          "# 本文 {#main}\n\n"
          "## 基本構造 {#basic-structure}\n"))
    (write-file!
     appendix-input
     (str ":xref[basic-structure]{type=\"heading\" format=\"number\"}\n\n"
          "# 追加情報 {#appendix-details}\n"))
    (write-file!
     backmatter-input
     (str "# 著者 {#authors}\n\n"
          ":xref[main]{type=\"heading\" format=\"title\"}\n"))

    (verify-success! (run-cli ["build" project] root)
                     "Release build command with heading references")
    (let [frontmatter-content (.readFileSync fs frontmatter-output "utf8")
          chapter-content (.readFileSync fs chapter-output "utf8")
          appendix-content (.readFileSync fs appendix-output "utf8")
          backmatter-content (.readFileSync fs backmatter-output "utf8")
          marker-content (.readFileSync fs marker-output "utf8")
          stylesheet-content (.readFileSync fs stylesheet-output "utf8")
          normalized-stylesheet-content
          (normalize-line-endings stylesheet-content)
          expected-files {"frontmatter.md" frontmatter-content
                          "chapters/main.md" chapter-content
                          "appendices/details.md" appendix-content
                          "backmatter.md" backmatter-content
                          "_clono/styles/clono.css" stylesheet-content
                          ".clono-output.json" marker-content}]
      (ensure!
       (.includes
        frontmatter-content
        (str "class=\"clono-xref clono-xref-heading "
             "clono-xref-heading-h1 clono-xref-heading-appendix "
             "clono-xref-title\" "
             "href=\"appendices/details.html#appendix-details\" "
             "data-title-href=\"appendices/details.html"
             "#appendix-details\""))
       "Release build command did not resolve the frontmatter reference")
      (ensure!
       (.includes
        chapter-content
        (str "class=\"clono-xref clono-xref-heading "
             "clono-xref-heading-h1 clono-xref-heading-appendix "
             "clono-xref-number-title\" "
             "href=\"../appendices/details.html#appendix-details\" "
             "data-title-href=\"../appendices/details.html"
             "#appendix-details\""))
       "Release build command did not resolve the appendix heading reference")
      (ensure!
       (.includes
        chapter-content
        (str "class=\"clono-xref clono-xref-heading "
             "clono-xref-heading-h1 clono-xref-heading-unnumbered "
             "clono-xref-title\" href=\"../frontmatter.html#preface\" "
             "data-title-href=\"../frontmatter.html#preface\""))
       "Release build command did not resolve the unnumbered heading reference")
      (ensure!
       (.includes
        appendix-content
        (str "class=\"clono-xref clono-xref-heading "
             "clono-xref-heading-h2 clono-xref-heading-chapter "
             "clono-xref-number\" "
             "href=\"../chapters/main.html#basic-structure\""))
       "Release build command did not resolve the chapter section reference")
      (ensure!
       (.includes
        backmatter-content
        (str "class=\"clono-xref clono-xref-heading "
             "clono-xref-heading-h1 clono-xref-heading-chapter "
             "clono-xref-title\" href=\"chapters/main.html#main\" "
             "data-title-href=\"chapters/main.html#main\""))
       "Release build command did not resolve the backmatter reference")
      (doseq [content [frontmatter-content
                       chapter-content
                       appendix-content
                       backmatter-content]]
        (ensure! (not (.includes content "clono-xref-placeholder"))
                 "Release build command emitted a heading placeholder"))
      (ensure!
       (.includes
        normalized-stylesheet-content
        (str "a.clono-xref-heading-chapter.clono-xref-heading-h1"
             ".clono-xref-number::before,"))
       "Release build command copied a stylesheet without chapter references")
      (ensure!
       (.includes
        normalized-stylesheet-content
        (str "a.clono-xref-heading-appendix.clono-xref-heading-h3"
             ".clono-xref-number-title::before"))
       "Release build command copied a stylesheet without appendix references")

      (write-file! (.join path output "keep.txt") "keep\n")
      (write-file!
       chapter-input
       (str ":xref[preface]{type=\"heading\" format=\"number\"}\n\n"
            ":xref[appendix-details]"
            "{type=\"heading\" format=\"number-title\"}\n\n"
            ":xref[preface]{type=\"heading\" format=\"title\"}\n\n"
            "# 本文 {#main}\n\n"
            "## 基本構造 {#basic-structure}\n"))
      (let [result (run-cli ["build" project] root)]
        (ensure! (= 1 (.-status result))
                 "Release build command accepted a numbered frontmatter reference")
        (ensure! (= "" (.-stdout result))
                 "Invalid heading reference build wrote to stdout")
        (ensure!
         (= (str "chapters/main.md:1:1: `xref`の表示形式に番号を持たない"
                 "参照先の番号を指定できません。\n")
            (.-stderr result))
         (str "Invalid heading reference diagnostics were incorrect: "
              (.-stderr result)))
        (verify-unchanged-output! output
                                  expected-files
                                  "Invalid heading reference build failure")))))

(defn- verify-unpositioned-diagnostic! [root]
  (let [project (.join path root "missing-config")
        config-path (.join path project "clono.config.mjs")]
    (.mkdirSync fs project)
    (let [result (run-cli ["build" project] root)]
      (ensure! (= 1 (.-status result))
               "Release build command did not fail without a config file")
      (ensure! (= "" (.-stdout result))
               "Config failure wrote to stdout")
      (ensure! (= (str config-path
                       ": 書籍プロジェクトのルート直下に`clono.config.mjs`がありません。\n")
                  (.-stderr result))
               (str "Unpositioned diagnostic was incorrectly formatted: "
                    (.-stderr result))))))

(defn main []
  (let [root (.mkdtempSync fs (.join path (.tmpdir os)
                                      "clono-cli-integration-"))]
    (try
      (verify-transform! root)
      (verify-heading-transform! root)
      (verify-listing-transform! root)
      (verify-build! root)
      (verify-diagnostics! root)
      (verify-reference-build! root)
      (verify-table-reference-build! root)
      (verify-listing-reference-build! root)
      (verify-heading-reference-build! root)
      (verify-unpositioned-diagnostic! root)
      (finally
        (.rmSync fs root #js {:recursive true :force true})))))
