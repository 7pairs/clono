(ns clono.book.reference-targets-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.book.reference-targets :as reference-targets]
   [clono.markdown :as markdown]))

(defn- context [source-name]
  {:mode :build
   :source-name source-name
   :publication-entry {:type :document
                       :path source-name
                       :kind "chapter"
                       :include-in-toc true}})

(defn- manuscript [source-name source]
  {:context (assoc (context source-name) :source source)
   :tree (markdown/parse source)})

(defn- figure [id caption]
  (str ":::figure[" caption "]{#" id "}\n"
       "![図](./image.svg)\n"
       ":::\n"))

(defn- table [id caption]
  (str ":::table[" caption "]{#" id "}\n"
       "| 項目 |\n"
       "| --- |\n"
       "| 値 |\n"
       ":::\n"))

(deftest book-reference-target-collection-test
  (testing "When published manuscripts contain figures, then their reference targets are collected in manuscript and source order"
    (let [result
          (reference-targets/collect
           [(manuscript
             "chapter-one.md"
             (str (figure "overview" "概要")
                  "\n"
                  (figure "details" "詳細")))
            (manuscript
             "chapter-two.md"
             (figure "workflow" "処理の流れ"))])]
      (is (:ok? result))
      (is (empty? (:diagnostics result)))
      (is (= [{:logical-id "overview"
               :type "figure"
               :target-id "figure-overview"
               :title-target-id "figure-overview-caption"
               :numbered? true
               :source-name "chapter-one.md"
               :line 1
               :column 1}
              {:logical-id "details"
               :type "figure"
               :target-id "figure-details"
               :title-target-id "figure-details-caption"
               :numbered? true
               :source-name "chapter-one.md"
               :line 5
               :column 1}
              {:logical-id "workflow"
               :type "figure"
               :target-id "figure-workflow"
               :title-target-id "figure-workflow-caption"
               :numbered? true
               :source-name "chapter-two.md"
               :line 1
               :column 1}]
             (:targets result)))))

  (testing "When published manuscripts contain no reference targets, then an empty successful collection is returned"
    (let [result
          (reference-targets/collect
           [(manuscript "preface.md" "# はじめに\n")])]
      (is (:ok? result))
      (is (= [] (:targets result)))
      (is (empty? (:diagnostics result))))))

(deftest book-heading-reference-target-collection-test
  (testing "When published manuscripts contain eligible headings, then their document metadata is retained in the collected targets"
    (let [chapter-source "# 本文 {#chapter-heading}\n"
          appendix-source "## 付録の節 {#appendix-section}\n"
          result
          (reference-targets/collect
           [(manuscript "chapter.md" chapter-source)
            {:context (assoc (context "appendix.md")
                             :source appendix-source
                             :publication-entry
                             {:type :document
                              :path "appendix.md"
                              :kind "appendix"
                              :include-in-toc true})
             :tree (markdown/parse appendix-source)}])]
      (is (:ok? result))
      (is (= [{:logical-id "chapter-heading"
               :type "heading"
               :target-id "chapter-heading"
               :title-target-id "chapter-heading"
               :numbered? true
               :heading-depth 1
               :document-kind "chapter"
               :source-name "chapter.md"
               :line 1
               :column 1}
              {:logical-id "appendix-section"
               :type "heading"
               :target-id "appendix-section"
               :title-target-id "appendix-section"
               :numbered? true
               :heading-depth 2
               :document-kind "appendix"
               :source-name "appendix.md"
               :line 1
               :column 1}]
             (:targets result))))))

(deftest book-table-reference-target-collection-test
  (testing "When published manuscripts contain tables alongside existing target types, then every numbered target is collected in manuscript and source order"
    (let [result
          (reference-targets/collect
           [(manuscript
             "chapter-one.md"
             (str "# 概要 {#overview}\n\n"
                  (table "runtime" "実行環境")
                  "\n"
                  "| 番号なし |\n"
                  "| --- |\n"
                  "| 対象外 |\n"))
            (manuscript
             "chapter-two.md"
             (str (figure "architecture" "構成図")
                  "\n"
                  (table "commands" "実行コマンド")))])]
      (is (:ok? result))
      (is (empty? (:diagnostics result)))
      (is (= [{:logical-id "overview"
               :type "heading"
               :target-id "overview"
               :title-target-id "overview"
               :numbered? true
               :heading-depth 1
               :document-kind "chapter"
               :source-name "chapter-one.md"
               :line 1
               :column 1}
              {:logical-id "runtime"
               :type "table"
               :target-id "table-runtime"
               :title-target-id "table-runtime-caption"
               :numbered? true
               :source-name "chapter-one.md"
               :line 3
               :column 1}
              {:logical-id "architecture"
               :type "figure"
               :target-id "figure-architecture"
               :title-target-id "figure-architecture-caption"
               :numbered? true
               :source-name "chapter-two.md"
               :line 1
               :column 1}
              {:logical-id "commands"
               :type "table"
               :target-id "table-commands"
               :title-target-id "table-commands-caption"
               :numbered? true
               :source-name "chapter-two.md"
               :line 5
               :column 1}]
             (:targets result))))))

