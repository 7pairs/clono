(ns clono.research.transformer-test
  (:require
   ["@vivliostyle/vfm" :refer [stringify]]
   ["node:fs" :as fs]
   ["node-html-parser" :refer [parse]]
   [cljs.test :refer [deftest is testing]]
   [clono.research.markdown-ast :as markdown-ast]
   [clono.research.transformer :as transformer]
   [goog.object :as gobj]))

(defn read-input [name]
  (.readFileSync fs (str "input/" name ".md") "utf8"))

(defn nodes-by-type [tree type]
  (filter #(= type (.-type %)) (transformer/nodes tree)))

(defn directive-names [tree]
  (->> (transformer/nodes tree)
       (keep #(.-name %))
       vec))

(defn node-text [node]
  (if (string? (.-value node))
    (.-value node)
    (apply str (map node-text (transformer/children node)))))

(defn query-all [root selector]
  (array-seq (.querySelectorAll root selector)))

(deftest successful-transformation-test
  (let [result (transformer/transform (read-input "valid") "input/valid.md")
        output (:output result)
        reparsed (markdown-ast/parse output)]
    (testing "When valid known directives are transformed, then VFM-compatible Markdown is returned without diagnostics"
      (is (true? (:ok? result)))
      (is (string? output))
      (is (empty? (:diagnostics result)))
      (is (.includes output "<div class=\"text-align-right\">"))
      (is (.includes output "<div class=\"page-break\" aria-hidden=\"true\"></div>"))
      (is (.includes output "<span class=\"index-marker\" data-index-reading=\"さくいんこうもく\">索引項目</span>")))

    (testing "When transformed Markdown is reparsed, then known directives are replaced and an unknown directive is semantically preserved"
      (is (= ["third-party"] (directive-names reparsed)))
      (let [unknown (first (filter #(= "third-party" (.-name %))
                                   (transformer/nodes reparsed)))]
        (is (= "sample" (-> unknown .-attributes (gobj/get "mode"))))
        (is (= "clonoが知らない記法です。" (node-text unknown))))
      (is (= 5 (count (nodes-by-type reparsed "html")))))

    (testing "When transformed Markdown is reparsed, then standard Markdown, code, and footnotes retain their structure"
      (is (= 1 (count (nodes-by-type reparsed "heading"))))
      (is (= 1 (count (nodes-by-type reparsed "strong"))))
      (is (= 2 (count (nodes-by-type reparsed "link"))))
      (is (= 1 (count (nodes-by-type reparsed "code"))))
      (is (= 1 (count (nodes-by-type reparsed "footnoteReference"))))
      (is (= 1 (count (nodes-by-type reparsed "footnoteDefinition"))))
      (is (.includes (.-value (first (nodes-by-type reparsed "code")))
                     ":index[コード内]{reading=\"こおとない\"}")))))

(deftest invalid-transformation-test
  (let [result (transformer/transform (read-input "invalid") "input/invalid.md")]
    (testing "When known directives have invalid semantics, then the whole transformation fails without output"
      (is (false? (:ok? result)))
      (is (nil? (:output result)))
      (is (= [{:file "input/invalid.md"
               :line 3
               :column 1
               :directive "align"
               :message "`align`には`position=\"right\"`が必要です。"}
              {:file "input/invalid.md"
               :line 7
               :column 1
               :directive "page-break"
               :message "`page-break`には属性を指定できません。"}
              {:file "input/invalid.md"
               :line 9
               :column 4
               :directive "index"
               :message "`index`には空でない`reading`属性が必要です。"}
              {:file "input/invalid.md"
               :line 11
               :column 4
               :directive "align"
               :message "`align`は`containerDirective`として記述する必要があります。"}]
             (:diagnostics result))))

    (testing "When an invalid document also contains an unknown directive, then no diagnostic is added for the unknown directive"
      (is (= ["align" "page-break" "index" "align"]
             (mapv :directive (:diagnostics result)))))))

(deftest dynamic-html-attribute-test
  (let [result (transformer/transform
                "これは:index[安全な索引]{reading=\"A&B\"}です。"
                "attribute.md")
        html (stringify (:output result) #js {:partial true})
        document (parse html)
        index-marker (.querySelector document "span.index-marker")]
    (testing "When a directive attribute contains an HTML metacharacter, then generated Markdown escapes it and VFM preserves its value"
      (is (true? (:ok? result)))
      (is (.includes (:output result) "data-index-reading=\"A&amp;B\""))
      (is (= "A&B" (.getAttribute index-marker "data-index-reading"))))))

(deftest vfm-integration-test
  (let [result (transformer/transform (read-input "valid") "input/valid.md")
        html (stringify (:output result) #js {:partial true :footnote "dpub"})
        document (parse html #js {:blockTextElements #js {}})
        align (.querySelector document "div.text-align-right")
        page-break (.querySelector document "div.page-break")
        index-marker (.querySelector document "span.index-marker")
        footnote-reference (.querySelector document "a[role=doc-noteref]")
        footnote-body (.querySelector document "aside[role=doc-footnote]")
        code (.querySelector document "pre.language-markdown code")
        unknown-paragraph (first (filter #(.includes (.-textContent %)
                                                   ":::third-party")
                                         (query-all document "p")))]
    (testing "When transformed Markdown is processed by VFM, then standard Markdown and transformed HTML structures are preserved"
      (is (= "AST変換と出力方式の検証"
             (.-textContent (.querySelector document "h1"))))
      (is (= 1 (count (query-all document "p > strong"))))
      (is (= "https://example.com/"
             (.getAttribute (.querySelector document "p > a") "href")))
      (is (= 2 (count (query-all align "p"))))
      (is (= "インラインコード"
             (.-textContent (.querySelector align "code"))))
      (is (= "true" (.getAttribute page-break "aria-hidden")))
      (is (= "さくいんこうもく"
             (.getAttribute index-marker "data-index-reading")))
      (is (= "索引項目" (.-textContent index-marker))))

    (testing "When an unknown directive reaches VFM, then VFM renders its syntax and content as ordinary text"
      (is (some? unknown-paragraph))
      (is (.includes (.-textContent unknown-paragraph)
                     "clonoが知らない記法です。")))

    (testing "When transformed Markdown contains a VFM footnote, then its reference, body, inline code, and link reach VFM HTML"
      (is (some? footnote-reference))
      (is (some? (.querySelector align "a[role=doc-noteref]")))
      (is (= "変数名"
             (.-textContent (.querySelector footnote-body "code"))))
      (is (= "https://docs.vivliostyle.org/ja/vfm/"
             (.getAttribute (.querySelector footnote-body "a:not([role=doc-backlink])")
                            "href"))))

    (testing "When directive-like text is inside a code fence, then VFM renders it as code without applying clono transformations"
      (is (some? code))
      (is (.includes (.-textContent code) ":::align{position=\"right\"}"))
      (is (.includes (.-textContent code) ":index[コード内]{reading=\"こおとない\"}")))))
