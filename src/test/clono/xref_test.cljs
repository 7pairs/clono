(ns clono.xref-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.markdown :as markdown]
   [clono.pipeline :as pipeline]
   [clono.test-support :as test-support]))

(defn- transform-context [source-name]
  {:mode :transform
   :source-name source-name})

(def figure-source
  (str ":::figure[全体構成]{#architecture}\n"
       "![入力から出力までの構成図](architecture.svg)\n"
       ":::\n"))

(deftest local-xref-transformation-test
  (testing "When local figure references use every format before and after their target, then each reference is resolved to the expected link structure"
    (let [source (str ":xref[architecture]{type=\"figure\" format=\"number\"}\n\n"
                      ":xref[architecture]{type=\"figure\" format=\"number-title\"}\n\n"
                      figure-source
                      "\n:xref[architecture]{type=\"figure\" format=\"title\"}\n")
          result (pipeline/run (transform-context "local-xref.md") source)
          output (:output result)
          tree (markdown/parse output)]
      (is (:ok? result))
      (is (empty? (:diagnostics result)))
      (is (.includes
           output
           (str "<a class=\"clono-xref clono-xref-figure clono-xref-number\" "
                "href=\"#figure-architecture\"></a>")))
      (is (.includes
           output
           (str "<a class=\"clono-xref clono-xref-figure clono-xref-number-title\" "
                "href=\"#figure-architecture\" "
                "data-title-href=\"#figure-architecture-caption\"></a>")))
      (is (.includes
           output
           (str "<a class=\"clono-xref clono-xref-figure clono-xref-title\" "
                "href=\"#figure-architecture\" "
                "data-title-href=\"#figure-architecture-caption\"></a>")))
      (is (nil? (test-support/directive tree "xref"))))))

(deftest invalid-xref-test
  (testing "When an xref directive violates its local contract, then transformation fails with a positioned diagnostic"
    (doseq [{:keys [case source message]}
            [{:case "leaf directive"
              :source (str "::xref[architecture]"
                           "{type=\"figure\" format=\"number\"}\n")
              :message "`xref`はText directiveとして記述する必要があります。"}
             {:case "container directive"
              :source (str ":::xref[architecture]"
                           "{type=\"figure\" format=\"number\"}\n"
                           ":::\n")
              :message "`xref`はText directiveとして記述する必要があります。"}
             {:case "empty label"
              :source ":xref[]{type=\"figure\" format=\"number\"}\n"
              :message "`xref`のラベルには有効な参照先の論理IDが必要です。"}
             {:case "formatted label"
              :source ":xref[**architecture**]{type=\"figure\" format=\"number\"}\n"
              :message "`xref`のラベルには有効な参照先の論理IDが必要です。"}
             {:case "invalid logical ID"
              :source ":xref[Architecture]{type=\"figure\" format=\"number\"}\n"
              :message "`xref`のラベルには有効な参照先の論理IDが必要です。"}
             {:case "missing type"
              :source ":xref[architecture]{format=\"number\"}\n"
              :message "`xref`には`type`属性が必要です。"}
             {:case "unsupported type"
              :source ":xref[architecture]{type=\"table\" format=\"number\"}\n"
              :message "`xref`の`type`属性には`figure`を指定してください。"}
             {:case "missing format"
              :source ":xref[architecture]{type=\"figure\"}\n"
              :message "`xref`には`format`属性が必要です。"}
             {:case "unsupported format"
              :source ":xref[architecture]{type=\"figure\" format=\"page\"}\n"
              :message (str "`xref`の`format`属性には`number`、"
                            "`number-title`または`title`を指定してください。")}
             {:case "unknown attribute"
              :source (str ":xref[architecture]"
                           "{type=\"figure\" format=\"number\" class=\"custom\"}\n")
              :message "`xref`には`type`と`format`以外の属性を指定できません。"}
             {:case "malformed attributes"
              :source (str ":xref[architecture]"
                           "{type=\"figure\" format=\"number\"\n")
              :message "`xref`の属性を解析できません。"}]]
      (let [result (pipeline/run (transform-context "invalid-xref.md") source)
            messages (mapv :message (:diagnostics result))]
        (is (false? (:ok? result)) case)
        (is (nil? (:output result)) case)
        (is (some #{message} messages) case)
        (doseq [problem (:diagnostics result)]
          (is (= "invalid-xref.md" (:file problem)) case)
          (is (= "xref" (:directive problem)) case)
          (is (pos-int? (:line problem)) case)
          (is (pos-int? (:column problem)) case))))))

(deftest unresolved-local-xref-test
  (testing "When transform cannot find a local xref target, then each format becomes a fixed placeholder without link attributes or author input"
    (doseq [[format expected-text]
            [["number" "図X.X"]
             ["number-title" "図X.X 参照先未解決"]
             ["title" "参照先未解決"]]]
      (let [source (str ":xref[external-figure]"
                        "{type=\"figure\" format=\"" format "\"}\n")
            result (pipeline/run
                    (transform-context "unresolved-xref.md")
                    source)
            output (:output result)
            expected (str "<span class=\"clono-xref clono-xref-figure "
                          "clono-xref-" format " clono-xref-placeholder\">"
                          expected-text
                          "</span>")]
        (is (:ok? result) format)
        (is (empty? (:diagnostics result)) format)
        (is (.includes output expected) format)
        (is (not (.includes output "external-figure")) format)
        (is (not (.includes output "href=")) format)
        (is (nil? (test-support/directive (markdown/parse output) "xref"))
            format))))

  (testing "When build cannot find an xref target, then it reports a diagnostic instead of generating a placeholder"
    (let [result
          (pipeline/run
           {:mode :build :source-name "chapter.md"}
           ":xref[missing-figure]{type=\"figure\" format=\"number\"}\n")]
      (is (false? (:ok? result)))
      (is (nil? (:output result)))
      (is (= [{:file "chapter.md"
               :line 1
               :column 1
               :directive "xref"
               :message "`xref`の参照先`missing-figure`を解決できません。"}]
             (:diagnostics result))))))
