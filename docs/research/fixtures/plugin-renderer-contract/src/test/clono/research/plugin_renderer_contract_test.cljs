(ns clono.research.plugin-renderer-contract-test
  (:require
   ["./custom_column_renderer.js" :refer [customColumnRenderer]]
   [cljs.test :refer [deftest is testing]]
   [clono.research.plugin-renderer-contract :as renderer-contract]))

(def body-markdown
  (str "本文には**強調**と`コード`がある。\n\n"
       "- 箇条書き\n"
       "- 二つ目"))

(def unsafe-title
  "A & </p><script data-clono-probe=\"title\">alert('x')</script> \"quoted\" 'single'")

(defn- column-input [title]
  (js/Object.freeze #js {:title title :body body-markdown}))

(defn- column-context [line]
  {:source-name "chapter.md"
   :line line
   :column 1})

(def expected-markdown
  (str "<aside class=\"clono-column\">\n\n"
       "<p class=\"clono-column-title\">"
       "A &amp; &lt;/p&gt;&lt;script data-clono-probe=&quot;title&quot;&gt;"
       "alert(&#39;x&#39;)&lt;/script&gt; &quot;quoted&quot; &#39;single&#39;"
       "</p>\n\n"
       body-markdown
       "\n\n</aside>"))

(deftest default-column-renderer-test
  (testing "When the default renderer receives HTML syntax in a title, then encoded title text and unchanged body Markdown are returned"
    (let [input (js/Object.freeze
                 #js {:title unsafe-title
                      :body body-markdown})
          output (renderer-contract/render-column
                  renderer-contract/default-column-renderer
                  input)]
      (is (= expected-markdown output))
      (is (= unsafe-title (.-title input)))
      (is (= body-markdown (.-body input))))))

(deftest renderer-exception-test
  (testing "When a renderer throws while converting columns, then no output is returned and later columns are not rendered"
    (let [rendered-titles (atom [])
          renderer (fn [input]
                     (let [title (.-title input)]
                       (swap! rendered-titles conj title)
                       (if (= "失敗するコラム" title)
                         (throw (js/Error. "The renderer deliberately failed"))
                         (str "<aside>" title "</aside>"))))
          result (renderer-contract/render-columns
                  renderer
                  [{:input (column-input "最初のコラム")
                    :context (column-context 2)}
                   {:input (column-input "失敗するコラム")
                    :context (column-context 8)}
                   {:input (column-input "後続のコラム")
                    :context (column-context 14)}])]
      (is (false? (:ok? result)))
      (is (nil? (:output result)))
      (is (= ["最初のコラム" "失敗するコラム"] @rendered-titles))
      (is (= [{:file "chapter.md"
               :line 8
               :column 1
               :directive "column"
               :message (str "コラムrendererの実行に失敗しました: "
                             "The renderer deliberately failed")}]
             (:diagnostics result))))))

(deftest renderer-return-value-test
  (testing "Given a renderer that returns an object"
    (testing "When a column is converted, then no output is returned and the invalid type is diagnosed"
      (let [result (renderer-contract/render-columns
                    (fn [_input] #js {:html "<aside></aside>"})
                    [{:input (column-input "不正な戻り値")
                      :context (column-context 3)}])]
        (is (false? (:ok? result)))
        (is (nil? (:output result)))
        (is (= [{:file "chapter.md"
                 :line 3
                 :column 1
                 :directive "column"
                 :message "コラムrendererは文字列を返す必要があります: object"}]
               (:diagnostics result))))))

  (testing "Given a renderer that returns a Promise"
    (testing "When a column is converted, then no output is returned and asynchronous rendering is diagnosed"
      (let [result (renderer-contract/render-columns
                    (fn [_input] (js/Promise.resolve "<aside></aside>"))
                    [{:input (column-input "非同期の戻り値")
                      :context (column-context 5)}])]
        (is (false? (:ok? result)))
        (is (nil? (:output result)))
        (is (= [{:file "chapter.md"
                 :line 5
                 :column 1
                 :directive "column"
                 :message (str "コラムrendererはPromiseではなく文字列を"
                               "同期的に返す必要があります。")}]
               (:diagnostics result)))))))

(deftest custom-column-renderer-test
  (testing "When the custom renderer receives HTML syntax in a title, then encoded title text and unchanged body Markdown are returned in nested wrappers"
    (let [input (js/Object.freeze
                 #js {:title unsafe-title
                      :body body-markdown})
          output (renderer-contract/render-column customColumnRenderer input)
          expected
          (str "<aside class=\"clono-column custom-column\">\n"
               "<div class=\"custom-column-outer\">\n"
               "<div class=\"custom-column-inner\">\n"
               "<p class=\"clono-column-title custom-column-title\">\n"
               "<span class=\"custom-column-title-mark\" "
               "aria-hidden=\"true\">COLUMN</span>\n"
               "<span class=\"custom-column-title-text\">"
               "A &amp; &lt;/p&gt;&lt;script data-clono-probe=&quot;title&quot;&gt;"
               "alert(&#39;x&#39;)&lt;/script&gt; &quot;quoted&quot; &#39;single&#39;</span>\n"
               "</p>\n"
               "<div class=\"custom-column-body\">\n\n"
               body-markdown
               "\n\n</div>\n"
               "</div>\n"
               "</div>\n"
               "</aside>")]
      (is (= expected output))
      (is (= unsafe-title (.-title input)))
      (is (= body-markdown (.-body input))))))
