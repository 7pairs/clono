(ns clono.pipeline-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.markdown :as markdown]
   [clono.pipeline :as pipeline]
   [clono.test-support :as test-support]
   [goog.object :as gobj]))

(deftest run-test
  (let [output (pipeline/run test-support/markdown-source)
        tree (markdown/parse output)
        column (test-support/directive tree "column")
        page-break (test-support/directive tree "page-break")
        index (test-support/directive tree "index")
        code (first (test-support/nodes-by-type tree "code"))]
    (testing "When Markdown passes through the minimal pipeline, then its supported meaning is preserved"
      (is (string? output))
      (is (= "column" (.-name column)))
      (is (= "コラム" (gobj/get (.-attributes column) "title")))
      (is (= "leafDirective" (.-type page-break)))
      (is (= "textDirective" (.-type index)))
      (is (= "さくいんこうもく" (gobj/get (.-attributes index) "reading")))
      (is (= 1 (count (test-support/nodes-by-type tree "strong"))))
      (is (= 1 (count (test-support/nodes-by-type tree "inlineCode"))))
      (is (= 1 (count (test-support/nodes-by-type tree "footnoteReference"))))
      (is (= 1 (count (test-support/nodes-by-type tree "footnoteDefinition"))))
      (is (= "markdown" (.-lang code)))
      (is (.includes (.-value code) ":index[コード内]")))))
