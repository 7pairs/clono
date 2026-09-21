(ns clono.research.plugin-renderer-contract-test
  (:require
   ["./custom_column_renderer.js" :refer [customColumnRenderer]]
   [cljs.test :refer [deftest is testing]]
   [clono.research.plugin-renderer-contract :as renderer-contract]))

(def body-markdown
  (str "本文には**強調**と`コード`がある。\n\n"
       "- 箇条書き\n"
       "- 二つ目"))

(def expected-markdown
  (str "<aside class=\"clono-column\">\n\n"
       "<p class=\"clono-column-title\">"
       "A &amp; B &lt;unsafe&gt; &quot;quoted&quot; &#39;single&#39;"
       "</p>\n\n"
       body-markdown
       "\n\n</aside>"))

(deftest default-column-renderer-test
  (testing "When the default column renderer receives a title and body, then the current column Markdown is returned"
    (let [input (js/Object.freeze
                 #js {:title "A & B <unsafe> \"quoted\" 'single'"
                      :body body-markdown})
          output (renderer-contract/render-column
                  renderer-contract/default-column-renderer
                  input)]
      (is (= expected-markdown output))
      (is (= "A & B <unsafe> \"quoted\" 'single'" (.-title input)))
      (is (= body-markdown (.-body input))))))

(deftest custom-column-renderer-test
  (testing "When a custom column renderer receives a title and body, then nested wrappers and title spans are returned"
    (let [input (js/Object.freeze
                 #js {:title "休憩 & <雑談>"
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
               "休憩 &amp; &lt;雑談&gt;</span>\n"
               "</p>\n"
               "<div class=\"custom-column-body\">\n\n"
               body-markdown
               "\n\n</div>\n"
               "</div>\n"
               "</div>\n"
               "</aside>")]
      (is (= expected output))
      (is (= "休憩 & <雑談>" (.-title input)))
      (is (= body-markdown (.-body input))))))
