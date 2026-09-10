(ns clono.research.listing-directive-test
  (:require
   ["@vivliostyle/vfm" :refer [stringify]]
   ["node:fs" :as fs]
   ["node-html-parser" :refer [parse]]
   [cljs.test :refer [deftest is testing]]
   [clono.research.listing-directive :as listing-directive]
   [goog.object :as gobj]))

(defn read-input []
  (.readFileSync fs "input/numbered-listings.md" "utf8"))

(defn nodes-by-type [tree type]
  (filter #(= type (.-type %)) (listing-directive/nodes tree)))

(defn query-all [root selector]
  (array-seq (.querySelectorAll root selector)))

(deftest listing-directive-ast-test
  (let [tree (listing-directive/parse (read-input))
        directives (vec (listing-directive/listing-directives tree))
        greeting (first directives)
        settings (second directives)
        greeting-code (first (listing-directive/body-children greeting))
        settings-code (first (listing-directive/body-children settings))]
    (testing "When listing directives contain fenced code, then their labels, identifiers, and code remain inspectable"
      (is (= 2 (count directives)))
      (is (= "greeting" (gobj/get (.-attributes greeting) "id")))
      (is (= "A & \"B\" <C>"
             (listing-directive/node-text
              (listing-directive/label-node greeting))))
      (is (= ["code"]
             (mapv #(.-type %) (listing-directive/body-children greeting))))
      (is (= "kotlin" (.-lang greeting-code)))
      (is (nil? (gobj/get greeting-code "meta")))
      (is (.includes (.-value greeting-code) ":xref[ignored]"))
      (is (= "settings" (gobj/get (.-attributes settings) "id")))
      (is (= "設定例"
             (listing-directive/node-text
              (listing-directive/label-node settings))))
      (is (nil? (.-lang settings-code)))
      (is (nil? (gobj/get settings-code "meta")))
      (is (.includes (.-value settings-code)
                     ":::listing[not-a-directive]{#ignored}")))

    (testing "When directive-like text appears inside fenced code, then it does not create another directive node"
      (is (= 2 (count (nodes-by-type tree "containerDirective")))))))

(deftest code-fence-meta-test
  (let [source (str ":::listing[Main function]{#main}\n\n"
                    "```kotlin title=Main.kt\n"
                    "fun main() = Unit\n"
                    "```\n\n"
                    ":::\n")
        directive (first (listing-directive/listing-directives
                          (listing-directive/parse source)))
        code (first (listing-directive/body-children directive))]
    (testing "When fenced code has metadata, then the language and metadata are independently inspectable"
      (is (= "kotlin" (.-lang code)))
      (is (= "title=Main.kt" (gobj/get code "meta"))))))

(deftest listing-directive-transformation-test
  (let [output (listing-directive/transformed-markdown (read-input))
        reparsed (listing-directive/parse output)
        code-nodes (vec (nodes-by-type reparsed "code"))]
    (testing "When listing directives are transformed, then VFM-compatible Markdown contains numbered listing boundaries"
      (is (.includes output
                     "<figure class=\"clono-numbered-listing\" id=\"listing-greeting\">"))
      (is (.includes output
                     (str "<figcaption class=\"clono-listing-caption\" "
                          "id=\"listing-greeting-caption\">"
                          "A &amp; &quot;B&quot; &lt;C&gt;</figcaption>")))
      (is (.includes output
                     "<figure class=\"clono-numbered-listing\" id=\"listing-settings\">"))
      (is (empty? (listing-directive/listing-directives reparsed))))

    (testing "When transformed Markdown is reparsed, then code languages and contents remain unchanged"
      (is (= 2 (count code-nodes)))
      (is (= "kotlin" (.-lang (first code-nodes))))
      (is (nil? (.-lang (second code-nodes))))
      (is (.includes (.-value (first code-nodes)) ":xref[ignored]"))
      (is (.includes (.-value (second code-nodes))
                     ":::listing[not-a-directive]{#ignored}")))))

(deftest vfm-integration-test
  (let [markdown (listing-directive/transformed-markdown (read-input))
        html (stringify markdown #js {:partial true})
        document (parse html)
        figures (vec (query-all document "figure.clono-numbered-listing"))
        greeting (.querySelector document "figure#listing-greeting")
        settings (.querySelector document "figure#listing-settings")
        greeting-caption (.querySelector greeting "figcaption#listing-greeting-caption")
        greeting-html (.-innerHTML greeting)
        settings-html (.-innerHTML settings)]
    (testing "When transformed Markdown is processed by VFM, then each listing has an upper caption and one code block"
      (is (= 2 (count figures)))
      (is (= "A & \"B\" <C>" (.-textContent greeting-caption)))
      (is (= 1 (count (query-all greeting ":scope > figcaption"))))
      (is (= 1 (count (query-all greeting ":scope > pre"))))
      (is (< (.indexOf (.-innerHTML greeting) "<figcaption")
             (.indexOf (.-innerHTML greeting) "<pre")))
      (is (= "設定例"
             (.-textContent
              (.querySelector settings "figcaption#listing-settings-caption"))))
      (is (= 1 (count (query-all settings ":scope > pre")))))

    (testing "When VFM renders listing code, then optional language and literal directive-like text remain observable"
      (is (re-find #"<pre class=\"language-kotlin\"><code class=\"language-kotlin\">"
                   greeting-html))
      (is (re-find #"<span class=\"token keyword\">fun</span>"
                   greeting-html))
      (is (.includes greeting-html ":xref[ignored]"))
      (is (re-find #"<pre class=\"language-text\"><code class=\"language-text\">"
                   settings-html))
      (is (.includes settings-html
                     ":::listing[not-a-directive]{#ignored}")))))
