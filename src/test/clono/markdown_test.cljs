(ns clono.markdown-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.markdown :as markdown]
   [clono.test-support :as test-support]
   [goog.object :as gobj]))

(deftest parse-test
  (let [tree (markdown/parse test-support/markdown-source)
        column (test-support/directive tree "column")
        page-break (test-support/directive tree "page-break")
        index (test-support/directive tree "index")]
    (testing "When Markdown extensions are parsed, then directives and footnotes become dedicated mdast nodes"
      (is (= "containerDirective" (.-type column)))
      (is (= "column" (.-name column)))
      (is (= "コラム" (gobj/get (.-attributes column) "title")))
      (is (= "leafDirective" (.-type page-break)))
      (is (= "textDirective" (.-type index)))
      (is (= "さくいんこうもく" (gobj/get (.-attributes index) "reading")))
      (is (= 1 (count (test-support/nodes-by-type tree "footnoteReference"))))
      (is (= 1 (count (test-support/nodes-by-type tree "footnoteDefinition")))))

    (testing "When directive-like text is inside a code fence, then it remains code"
      (let [code (first (test-support/nodes-by-type tree "code"))]
        (is (= "markdown" (.-lang code)))
        (is (.includes (.-value code) ":index[コード内]"))
        (is (= 3 (count (filter #(some? (.-name %))
                                (test-support/nodes tree)))))))))
