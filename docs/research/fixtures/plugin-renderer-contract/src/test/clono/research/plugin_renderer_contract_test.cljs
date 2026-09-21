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
