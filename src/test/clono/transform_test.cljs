(ns clono.transform-test
  (:require
   ["node:fs" :as fs]
   [cljs.test :refer [deftest is testing]]
   [clono.markdown :as markdown]
   [clono.pipeline :as pipeline]
   [clono.test-support :as test-support]))

(def valid-align-source
  (str ":::align{position=\"right\"}\n"
       "*2026年8月22日*\\\n"
       "`22:00`\n\n"
       "[Thunder Claw][circle]の**署名**です[^note]。\n"
       ":::\n\n"
       "[circle]: https://thunder-claw.com/\n\n"
       "[^note]: 脚注です。\n"))

(def invalid-align-cases
  [{:case "text directive"
    :source ":align[右寄せ]{position=\"right\"}\n"
    :message "`align`はContainer directiveとして記述する必要があります。"}
   {:case "leaf directive"
    :source "::align{position=\"right\"}\n"
    :message "`align`はContainer directiveとして記述する必要があります。"}
   {:case "missing position"
    :source ":::align\n本文です。\n:::\n"
    :message "`align`には`position=\"right\"`が必要です。"}
   {:case "unsupported position"
    :source ":::align{position=\"center\"}\n本文です。\n:::\n"
    :message "`align`の`position`属性には`right`を指定する必要があります。"}
   {:case "additional attribute"
    :source ":::align{position=\"right\" custom=\"value\"}\n本文です。\n:::\n"
    :message "`align`には`position`以外の属性を指定できません。"}
   {:case "empty content"
    :source ":::align{position=\"right\"}\n:::\n"
    :message "`align`には1個以上の段落が必要です。"}
   {:case "heading"
    :source ":::align{position=\"right\"}\n# 見出し\n:::\n"
    :message "`align`の直下には段落だけを記述できます（見出しを検出しました）。"}
   {:case "list"
    :source ":::align{position=\"right\"}\n- 項目\n:::\n"
    :message "`align`の直下には段落だけを記述できます（リストを検出しました）。"}
   {:case "code block"
    :source ":::align{position=\"right\"}\n```clojure\n(+ 1 1)\n```\n:::\n"
    :message "`align`の直下には段落だけを記述できます（コードブロックを検出しました）。"}
   {:case "image"
    :source ":::align{position=\"right\"}\n![画像](image.png)\n:::\n"
    :message "`align`の段落内では画像を使用できません。"}
   {:case "table"
    :source ":::align{position=\"right\"}\n| 項目 | 値 |\n| --- | --- |\n| A | 1 |\n:::\n"
    :message "`align`の直下には段落だけを記述できます（表を検出しました）。"}
   {:case "raw HTML"
    :source ":::align{position=\"right\"}\n<div>HTML</div>\n:::\n"
    :message "`align`の直下には段落だけを記述できます（raw HTMLを検出しました）。"}
   {:case "nested directive"
    :source (str "::::align{position=\"right\"}\n"
                 ":::align{position=\"right\"}\n"
                 "本文です。\n"
                 ":::\n"
                 "::::\n")
    :message "`align`の直下には段落だけを記述できます（directiveを検出しました）。"}])

(defn- normalize-line-endings [value]
  (.replace value (js/RegExp. "\\r\\n?" "g") "\n"))

(deftest align-transformation-test
  (let [result (pipeline/run {:mode :transform :source-name "align.md"}
                             valid-align-source)
        output (:output result)
        tree (markdown/parse output)]
    (testing "When a valid align directive is transformed, then fixed wrapper HTML and preserved Markdown are returned"
      (is (true? (:ok? result)))
      (is (empty? (:diagnostics result)))
      (is (.includes output "<div class=\"clono-align-right\">"))
      (is (.includes output "</div>"))
      (is (nil? (test-support/directive tree "align")))
      (is (= 2 (count (test-support/nodes-by-type tree "html"))))
      (is (= 1 (count (test-support/nodes-by-type tree "emphasis"))))
      (is (= 1 (count (test-support/nodes-by-type tree "strong"))))
      (is (= 1 (count (test-support/nodes-by-type tree "inlineCode"))))
      (is (= 1 (count (test-support/nodes-by-type tree "linkReference"))))
      (is (= 1 (count (test-support/nodes-by-type tree "break"))))
      (is (= 1 (count (test-support/nodes-by-type tree "footnoteReference"))))))

  (testing "When the clono stylesheet is inspected, then it provides the required right-alignment rule"
    (let [stylesheet (normalize-line-endings
                      (.readFileSync fs "styles/clono.css" "utf8"))]
      (is (.includes stylesheet
                     ".clono-align-right {\n  text-align: right;\n}\n"))))

  (testing "When multiple Windows line endings are normalized, then every line ending becomes LF"
    (is (= "first\nsecond\nthird\n"
           (normalize-line-endings "first\r\nsecond\r\nthird\r")))))

(deftest invalid-align-test
  (testing "When an align directive violates its contract, then transformation fails with a positioned diagnostic"
    (doseq [{:keys [case source message]} invalid-align-cases]
      (let [result (pipeline/run {:mode :transform
                                  :source-name "invalid-align.md"}
                                 source)
            problem (first (:diagnostics result))]
        (is (false? (:ok? result)) case)
        (is (nil? (:output result)) case)
        (is (= 1 (count (:diagnostics result))) case)
        (is (= {:file "invalid-align.md"
                :directive "align"
                :message message}
               (select-keys problem [:file :directive :message]))
            case)
        (is (pos-int? (:line problem)) case)
        (is (pos-int? (:column problem)) case)))))

(deftest align-diagnostic-integration-test
  (testing "When an unknown container is inside align, then only the unknown container is reported"
    (let [source (str "::::align{position=\"right\"}\n"
                      ":::third-party\n"
                      "未知の内容です。\n"
                      ":::\n"
                      "::::\n")
          result (pipeline/run {:mode :transform
                                :source-name "unknown-in-align.md"}
                               source)]
      (is (= [{:file "unknown-in-align.md"
               :line 2
               :column 1
               :directive "third-party"
               :message "`third-party`は登録されていないdirectiveです。"}]
             (:diagnostics result)))
      (is (nil? (:output result)))))

  (testing "When a known align container has no closing fence, then transformation fails with its opening position"
    (let [result (pipeline/run
                  {:mode :transform
                   :source-name "unclosed-align.md"}
                  ":::align{position=\"right\"}\n本文です。\n")]
      (is (= [{:file "unclosed-align.md"
               :line 1
               :column 1
               :directive "align"
               :message "`align`の終了マーカーがありません。"}]
             (:diagnostics result)))
      (is (nil? (:output result))))))
