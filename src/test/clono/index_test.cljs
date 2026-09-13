(ns clono.index-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.markdown :as markdown]
   [clono.pipeline :as pipeline]
   [clono.transform :as transform]
   [clono.test-support :as test-support]))

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

(deftest index-entry-collection-test
  (let [source (str "これは:index[Ａｎｄｒｏｉｄ]{reading=\"ＡＮＤＲＯＩＤ\"}です。\n\n"
                    "> :index[A &amp; B]{reading=\"えーあんどびー\"}\n\n"
                    ":::column[雑談]\n"
                    "- :index[バックナンバー]{reading=\"ばっくなんばー\"}\n"
                    ":::\n")
        context (transform-context "chapter.md")
        analysis (pipeline/analyze context source)
        entries (transform/collect-index-entries (:tree analysis) context)]
    (testing "When index directives are collected, then their source values and derived reading data remain in document order"
      (is (:ok? analysis))
      (is (= [{:term "Ａｎｄｒｏｉｄ"
               :normalized-term "Android"
               :reading "ＡＮＤＲＯＩＤ"
               :normalized-reading "android"
               :sort-key "android"
               :group-id :alphanumeric
               :source-name "chapter.md"
               :line 1
               :column 4}
              {:term "A & B"
               :normalized-term "A & B"
               :reading "えーあんどびー"
               :normalized-reading "えーあんどびー"
               :sort-key "ええあんとひい"
               :group-id :a
               :source-name "chapter.md"
               :line 3
               :column 3}
              {:term "バックナンバー"
               :normalized-term "バックナンバー"
               :reading "ばっくなんばー"
               :normalized-reading "ばっくなんばー"
               :sort-key "はつくなんはあ"
               :group-id :ha
               :source-name "chapter.md"
               :line 6
               :column 3}]
             (mapv #(dissoc % :offset :node) entries))))

    (testing "When index directives are collected, then each occurrence reports its original source offset"
      (is (= (mapv #(.indexOf source %)
                   [":index[Ａｎｄｒｏｉｄ]"
                    ":index[A &amp; B]"
                    ":index[バックナンバー]"])
             (mapv :offset entries)))))

  (testing "When a document has no index directive, then its collected index entries are empty"
    (let [source (str "通常の本文です。\n\n"
                      "```markdown\n"
                      ":index[コード内]{reading=\"こおとない\"}\n"
                      "```\n")
          context (transform-context "without-index.md")
          analysis (pipeline/analyze context source)]
      (is (:ok? analysis))
      (is (empty? (transform/collect-index-entries (:tree analysis)
                                                   context))))))

(deftest index-marker-transformation-test
  (let [source (str "これは:index[A &amp; &quot;B&quot;]{reading=\"えーあんどびー\"}です。\n\n"
                    ":index[バックナンバー]{reading=\"ばっくなんばー\"}も参照します。\n")
        result (pipeline/run (transform-context "markers.md") source)
        output (:output result)
        output-tree (markdown/parse output)]
    (testing "When index directives are transformed, then numbered marker spans preserve their visible terms in document order"
      (is (:ok? result))
      (is (empty? (:diagnostics result)))
      (is (.includes
           output
           (str "<span class=\"clono-index-marker\" "
                "id=\"clono-index-marker-1\">A &amp; &quot;B&quot;</span>")))
      (is (.includes
           output
           (str "<span class=\"clono-index-marker\" "
                "id=\"clono-index-marker-2\">バックナンバー</span>")))
      (is (nil? (test-support/directive output-tree "index")))
      (is (not (.includes output "reading="))))

    (testing "When another document is transformed, then its local marker numbering starts at one"
      (let [next-result
            (pipeline/run
             (transform-context "next.md")
             ":index[次]{reading=\"つぎ\"}\n")]
        (is (:ok? next-result))
        (is (.includes
             (:output next-result)
             "id=\"clono-index-marker-1\">次</span>")))))

  (testing "When an index directive is inside a column, then its marker remains inside the transformed column content"
    (let [result
          (pipeline/run
           (transform-context "column-index.md")
           (str ":::column[雑談]\n"
                "これは:index[麻雀]{reading=\"まーじゃん\"}の話です。\n"
                ":::\n"))
          output (:output result)]
      (is (:ok? result))
      (is (< (.indexOf output "<aside class=\"clono-column\">")
             (.indexOf output "id=\"clono-index-marker-1\">麻雀</span>")
             (.indexOf output "</aside>"))))))

(deftest index-marker-failure-test
  (testing "When one index term has conflicting normalized readings, then the later occurrence is diagnosed without output"
    (let [result
          (pipeline/run
           (transform-context "conflicting-index.md")
           (str ":index[橋]{reading=\"はし\"}\n\n"
                ":index[橋]{reading=\"ばし\"}\n"))]
      (is (false? (:ok? result)))
      (is (nil? (:output result)))
      (is (= [{:file "conflicting-index.md"
               :line 3
               :column 1
               :directive "index"
               :message "`index`の索引語`橋`には異なる読みを指定できません。"}]
             (:diagnostics result)))))

  (testing "When equivalent readings are repeated for one index term, then every occurrence receives a marker"
    (let [result
          (pipeline/run
           (transform-context "repeated-index.md")
           (str ":index[Android]{reading=\"ＡＮＤＲＯＩＤ\"}\n\n"
                ":index[Android]{reading=\"android\"}\n"))]
      (is (:ok? result))
      (is (.includes (:output result) "id=\"clono-index-marker-1\""))
      (is (.includes (:output result) "id=\"clono-index-marker-2\""))))

  (testing "When a managed heading ID uses the index marker prefix, then the reserved namespace is diagnosed without output"
    (let [result
          (pipeline/run
           (transform-context "reserved-index-id.md")
           "# 見出し {#clono-index-marker-custom}\n")]
      (is (false? (:ok? result)))
      (is (nil? (:output result)))
      (is (= [{:file "reserved-index-id.md"
               :line 1
               :column 1
               :message (str "見出しのHTML ID`clono-index-marker-custom`には"
                             "clonoの予約接頭辞`clono-index-marker-`を使用できません。")}]
             (:diagnostics result))))))
