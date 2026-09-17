(ns clono.research.definition-list-directive-test
  (:require
   ["@vivliostyle/vfm" :refer [stringify]]
   ["node:fs" :as fs]
   ["node-html-parser" :refer [parse]]
   [cljs.test :refer [deftest is testing]]
   [clono.research.definition-list-directive :as definition-list-directive]))

(defn read-input []
  (.readFileSync fs "input/definition-list.md" "utf8"))

(defn query-all [root selector]
  (array-seq (.querySelectorAll root selector)))

(defn transformed-output [source source-name]
  (:output (definition-list-directive/transform-markdown source source-name)))

(deftest definition-list-directive-ast-test
  (let [tree (definition-list-directive/parse (read-input))
        lists (vec (definition-list-directive/definition-list-directives tree))
        definitions (vec (definition-list-directive/definition-directives
                          (first lists)))
        ready (first definitions)
        done (second definitions)
        ready-terms (vec (definition-list-directive/term-directives ready))
        done-terms (vec (definition-list-directive/term-directives done))]
    (testing "When a definition list contains multiple definitions, then each term and description has an explicit AST boundary"
      (is (= 1 (count lists)))
      (is (= 2 (count definitions)))
      (is (= 1 (count ready-terms)))
      (is (= 1 (count done-terms)))
      (is (= ["inlineCode"]
             (mapv #(.-type %)
                   (definition-list-directive/children (first ready-terms)))))
      (is (= "READY"
             (.-value
              (first
               (definition-list-directive/children (first ready-terms))))))
      (is (= ["text"]
             (mapv #(.-type %)
                   (definition-list-directive/children (first done-terms)))))
      (is (= ["paragraph"]
             (mapv #(.-type %)
                   (definition-list-directive/body-children ready))))
      (is (= ["paragraph"]
             (mapv #(.-type %)
                   (definition-list-directive/body-children done)))))))

(deftest multiple-term-extension-test
  (let [source (str "::::definition-list\n"
                    ":::definition\n"
                    "::term[一塁手]\n"
                    "::term[二塁手]\n\n"
                    "内野手です。\n"
                    ":::\n"
                    "::::\n")
        markdown (transformed-output source "input/multiple-terms.md")
        html (stringify markdown #js {:partial true})
        document (parse html)
        item (.querySelector document "div.clono-definition-item")]
    (testing "When one definition contains multiple term directives, then every term shares one generated description"
      (is (= ["一塁手" "二塁手"]
             (mapv #(.-textContent %)
                   (query-all item ":scope > dt"))))
      (is (= 1 (count (query-all item ":scope > dd"))))
      (is (= "内野手です。"
             (.-textContent (.querySelector item "dd > p")))))))

(deftest definition-list-transformation-test
  (let [result (definition-list-directive/transform-markdown
                (read-input)
                "input/definition-list.md")
        output (:output result)
        reparsed (definition-list-directive/parse output)]
    (testing "When a definition list is transformed, then VFM-compatible Markdown contains one list with two grouped items"
      (is (true? (:ok? result)))
      (is (empty? (:diagnostics result)))
      (is (= 1 (count (re-seq #"<dl class=\"clono-definition-list\">" output))))
      (is (= 2 (count (re-seq #"<div class=\"clono-definition-item\">" output))))
      (is (.includes output "<dt><code>READY</code></dt>"))
      (is (.includes output "<dt>DONE</dt>"))
      (is (empty?
           (definition-list-directive/definition-list-directives reparsed))))))

(deftest invalid-term-label-test
  (let [source (str "::::definition-list\n"
                    ":::definition\n"
                    "::term[[READY](https://example.com/state)]\n\n"
                    "処理を開始できる状態。\n"
                    ":::"
                    "\n::::\n")
        result (definition-list-directive/transform-markdown
                source
                "input/invalid-term.md")
        empty-source (str "::::definition-list\n"
                          ":::definition\n"
                          "::term[]\n\n"
                          "用語のない説明。\n"
                          ":::\n"
                          "::::\n")
        empty-result (definition-list-directive/transform-markdown
                      empty-source
                      "input/empty-term.md")]
    (testing "When a term label contains a link, then transformation fails with a diagnostic and no output"
      (is (false? (:ok? result)))
      (is (nil? (:output result)))
      (is (= [{:file "input/invalid-term.md"
               :line 3
               :column 1
               :directive "term"
               :message "`term`のラベルには通常テキストとインラインコードだけを指定できます。"}]
             (:diagnostics result))))

    (testing "When a term label is empty, then transformation fails with a diagnostic and no output"
      (is (false? (:ok? empty-result)))
      (is (nil? (:output empty-result)))
      (is (= [{:file "input/empty-term.md"
               :line 3
               :column 1
               :directive "term"
               :message "`term`には空でないラベルが必要です。"}]
             (:diagnostics empty-result))))))

(deftest vfm-integration-test
  (let [markdown (transformed-output
                  (read-input)
                  "input/definition-list.md")
        html (stringify markdown #js {:partial true})
        document (parse html)
        list (.querySelector document "dl.clono-definition-list")
        items (vec (query-all list ":scope > div.clono-definition-item"))
        ready (first items)
        done (second items)]
    (testing "When transformed Markdown is processed by VFM, then definition semantics and item grouping remain observable"
      (is (= 1 (count (query-all document "dl.clono-definition-list"))))
      (is (= 2 (count items)))
      (is (= 1 (count (query-all ready ":scope > dt"))))
      (is (= 1 (count (query-all ready ":scope > dd"))))
      (is (= 1 (count (query-all done ":scope > dt"))))
      (is (= 1 (count (query-all done ":scope > dd"))))
      (is (= "READY" (.-textContent (.querySelector ready "dt > code"))))
      (is (= "DONE" (.-textContent (.querySelector done "dt")))))

    (testing "When descriptions contain inline Markdown, then VFM preserves code, strong emphasis, and links inside their definition items"
      (is (= "待機状態" (.-textContent (.querySelector ready "dd strong"))))
      (is (= "https://example.com/state"
             (.getAttribute (.querySelector ready "dd a") "href")))
      (is (= "0" (.-textContent (.querySelector done "dd code")))))))
