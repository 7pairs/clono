(ns clono.research.plugin-renderer-contract-test
  (:require
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
          output (renderer-contract/default-column-renderer input)]
      (is (= expected-markdown output))
      (is (= "A & B <unsafe> \"quoted\" 'single'" (.-title input)))
      (is (= body-markdown (.-body input))))))
