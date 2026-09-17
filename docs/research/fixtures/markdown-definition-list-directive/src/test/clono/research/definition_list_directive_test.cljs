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

(deftest definition-list-directive-ast-test
  (let [tree (definition-list-directive/parse (read-input))
        lists (vec (definition-list-directive/definition-list-directives tree))
        definitions (vec (definition-list-directive/definition-directives
                          (first lists)))
        ready (first definitions)
        done (second definitions)]
    (testing "When a definition list contains multiple definitions, then each term and description has an explicit AST boundary"
      (is (= 1 (count lists)))
      (is (= 2 (count definitions)))
      (is (= ["inlineCode"]
             (mapv #(.-type %)
                   (definition-list-directive/children
                    (definition-list-directive/label-node ready)))))
      (is (= "READY"
             (.-value
              (first
               (definition-list-directive/children
                (definition-list-directive/label-node ready))))))
      (is (= ["text"]
             (mapv #(.-type %)
                   (definition-list-directive/children
                    (definition-list-directive/label-node done)))))
      (is (= ["paragraph"]
             (mapv #(.-type %)
                   (definition-list-directive/body-children ready))))
      (is (= ["paragraph"]
             (mapv #(.-type %)
                   (definition-list-directive/body-children done)))))))

(deftest definition-list-transformation-test
  (let [output (definition-list-directive/transformed-markdown (read-input))
        reparsed (definition-list-directive/parse output)]
    (testing "When a definition list is transformed, then VFM-compatible Markdown contains one list with two grouped items"
      (is (= 1 (count (re-seq #"<dl class=\"clono-definition-list\">" output))))
      (is (= 2 (count (re-seq #"<div class=\"clono-definition-item\">" output))))
      (is (.includes output "<dt><code>READY</code></dt>"))
      (is (.includes output "<dt>DONE</dt>"))
      (is (empty?
           (definition-list-directive/definition-list-directives reparsed))))))

(deftest vfm-integration-test
  (let [markdown (definition-list-directive/transformed-markdown (read-input))
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

    (testing "When descriptions contain inline Markdown, then VFM preserves code, emphasis, and links inside their definition items"
      (is (= "待機状態" (.-textContent (.querySelector ready "dd strong"))))
      (is (= "https://example.com/state"
             (.getAttribute (.querySelector ready "dd a") "href")))
      (is (= "0" (.-textContent (.querySelector done "dd code")))))))

