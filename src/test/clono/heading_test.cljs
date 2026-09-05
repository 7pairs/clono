(ns clono.heading-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.markdown :as markdown]
   [clono.test-support :as test-support]
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
