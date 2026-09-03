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
      (verify-build! root)
      (verify-diagnostics! root)
      (verify-reference-build! root)
      (verify-unpositioned-diagnostic! root)
      (finally
        (.rmSync fs root #js {:recursive true :force true})))))
