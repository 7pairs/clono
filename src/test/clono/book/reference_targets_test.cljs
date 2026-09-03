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
  {:context (context source-name)
   :tree (markdown/parse source)})

(defn- figure [id caption]
  (str ":::figure[" caption "]{#" id "}\n"
       "![図](./image.svg)\n"
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
             (:diagnostics result))))))
