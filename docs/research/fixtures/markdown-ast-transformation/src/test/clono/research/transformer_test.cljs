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

    (testing "When transformed Markdown is reparsed, then all known directives are replaced"
      (is (empty? (directive-names reparsed)))
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
               :message "`align`は`containerDirective`として記述する必要があります。"}
              {:file "input/invalid.md"
               :line 13
               :column 1
               :directive "third-party"
               :message "`third-party`は登録されていないdirectiveです。"}]
             (:diagnostics result))))

    (testing "When an invalid document also contains an unknown directive, then all independent diagnostics remain in source order"
      (is (= ["align" "page-break" "index" "align" "third-party"]
             (mapv :directive (:diagnostics result)))))))

(deftest unknown-directive-test
  (let [result (transformer/transform
                (read-input "unknown-nested")
                "input/unknown-nested.md")]
    (testing "When an unknown container has an invalid known descendant, then the container is reported without traversing its descendants"
      (is (false? (:ok? result)))
      (is (nil? (:output result)))
      (is (= [{:file "input/unknown-nested.md"
               :line 1
               :column 1
               :directive "third-party"
               :message "`third-party`は登録されていないdirectiveです。"}
              {:file "input/unknown-nested.md"
               :line 5
               :column 1
               :directive "index"
               :message "`index`には空でない`reading`属性が必要です。"}
              {:file "input/unknown-nested.md"
               :line 7
               :column 1
               :directive "another-extension"
               :message "`another-extension`は登録されていないdirectiveです。"}]
             (:diagnostics result))))))

(deftest unknown-preservation-capability-test
  (let [markdown ":::third-party{mode=\"sample\"}\nclonoが知らない記法です。\n:::\n"
        output (-> markdown markdown-ast/parse markdown-ast/serialize)
        reparsed (markdown-ast/parse output)
        unknown (first (filter #(= "third-party" (.-name %))
                               (transformer/nodes reparsed)))
        html (stringify output #js {:partial true})]
    (testing "When an unknown directive bypasses the adopted validation policy, then the low-level parser can preserve its meaning"
      (is (= "sample" (-> unknown .-attributes (gobj/get "mode"))))
      (is (= "clonoが知らない記法です。" (node-text unknown)))
      (is (.includes html ":::third-party")))))

(deftest dynamic-html-attribute-test
  (let [dangerous-value "A&B\"><img src=x onerror=alert(1)>"
        result (transformer/transform
                "これは:index[安全な索引]{reading='A&B\"><img src=x onerror=alert(1)>'}です。"
                "attribute.md")
        html (stringify (:output result) #js {:partial true})
        document (parse html)
        index-marker (.querySelector document "span.index-marker")]
    (testing "When a directive attribute attempts to escape its HTML attribute, then generated Markdown contains only the allowed structure"
      (is (true? (:ok? result)))
      (is (.includes (:output result)
                     "data-index-reading=\"A&amp;B&quot;&gt;&lt;img src=x onerror=alert(1)&gt;\""))
      (is (= dangerous-value
             (.getAttribute index-marker "data-index-reading")))
      (is (nil? (.getAttribute index-marker "onerror")))
      (is (nil? (.querySelector document "img")))
      (is (= 1 (count (query-all document "span.index-marker")))))))

(deftest vfm-integration-test
  (let [result (transformer/transform (read-input "valid") "input/valid.md")
        html (stringify (:output result) #js {:partial true :footnote "dpub"})
        document (parse html #js {:blockTextElements #js {}})
        align (.querySelector document "div.text-align-right")
        page-break (.querySelector document "div.page-break")
        index-marker (.querySelector document "span.index-marker")
        footnote-reference (.querySelector document "a[role=doc-noteref]")
        footnote-body (.querySelector document "aside[role=doc-footnote]")
        code (.querySelector document "pre.language-markdown code")]
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
