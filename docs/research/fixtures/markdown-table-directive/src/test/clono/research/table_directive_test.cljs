(ns clono.research.table-directive-test
  (:require
   ["@vivliostyle/vfm" :refer [stringify]]
   ["node:fs" :as fs]
   ["node-html-parser" :refer [parse]]
   [cljs.test :refer [deftest is testing]]
   [clono.research.table-directive :as table-directive]
   [goog.object :as gobj]))

(defn read-input []
  (.readFileSync fs "input/numbered-table.md" "utf8"))

(defn child-types [node]
  (mapv #(.-type %) (table-directive/children node)))

(defn nodes-by-type [tree type]
  (filter #(= type (.-type %)) (table-directive/nodes tree)))

(defn query-all [root selector]
  (array-seq (.querySelectorAll root selector)))

(deftest table-directive-ast-test
  (let [tree (table-directive/parse (read-input))
        directive (table-directive/directive-node tree)
        label (table-directive/label-node directive)
        body (vec (table-directive/body-children directive))
        table (first body)]
    (testing "When a table directive contains one GFM table, then its label and table remain distinct children"
      (is (some? directive))
      (is (= "runtime" (gobj/get (.-attributes directive) "id")))
      (is (= "実行環境" (table-directive/node-text label)))
      (is (= ["table"] (mapv #(.-type %) body)))
      (is (= 3 (count (table-directive/children table))))
      (is (= ["left" "center" "right"]
             (vec (.-align table)))))

    (testing "When inline Markdown appears in table cells, then its semantic nodes remain inspectable"
      (is (= 1 (count (nodes-by-type table "inlineCode"))))
      (is (= 1 (count (nodes-by-type table "strong"))))
      (is (= 1 (count (nodes-by-type table "link"))))
      (is (= 1 (count (nodes-by-type table "emphasis"))))
      (is (empty? (nodes-by-type table "delete")))
      (is (some #(= "、~~旧形式ではない~~" (.-value %))
                (nodes-by-type table "text"))))))

(deftest table-directive-transformation-test
  (let [source (read-input)
        output (table-directive/transformed-markdown source)
        reparsed (table-directive/parse output)]
    (testing "When a table directive is transformed, then VFM-compatible Markdown contains the table between its figure boundaries"
      (is (.includes output
                     "<figure class=\"clono-numbered-table\" id=\"table-runtime\">"))
      (is (.includes output "`ClojureScript`"))
      (is (.includes output
                     (str "<figcaption class=\"clono-table-caption\" "
                          "id=\"table-runtime-caption\">実行環境</figcaption>")))
      (is (= ["html" "table" "html"] (child-types reparsed)))
      (is (nil? (table-directive/directive-node reparsed))))

    (testing "When transformed Markdown is reparsed, then table alignment and inline semantics remain unchanged"
      (let [table (first (nodes-by-type reparsed "table"))]
        (is (= ["left" "center" "right"]
               (vec (.-align table))))
        (is (= 1 (count (nodes-by-type table "inlineCode"))))
        (is (= 1 (count (nodes-by-type table "strong"))))
        (is (= 1 (count (nodes-by-type table "link"))))
        (is (= 1 (count (nodes-by-type table "emphasis"))))
        (is (empty? (nodes-by-type table "delete")))
        (is (some #(= "、~~旧形式ではない~~" (.-value %))
                  (nodes-by-type table "text")))))))

(deftest vfm-integration-test
  (let [markdown (table-directive/transformed-markdown (read-input))
        html (stringify markdown #js {:partial true})
        document (parse html)
        figure (.querySelector document "figure.clono-numbered-table#table-runtime")
        table (.querySelector figure "table")
        caption (.querySelector figure "figcaption.clono-table-caption#table-runtime-caption")
        cells (query-all table "tbody td")]
    (testing "When transformed Markdown is processed by VFM, then the numbered table structure and lower caption are preserved"
      (is (some? figure))
      (is (= 1 (count (query-all figure ":scope > table"))))
      (is (= "実行環境" (.-textContent caption)))
      (is (< (.indexOf (.-innerHTML figure) "<table")
             (.indexOf (.-innerHTML figure) "<figcaption"))))

    (testing "When VFM renders the table, then alignment and inline Markdown remain observable in HTML"
      (is (= "left" (.getAttribute (nth cells 0) "align")))
      (is (= "center" (.getAttribute (nth cells 1) "align")))
      (is (= "right" (.getAttribute (nth cells 2) "align")))
      (is (= "ClojureScript"
             (.-textContent (.querySelector table "code"))))
      (is (= "必須"
             (.-textContent (.querySelector table "strong"))))
      (is (= "https://vivliostyle.github.io/vfm/"
             (.getAttribute (.querySelector table "a") "href")))
      (is (= "推奨"
             (.-textContent (.querySelector table "em"))))
      (is (= "旧形式ではない"
             (.-textContent (.querySelector table "del")))))))
