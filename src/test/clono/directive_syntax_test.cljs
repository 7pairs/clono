(ns clono.directive-syntax-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.diagnostic :as diagnostic]
   [clono.directive-syntax :as directive-syntax]
   [clono.markdown :as markdown]))

(def options
  {:known-directive-names #{"column" "index"}
   :required-text-attribute-names #{"index"}})

(defn diagnostics [source source-name]
  (-> (directive-syntax/diagnostics
       source
       (markdown/parse source)
       source-name
       options)
      diagnostic/finalize))

(deftest syntax-diagnostics-test
  (testing "When a known container has no closing fence, then its opening position is reported"
    (is (= [{:file "unclosed.md"
             :line 1
             :column 1
             :directive "column"
             :message "`column`の終了マーカーがありません。"}]
           (diagnostics
            ":::column\n閉じていないコラムです。\n"
            "unclosed.md"))))

  (testing "When a known text directive has an unclosed required attribute block, then its position is reported"
    (is (= [{:file "malformed.md"
             :line 1
             :column 14
             :directive "index"
             :message "`index`の属性を解析できません。"}]
           (diagnostics
            ":index[壊れた索引]{reading=\"こわれたさくいん\"です。"
            "malformed.md"))))

  (testing "When ordinary braced text follows valid attributes, then no syntax diagnostic is reported"
    (is (empty?
         (diagnostics
          ":index[索引項目]{reading=\"よみ\"}{注記}"
          "valid.md"))))

  (testing "When directive-like text is inside a code fence, then no syntax diagnostic is reported"
    (is (empty?
         (diagnostics
          "```markdown\n:::column\n:index[索引]{reading=\"broken\"\n```\n"
          "code.md")))))
