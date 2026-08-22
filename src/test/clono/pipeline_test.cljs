(ns clono.pipeline-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.markdown :as markdown]
   [clono.pipeline :as pipeline]
   [clono.test-support :as test-support]
   [clono.transform :as transform]))

(deftest run-test
  (let [result (pipeline/run "manuscript.md" test-support/standard-markdown-source)
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
                    "::page-break\n\n"
                    ":index[独立した未知記法]\n")
        transform-called? (atom false)
        result (with-redefs [transform/transform
                             (fn [tree]
                               (reset! transform-called? true)
                               tree)]
                 (pipeline/run "unknown.md" source))]
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
               :directive "page-break"
               :message "`page-break`は登録されていないdirectiveです。"}
              {:file "unknown.md"
               :line 7
               :column 1
               :directive "index"
               :message "`index`は登録されていないdirectiveです。"}]
             (:diagnostics result))))

    (testing "When diagnostics are returned, then transformation is skipped and output is omitted"
      (is (nil? (:output result)))
      (is (false? @transform-called?)))))
