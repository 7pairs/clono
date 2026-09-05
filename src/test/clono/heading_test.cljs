(ns clono.heading-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.markdown :as markdown]
   [clono.pipeline :as pipeline]
   [clono.test-support :as test-support]
   [clono.transform :as transform]
   [clono.transform.heading :as heading]))

(defn- headings [source]
  (test-support/nodes-by-type (markdown/parse source) "heading"))

(defn- candidates [source]
  (mapv #(heading/explicit-id-candidate source %)
        (headings source)))

(deftest heading-id-candidate-test
  (testing "When ATX headings from H1 through H3 end with explicit IDs, then each ID candidate is extracted"
    (let [source (str "# はじめに {#introduction}\n\n"
                      "## `clono`の**構造** {#clono-structure}\n\n"
                      "### 変換パイプライン\t{#transformation-pipeline}\n")]
      (is (= [{:value "introduction"
               :suffix " {#introduction}"}
              {:value "clono-structure"
               :suffix " {#clono-structure}"}
              {:value "transformation-pipeline"
               :suffix "\t{#transformation-pipeline}"}]
             (candidates source)))))

  (testing "When an ID-like suffix uses an unsupported heading form, then it is not extracted"
    (let [source (str "#### 深い見出し {#deep-heading}\n\n"
                      "Setext見出し {#setext-heading}\n"
                      "------------------------------\n\n"
                      "# IDなし\n\n"
                      "# 通常の波括弧 {注記}\n\n"
                      "# エスケープ済み \\{#escaped}\n\n"
                      "# 空白なし{#attached}\n\n"
                      "# 閉じ見出しの前 {#before-closing} #\n")]
      (is (= [nil nil nil nil nil nil nil]
             (candidates source)))))

  (testing "When an eligible ATX heading has a malformed ID value, then the value remains available for later validation"
    (let [source (str "# 不正なID {#Invalid_ID}\n\n"
                      "## 空のID {#}\n")]
      (is (= [{:value "Invalid_ID"
               :suffix " {#Invalid_ID}"}
              {:value ""
               :suffix " {#}"}]
             (candidates source))))))

(deftest heading-id-validation-test
  (testing "When eligible ATX headings have malformed IDs, then positioned diagnostics are returned in source order"
    (let [result
          (pipeline/run
           {:mode :transform
            :source-name "invalid-headings.md"}
           (str "# 大文字 {#Invalid}\n\n"
                "## 空のID {#}\n"))]
      (is (false? (:ok? result)))
      (is (nil? (:output result)))
      (is (= [{:file "invalid-headings.md"
               :line 1
               :column 1
               :message (str "見出しのIDには英小文字で始まる英小文字、"
                             "数字、ハイフンだけの値を指定してください。")}
              {:file "invalid-headings.md"
               :line 3
               :column 1
               :message (str "見出しのIDには英小文字で始まる英小文字、"
                             "数字、ハイフンだけの値を指定してください。")}]
             (:diagnostics result)))))

  (testing "When malformed IDs use unsupported heading forms, then they remain outside heading reference validation"
    (let [source (str "#### 深い見出し {#Invalid_ID}\n\n"
                      "Setext見出し {#Invalid_ID}\n"
                      "---------------------------\n")
          result (pipeline/run {:mode :transform
                                :source-name "unsupported-headings.md"}
                               source)]
      (is (:ok? result))
      (is (empty? (:diagnostics result)))
      (is (string? (:output result))))))

(deftest heading-reference-target-test
  (testing "When eligible headings have valid IDs, then reference targets are generated in source order"
    (let [source (str "# はじめに {#introduction}\n\n"
                      "## `clono`の**構造** {#clono-structure}\n\n"
                      "### 変換パイプライン {#transformation-pipeline}\n")
          tree (markdown/parse source)
          targets
          (transform/collect-reference-targets
           tree
           {:mode :transform
            :source-name "chapter.md"
            :source source})]
      (is (= [{:logical-id "introduction"
               :type "heading"
               :target-id "introduction"
               :title-target-id "introduction"
               :numbered? true
               :heading-depth 1
               :document-kind "chapter"
               :source-name "chapter.md"
               :line 1
               :column 1}
              {:logical-id "clono-structure"
               :type "heading"
               :target-id "clono-structure"
               :title-target-id "clono-structure"
               :numbered? true
               :heading-depth 2
               :document-kind "chapter"
               :source-name "chapter.md"
               :line 3
               :column 1}
              {:logical-id "transformation-pipeline"
               :type "heading"
               :target-id "transformation-pipeline"
               :title-target-id "transformation-pipeline"
               :numbered? true
               :heading-depth 3
               :document-kind "chapter"
               :source-name "chapter.md"
               :line 5
               :column 1}]
             targets))))

  (testing "When build targets belong to different document kinds, then numbering metadata follows each publication entry"
    (let [source "## 対象 {#target}\n"
          tree (markdown/parse source)
          target-for
          (fn [kind]
            (first
             (transform/collect-reference-targets
              tree
              {:mode :build
               :source-name (str kind ".md")
               :source source
               :publication-entry {:type :document
                                   :path (str kind ".md")
                                   :kind kind
                                   :include-in-toc true}})))]
      (is (= [["chapter" true]
              ["appendix" true]
              ["frontmatter" false]
              ["backmatter" false]]
             (mapv (fn [kind]
                     (let [target (target-for kind)]
                       [(:document-kind target) (:numbered? target)]))
                   ["chapter" "appendix" "frontmatter" "backmatter"])))))

  (testing "When headings are outside the supported syntax or have invalid IDs, then no reference target is generated for them"
    (let [source (str "# 有効 {#valid}\n\n"
                      "## 不正 {#Invalid_ID}\n\n"
                      "#### 対象外 {#deep-heading}\n\n"
                      "Setext {#setext-heading}\n"
                      "------------------------\n")]
      (is (= ["valid"]
             (mapv :logical-id
                   (transform/collect-reference-targets
                    (markdown/parse source)
                    {:mode :transform
                     :source-name "headings.md"
                     :source source})))))))
