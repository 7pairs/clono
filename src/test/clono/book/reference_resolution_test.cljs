(ns clono.book.reference-resolution-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.book.reference-resolution :as reference-resolution]
   [clono.markdown :as markdown]
   [clono.pipeline :as pipeline]))

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

(defn- target [logical-id source-name]
  {:logical-id logical-id
   :type "figure"
   :target-id (str "figure-" logical-id)
   :title-target-id (str "figure-" logical-id "-caption")
   :numbered? true
   :source-name source-name
   :line 1
   :column 1})

(defn- table-target [logical-id source-name]
  {:logical-id logical-id
   :type "table"
   :target-id (str "table-" logical-id)
   :title-target-id (str "table-" logical-id "-caption")
   :numbered? true
   :source-name source-name
   :line 1
   :column 1})

(deftest cross-document-reference-resolution-test
  (testing "When local and cross-document references are resolved, then each manuscript receives source-relative fragment URLs"
    (let [source-name "chapters/chapter-one.md"
          external-name "appendices/図#2?!%.MD"
          source (str ":xref[local]{type=\"figure\" format=\"number\"}\n\n"
                      ":xref[external]{type=\"figure\" "
                      "format=\"number-title\"}\n")
          result
          (reference-resolution/resolve-references
           [(manuscript source-name source)]
           [(target "local" source-name)
            (target "external" external-name)])
          resolved (first (:manuscripts result))
          targets (get-in resolved [:context :reference-targets])]
      (is (:ok? result))
      (is (empty? (:diagnostics result)))
      (is (= [{:href "#figure-local"
               :title-href "#figure-local-caption"}
              {:href (str "../appendices/%E5%9B%B3%232%3F%21%25.html"
                          "#figure-external")
               :title-href
               (str "../appendices/%E5%9B%B3%232%3F%21%25.html"
                    "#figure-external-caption")}]
             (mapv #(select-keys % [:href :title-href]) targets)))
      (let [transformation
            (pipeline/run-analyzed (:context resolved) (:tree resolved))]
        (is (:ok? transformation))
        (is (.includes
             (:output transformation)
             (str "href=\"#figure-local\"")))
        (is (.includes
             (:output transformation)
             (str "href=\"../appendices/%E5%9B%B3%232%3F%21%25.html"
                  "#figure-external\"")))
        (is (.includes
             (:output transformation)
             (str "data-title-href=\""
                  "../appendices/%E5%9B%B3%232%3F%21%25.html"
                  "#figure-external-caption\""))))))

  (testing "When a nested target refers back to another manuscript, then parent path segments remain URL separators"
    (let [source-name "appendices/details/appendix.md"
          result
          (reference-resolution/resolve-references
           [(manuscript
             source-name
             ":xref[overview]{type=\"figure\" format=\"title\"}\n")]
           [(target "overview" "chapters/overview.md")])
          resolved-target
          (first (get-in result [:manuscripts 0 :context :reference-targets]))]
      (is (:ok? result))
      (is (= "../../chapters/overview.html#figure-overview"
             (:href resolved-target)))
      (is (= "../../chapters/overview.html#figure-overview-caption"
             (:title-href resolved-target))))))

(deftest cross-document-table-reference-resolution-test
  (testing "When published manuscripts refer to each other's tables, then both references resolve to source-relative table and caption links"
    (let [chapter-name "chapters/one.md"
          appendix-name "appendices/two.MD"
          chapter-source
          (str ":xref[workflow]{type=\"table\" format=\"number-title\"}\n\n"
               ":::table[実行環境]{#runtime}\n"
               "| 項目 | 値 |\n"
               "| --- | --- |\n"
               "| Node.js | 24 |\n"
               ":::\n")
          appendix-source
          (str ":xref[runtime]{type=\"table\" format=\"title\"}\n\n"
               ":::table[処理フロー]{#workflow}\n"
               "| 手順 |\n"
               "| --- |\n"
               "| 変換 |\n"
               ":::\n")
          result
          (reference-resolution/resolve-references
           [(manuscript chapter-name chapter-source)
            (manuscript appendix-name appendix-source)]
           [(table-target "runtime" chapter-name)
            (table-target "workflow" appendix-name)])
          resolved (:manuscripts result)
          chapter-result
          (pipeline/run-analyzed (get-in resolved [0 :context])
                                 (get-in resolved [0 :tree]))
          appendix-result
          (pipeline/run-analyzed (get-in resolved [1 :context])
                                 (get-in resolved [1 :tree]))]
      (is (:ok? result))
      (is (empty? (:diagnostics result)))
      (is (:ok? chapter-result))
      (is (:ok? appendix-result))
      (is (.includes
           (:output chapter-result)
           (str "<a class=\"clono-xref clono-xref-table "
                "clono-xref-number-title\" "
                "href=\"../appendices/two.html#table-workflow\" "
                "data-title-href=\"../appendices/two.html"
                "#table-workflow-caption\"></a>")))
      (is (.includes
           (:output appendix-result)
           (str "<a class=\"clono-xref clono-xref-table "
                "clono-xref-title\" "
                "href=\"../chapters/one.html#table-runtime\" "
                "data-title-href=\"../chapters/one.html"
                "#table-runtime-caption\"></a>")))
      (is (not (.includes (:output chapter-result)
                          "clono-xref-placeholder")))
      (is (not (.includes (:output appendix-result)
                          "clono-xref-placeholder"))))))

(deftest book-table-reference-failure-test
  (testing "When published manuscripts contain missing and mismatched table references, then every diagnostic is returned in manuscript and source order without resolved manuscripts"
    (let [result
          (reference-resolution/resolve-references
           [(manuscript
             "chapter-one.md"
             (str ":xref[missing-table]"
                  "{type=\"table\" format=\"number\"}\n\n"
                  ":xref[diagram]{type=\"table\" format=\"title\"}\n"))
            (manuscript
             "chapter-two.md"
             ":xref[runtime]{type=\"figure\" format=\"number-title\"}\n")]
           [(target "diagram" "chapter-three.md")
            (table-target "runtime" "chapter-three.md")])]
      (is (false? (:ok? result)))
      (is (nil? (:manuscripts result)))
      (is (= [{:file "chapter-one.md"
               :line 1
               :column 1
               :directive "xref"
               :message "`xref`の参照先`missing-table`を解決できません。"}
              {:file "chapter-one.md"
               :line 3
               :column 1
               :directive "xref"
               :message "`xref`の参照種別が参照先と一致しません。"}
              {:file "chapter-two.md"
               :line 1
               :column 1
               :directive "xref"
               :message "`xref`の参照種別が参照先と一致しません。"}]
             (:diagnostics result))))))

(deftest unresolved-book-reference-test
  (testing "When published manuscripts contain unresolved references, then every diagnostic is returned in manuscript and source order without resolved manuscripts"
    (let [result
          (reference-resolution/resolve-references
           [(manuscript
             "chapter-one.md"
             (str ":xref[first]{type=\"figure\" format=\"number\"}\n\n"
                  ":xref[second]{type=\"figure\" format=\"title\"}\n"))
            (manuscript
             "chapter-two.md"
             ":xref[third]{type=\"figure\" format=\"number-title\"}\n")]
           [])]
      (is (false? (:ok? result)))
      (is (nil? (:manuscripts result)))
      (is (= [{:file "chapter-one.md"
               :line 1
               :column 1
               :directive "xref"
               :message "`xref`の参照先`first`を解決できません。"}
              {:file "chapter-one.md"
               :line 3
               :column 1
               :directive "xref"
               :message "`xref`の参照先`second`を解決できません。"}
              {:file "chapter-two.md"
               :line 1
               :column 1
               :directive "xref"
               :message "`xref`の参照先`third`を解決できません。"}]
             (:diagnostics result))))))

(deftest unsafe-reference-path-test
  (testing "When a referenced manuscript path cannot be encoded safely, then the reference position is diagnosed without leaking a path exception"
    (let [invalid-segment (js/String.fromCharCode 0xD800)
          result
          (reference-resolution/resolve-references
           [(manuscript
             "chapter.md"
             ":xref[unsafe]{type=\"figure\" format=\"number\"}\n")]
           [(target "unsafe" (str "appendices/" invalid-segment ".md"))])]
      (is (false? (:ok? result)))
      (is (nil? (:manuscripts result)))
      (is (= [{:file "chapter.md"
               :line 1
               :column 1
               :directive "xref"
               :message (str "`xref`の参照先への相対HTMLパスを"
                             "安全に生成できません。")}]
             (:diagnostics result)))))

  (testing "When distinct manuscripts map to the same HTML path, then the reference position is diagnosed"
    (let [result
          (reference-resolution/resolve-references
           [(manuscript
             "chapter.md"
             ":xref[collision]{type=\"figure\" format=\"title\"}\n")]
           [(target "collision" "chapter.MD")])]
      (is (false? (:ok? result)))
      (is (nil? (:manuscripts result)))
      (is (= "`xref`の参照先への相対HTMLパスを安全に生成できません。"
             (-> result :diagnostics first :message))))))
