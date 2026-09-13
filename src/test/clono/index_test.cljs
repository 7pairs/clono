(ns clono.index-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.pipeline :as pipeline]))

(defn- transform-context [source-name]
  {:mode :transform
   :source-name source-name})

(def valid-index-placements
  [{:case "root paragraph"
    :source "これは:index[索引]{reading=\"さくいん\"}です。\n"}
   {:case "ordinary brace text after attributes"
    :source ":index[索引]{reading=\"さくいん\"}{注記}\n"}
   {:case "list item"
    :source "- :index[一覧]{reading=\"いちらん\"}\n"}
   {:case "blockquote"
    :source "> :index[引用]{reading=\"いんよう\"}\n"}
   {:case "column paragraph"
    :source (str ":::column[雑談]\n"
                 ":index[麻雀]{reading=\"まーじゃん\"}の話です。\n"
                 ":::\n")}
   {:case "column list item"
    :source (str ":::column[用語]\n"
                 "- :index[配牌]{reading=\"はいはい\"}\n"
                 ":::\n")}])

(def invalid-index-cases
  [{:case "leaf directive"
    :source "::index[索引]{reading=\"さくいん\"}\n"
    :message "`index`はText directiveとして記述する必要があります。"}
   {:case "container directive"
    :source (str ":::index[索引]{reading=\"さくいん\"}\n"
                 "本文\n"
                 ":::\n")
    :message "`index`はText directiveとして記述する必要があります。"}
   {:case "missing term"
    :source ":index[]{reading=\"さくいん\"}\n"
    :message "`index`のラベルには空白ではないプレーンテキストの索引語が必要です。"}
   {:case "formatted term"
    :source ":index[**索引**]{reading=\"さくいん\"}\n"
    :message "`index`のラベルには空白ではないプレーンテキストの索引語が必要です。"}
   {:case "missing reading"
    :source ":index[索引]\n"
    :message "`index`には`reading`属性が必要です。"}
   {:case "empty reading"
    :source ":index[索引]{reading=\"　\"}\n"
    :message "`index`の`reading`属性には空白ではない読みを指定してください。"}
   {:case "surrounding whitespace"
    :source ":index[索引]{reading=\" さくいん\"}\n"
    :message "`index`の`reading`属性の先頭または末尾に空白を含めることはできません。"}
   {:case "unsupported characters"
    :source ":index[索引]{reading=\"索引\"}\n"
    :message "`index`の`reading`属性に使用できない文字または文字の組み合わせがあります。"}
   {:case "invalid prolonged mark"
    :source ":index[索引]{reading=\"んー\"}\n"
    :message "`index`の`reading`属性にある音引きから母音を決定できません。"}
   {:case "unknown attribute"
    :source ":index[索引]{reading=\"さくいん\" class=\"custom\"}\n"
    :message "`index`には`reading`以外の属性を指定できません。"}
   {:case "malformed attributes"
    :source ":index[索引]{reading=\"さくいん\"\n"
    :message "`index`の属性を解析できません。"}])

(def invalid-index-placements
  [{:case "heading"
    :source "# :index[索引]{reading=\"さくいん\"}\n"}
   {:case "emphasis"
    :source "*:index[索引]{reading=\"さくいん\"}*\n"}
   {:case "link"
    :source "[:index[索引]{reading=\"さくいん\"}](https://example.com/)\n"}
   {:case "footnote definition"
    :source (str "脚注です[^note]。\n\n"
                 "[^note]: :index[索引]{reading=\"さくいん\"}\n")}
   {:case "column title"
    :source (str ":::column[:index[索引]{reading=\"さくいん\"}]\n"
                 "本文です。\n"
                 ":::\n")}
   {:case "other directive"
    :source (str ":::align{position=\"right\"}\n"
                 ":index[索引]{reading=\"さくいん\"}\n"
                 ":::\n")}])

(deftest valid-index-test
  (testing "When index directives use the supported term, reading, and placement, then analysis accepts every directive"
    (doseq [{:keys [case source]} valid-index-placements]
      (let [result (pipeline/analyze (transform-context "valid-index.md")
                                     source)]
        (is (:ok? result) case)
        (is (empty? (:diagnostics result)) case)))))

(deftest invalid-index-test
  (testing "When an index directive violates its local contract, then analysis fails with a positioned diagnostic"
    (doseq [{:keys [case source message]} invalid-index-cases]
      (let [result (pipeline/analyze (transform-context "invalid-index.md")
                                     source)
            messages (mapv :message (:diagnostics result))]
        (is (false? (:ok? result)) case)
        (is (nil? (:tree result)) case)
        (is (some #{message} messages) case)
        (doseq [problem (:diagnostics result)]
          (is (= "invalid-index.md" (:file problem)) case)
          (is (= "index" (:directive problem)) case)
          (is (pos-int? (:line problem)) case)
          (is (pos-int? (:column problem)) case))))))

(deftest index-placement-test
  (testing "When an index directive is outside a permitted paragraph position, then analysis rejects its placement"
    (doseq [{:keys [case source]} invalid-index-placements]
      (let [result (pipeline/analyze (transform-context "misplaced-index.md")
                                     source)]
        (is (false? (:ok? result)) case)
        (is (nil? (:tree result)) case)
        (is (some #{"`index`は許可された通常の段落の直接の子として記述してください。"}
                  (mapv :message (:diagnostics result)))
            case)))))