(deftest book-reference-target-duplicate-test
  (testing "When a logical ID is repeated across published manuscripts, then the later target is diagnosed and no ambiguous collection is returned"
    (let [result
          (reference-targets/collect
           [(manuscript "chapter-one.md" (figure "diagram" "最初の図"))
            (manuscript "chapter-two.md" (figure "diagram" "次の図"))
            (manuscript "chapter-three.md" (figure "diagram" "最後の図"))])]
      (is (false? (:ok? result)))
      (is (nil? (:targets result)))
      (is (= [{:file "chapter-two.md"
               :line 1
               :column 1
               :directive "figure"
               :message "`figure`の論理ID`diagram`が重複しています。"}
              {:file "chapter-three.md"
               :line 1
               :column 1
               :directive "figure"
               :message "`figure`の論理ID`diagram`が重複しています。"}]
             (:diagnostics result)))))

  (testing "When generated HTML IDs collide across published manuscripts, then the later target is diagnosed and no ambiguous collection is returned"
    (let [result
          (reference-targets/collect
           [(manuscript "chapter-one.md" (figure "diagram" "最初の図"))
            (manuscript
             "chapter-two.md"
             (figure "diagram-caption" "衝突する図"))])]
      (is (false? (:ok? result)))
      (is (nil? (:targets result)))
      (is (= [{:file "chapter-two.md"
               :line 1
               :column 1
               :directive "figure"
               :message (str "`figure`から生成するHTML ID`"
                             "figure-diagram-caption`が重複しています。")}]
             (:diagnostics result)))))

  (testing "When a figure repeats a heading logical ID from another manuscript, then the later figure is diagnosed"
    (let [result
          (reference-targets/collect
           [(manuscript "chapter-one.md"
                        "# 構成 {#architecture}\n")
            (manuscript "chapter-two.md"
                        (figure "architecture" "全体構成"))])]
      (is (false? (:ok? result)))
      (is (nil? (:targets result)))
      (is (= [{:file "chapter-two.md"
               :line 1
               :column 1
               :directive "figure"
               :message "`figure`の論理ID`architecture`が重複しています。"}]
             (:diagnostics result)))))

  (testing "When a heading HTML ID collides with a figure caption ID from another manuscript, then the later heading is diagnosed without a directive name"
    (let [result
          (reference-targets/collect
           [(manuscript "chapter-one.md"
                        (figure "architecture" "全体構成"))
            (manuscript
             "chapter-two.md"
             "# 衝突する見出し {#figure-architecture-caption}\n")])]
      (is (false? (:ok? result)))
      (is (nil? (:targets result)))
      (is (= [{:file "chapter-two.md"
               :line 1
               :column 1
               :message (str "見出しのHTML ID`figure-architecture-caption"
                             "`が重複しています。")}]
             (:diagnostics result)))))

  (testing "When a table repeats a figure logical ID from another manuscript, then the later table is diagnosed in the shared namespace"
    (let [result
          (reference-targets/collect
           [(manuscript "chapter-one.md"
                        (figure "architecture" "構成図"))
            (manuscript "chapter-two.md"
                        (table "architecture" "構成表"))])]
      (is (false? (:ok? result)))
      (is (nil? (:targets result)))
      (is (= [{:file "chapter-two.md"
               :line 1
               :column 1
               :directive "table"
               :message "`table`の論理ID`architecture`が重複しています。"}]
             (:diagnostics result)))))

  (testing "When a heading HTML ID collides with a table caption ID from another manuscript, then the later heading is diagnosed without a directive name"
    (let [result
          (reference-targets/collect
           [(manuscript "chapter-one.md"
                        (table "runtime" "実行環境"))
            (manuscript
             "chapter-two.md"
             "# 衝突する見出し {#table-runtime-caption}\n")])]
      (is (false? (:ok? result)))
      (is (nil? (:targets result)))
      (is (= [{:file "chapter-two.md"
               :line 1
               :column 1
               :message (str "見出しのHTML ID`table-runtime-caption"
                             "`が重複しています。")}]
             (:diagnostics result))))))
