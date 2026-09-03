(ns clono.pipeline-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.markdown :as markdown]
   [clono.pipeline :as pipeline]
   [clono.test-support :as test-support]
   [clono.transform :as transform]))

(deftest run-test
  (let [result (pipeline/run {:mode :transform
                              :source-name "manuscript.md"
                              :input-path "/work/manuscript.md"}
                             test-support/standard-markdown-source)
        output (:output result)
        tree (markdown/parse output)
        code (first (test-support/nodes-by-type tree "code"))]
    (testing "When valid Markdown passes through the pipeline, then output is returned without diagnostics"
      (is (true? (:ok? result)))
      (is (string? output))
      (is (empty? (:diagnostics result))))

    (testing "When valid Markdown is serialized, then its supported meaning is preserved"
      (is (= 1 (count (test-support/nodes-by-type tree "strong"))))
      (is (= 1 (count (test-support/nodes-by-type tree "inlineCode"))))
      (is (= 1 (count (test-support/nodes-by-type tree "footnoteReference"))))
      (is (= 1 (count (test-support/nodes-by-type tree "footnoteDefinition"))))
      (is (= 1 (count (test-support/nodes-by-type tree "table"))))
      (is (= "markdown" (.-lang code)))
      (is (.includes (.-value code) ":index[コード内]"))))

  (let [source (str ":::third-party\n"
                    ":nested[内部の未知記法]\n"
                    ":::\n\n"
                    ":index[独立した未知記法]\n")
        collection-called? (atom false)
        transform-called? (atom false)
        result (with-redefs [transform/collect-reference-targets
                             (fn [_tree _context]
                               (reset! collection-called? true)
                               [])
                             transform/transform
                             (fn [tree _context]
                               (reset! transform-called? true)
                               tree)]
                 (pipeline/run {:mode :transform
                                :source-name "unknown.md"
                                :input-path "/work/unknown.md"}
                               source))]
    (testing "When unknown directives are found, then independent diagnostics are returned in source order"
      (is (false? (:ok? result)))
      (is (= [{:file "unknown.md"
               :line 1
               :column 1
               :directive "third-party"
               :message "`third-party`は登録されていないdirectiveです。"}
              {:file "unknown.md"
               :line 5
               :column 1
               :directive "index"
               :message "`index`は登録されていないdirectiveです。"}]
             (:diagnostics result))))

    (testing "When diagnostics are returned, then collection and transformation are skipped and output is omitted"
      (is (nil? (:output result)))
      (is (false? @collection-called?))
      (is (false? @transform-called?)))))

(deftest execution-context-test
  (let [context {:mode :build
                 :source-name "chapter.md"
                 :input-path "/work/manuscripts/chapter.md"
                 :source-root-path "/work/manuscripts"
                 :publication-entry {:type :document
                                     :path "chapter.md"
                                     :kind "chapter"
                                     :include-in-toc true}}
        validation-context (atom nil)
        collection-context (atom nil)
        reference-validation-context (atom nil)
        transformation-context (atom nil)
        reference-targets [{:logical-id "diagram"
                            :type "figure"
                            :target-id "figure-diagram"
                            :title-target-id "figure-diagram-caption"
                            :numbered? true
                            :source-name "chapter.md"
                            :line 1
                            :column 1}]
        result (with-redefs [transform/validate
                             (fn [_tree actual-context]
                               (reset! validation-context actual-context)
                               [])
                             transform/collect-reference-targets
                             (fn [_tree actual-context]
                               (reset! collection-context actual-context)
                               reference-targets)
                             transform/reference-diagnostics
                             (fn [_tree actual-context]
                               (reset! reference-validation-context
                                       actual-context)
                               [])
                             transform/transform
                             (fn [tree actual-context]
                               (reset! transformation-context actual-context)
                               tree)]
                 (pipeline/run context "# 見出し\n"))]
    (testing "When the pipeline runs successfully, then collected targets enrich the context used for reference validation and transformation"
      (is (:ok? result))
      (is (= context @validation-context))
      (is (= context @collection-context))
      (is (= (assoc context :reference-targets reference-targets)
             @reference-validation-context))
      (is (= (assoc context :reference-targets reference-targets)
             @transformation-context)))))
