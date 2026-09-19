(ns clono.space-test
  (:require
   ["node:fs" :as fs]
   [cljs.test :refer [deftest is testing]]
   [clono.ast :as ast]
   [clono.markdown :as markdown]
   [clono.pipeline :as pipeline]
   [clono.test-support :as test-support]))

(def valid-space-source
  (str "前の段落には![印](icon.svg)を含みます[^note]。\n\n"
       "[site]: https://example.com/\n\n"
       "::space\n\n"
       "[^note]: 紙面上のブロックとして数えない脚注定義です。\n\n"
       "後の段落です。\n"))

(def invalid-space-cases
  [{:case "text directive"
    :source "前の段落。\n\n:space[余白]\n\n後の段落。\n"
    :message "`space`はLeaf directiveとして記述する必要があります。"}
   {:case "container directive"
    :source "前の段落。\n\n:::space\n本文です。\n:::\n\n後の段落。\n"
    :message "`space`はLeaf directiveとして記述する必要があります。"}
   {:case "label"
    :source "前の段落。\n\n::space[余白]\n\n後の段落。\n"
    :message "`space`にはラベルを指定できません。"}
   {:case "attribute"
    :source "前の段落。\n\n::space{lines=\"2\"}\n\n後の段落。\n"
    :message "`space`には属性を指定できません。"}
   {:case "document start"
    :source "::space\n\n後の段落。\n"
    :message "`space`の前には通常段落が必要です。"}
   {:case "document start after definitions"
    :source "[site]: https://example.com/\n\n::space\n\n後の段落。\n"
    :message "`space`の前には通常段落が必要です。"}
   {:case "document end"
    :source "前の段落。\n\n::space\n"
    :message "`space`の後には通常段落が必要です。"}
   {:case "document end before definitions"
    :source "前の段落。\n\n::space\n\n[site]: https://example.com/\n"
    :message "`space`の後には通常段落が必要です。"}
   {:case "heading before"
    :source "# 見出し\n\n::space\n\n後の段落。\n"
    :message "`space`の前には通常段落が必要です。"}
   {:case "heading after"
    :source "前の段落。\n\n::space\n\n# 見出し\n"
    :message "`space`の後には通常段落が必要です。"}
   {:case "image-only paragraph before"
    :source "![画像](image.svg)\n\n::space\n\n後の段落。\n"
    :message "`space`の前には通常段落が必要です。"}
   {:case "image-reference-only paragraph after"
    :source (str "前の段落。\n\n"
                 "::space\n\n"
                 "![画像][image]\n\n"
                 "[image]: image.svg\n")
    :message "`space`の後には通常段落が必要です。"}
   {:case "consecutive directives"
    :source "前の段落。\n\n::space\n\n::space\n\n後の段落。\n"
    :message "`space`を連続して記述できません。"}
   {:case "consecutive directives around definitions"
    :source (str "前の段落。\n\n"
                 "::space\n\n"
                 "[site]: https://example.com/\n\n"
                 "::space\n\n"
                 "後の段落。\n")
    :message "`space`を連続して記述できません。"}
   {:case "inside blockquote"
    :source "前の段落。\n\n> 引用です。\n>\n> ::space\n\n後の段落。\n"
    :message "`space`はMarkdown文書のトップレベルに記述する必要があります。"}
   {:case "inside list item"
    :source "前の段落。\n\n- 項目\n\n  ::space\n\n後の段落。\n"
    :message "`space`はMarkdown文書のトップレベルに記述する必要があります。"}
   {:case "inside footnote definition"
    :source (str "前の段落[^note]。\n\n"
                 "[^note]:\n"
                 "    ::space\n\n"
                 "後の段落。\n")
    :message "`space`はMarkdown文書のトップレベルに記述する必要があります。"}])

(defn- normalize-line-endings [value]
  (.replace value (js/RegExp. "\\r\\n?" "g") "\n"))

(deftest space-transformation-test
  (doseq [mode [:transform :build]]
    (let [context (cond-> {:mode mode :source-name "space.md"}
                    (= :build mode)
                    (assoc :publication-entry
                           {:type :document
                            :path "space.md"
                            :kind "chapter"
                            :include-in-toc true}))
          result (pipeline/run context valid-space-source)
          output (:output result)
          tree (markdown/parse output)]
      (testing (str "When valid vertical space is transformed in "
                    (name mode)
                    " mode, then one hidden one-line marker and surrounding paragraphs are returned")
        (is (true? (:ok? result)))
        (is (empty? (:diagnostics result)))
        (is (.includes
             output
             "<div class=\"clono-space\" aria-hidden=\"true\"></div>"))
        (is (nil? (test-support/directive tree "space")))
        (is (= 1 (count (test-support/nodes-by-type tree "html"))))
        (is (= 2 (count (filter #(= "paragraph" (.-type %))
                                (ast/children tree)))))
        (is (= 1 (count (test-support/nodes-by-type tree "definition"))))
        (is (= 1 (count (test-support/nodes-by-type tree "footnoteDefinition")))))))

  (testing "When the clono stylesheet is used, then vertical space receives a one-line block size"
    (let [stylesheet (normalize-line-endings
                      (.readFileSync fs "styles/clono.css" "utf8"))]
      (is (.includes stylesheet
                     ".clono-space {\n  block-size: 1lh;\n}\n")))))

(deftest invalid-space-test
  (testing "When a vertical-space directive violates its contract, then transformation fails with a positioned diagnostic"
    (doseq [{:keys [case source message]} invalid-space-cases]
      (let [result (pipeline/run {:mode :transform
                                  :source-name "invalid-space.md"}
                                 source)
            problem (first (:diagnostics result))]
        (is (false? (:ok? result)) case)
        (is (nil? (:output result)) case)
        (is (= 1 (count (:diagnostics result))) case)
        (is (= {:file "invalid-space.md"
                :directive "space"
                :message message}
               (select-keys problem [:file :directive :message]))
            case)
        (is (pos-int? (:line problem)) case)
        (is (pos-int? (:column problem)) case)))))

(deftest space-diagnostic-integration-test
  (testing "When vertical space is inside a known container, then only the container contract is reported"
    (let [source (str "::::column[コラム]\n"
                      "本文です。\n\n"
                      "::space\n"
                      "::::\n")
          result (pipeline/run {:mode :transform
                                :source-name "space-in-column.md"}
                               source)]
      (is (= [{:file "space-in-column.md"
               :line 4
               :column 1
               :directive "column"
               :message "`column`内ではdirectiveを使用できません。"}]
             (:diagnostics result)))
      (is (nil? (:output result)))))

  (testing "When vertical space is inside an unknown container, then only the unknown container is reported"
    (let [source (str "::::third-party\n"
                      "::space\n"
                      "::::\n")
          result (pipeline/run {:mode :transform
                                :source-name "space-in-unknown.md"}
                               source)]
      (is (= [{:file "space-in-unknown.md"
               :line 1
               :column 1
               :directive "third-party"
               :message "`third-party`は登録されていないdirectiveです。"}]
             (:diagnostics result)))
      (is (nil? (:output result))))))
