; Copyright 2025 HASEBA Junya
; 
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
; 
;     http://www.apache.org/licenses/LICENSE-2.0
; 
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns blue.lions.clono.analyze-test
  (:require [cljs.test :as t]
            [blue.lions.clono.analyze :as analyze]))

(t/deftest create-toc-item-test
  (t/testing "Node has single text."
    (t/is (= {:depth 1
              :caption "text1"
              :url "markdown.html#text1"}
             (analyze/create-toc-item
              "markdown.md"
              {:type "heading"
               :depth 1
               :children [{:type "text" :value "text1"}]
               :slug "text1"}))))

  (t/testing "Node has multiple texts."
    (t/is (= {:depth 2
              :caption "text2text3"
              :url "markdown.html#text2text3"}
             (analyze/create-toc-item
              "markdown.md"
              {:type "heading"
               :depth 2
               :children [{:type "text" :value "text2"}
                          {:type "text" :value "text3"}]
               :slug "text2text3"}))))

  (t/testing "Node does not have slug."
    (let [node {:type "heading"
                :depth 3
                :children [{:type "text" :value "text4"}]}]
      (try
        (analyze/create-toc-item "markdown.md" node)
        (catch js/Error e
          (let [data (ex-data e)]
            (t/is (= "Node does not have slug." (ex-message e)))
            (t/is (= "markdown.md" (:file-name data)))
            (t/is (= node (:node data)))))))))

(t/deftest create-toc-items-test
  (t/testing "Documents have headings."
    (t/is (= [{:depth 1 :caption "text1" :url "markdown1.html#text1"}
              {:depth 2 :caption "text3" :url "markdown1.html#text3"}
              {:depth 2 :caption "text5" :url "markdown1.html#text5"}
              {:depth 1 :caption "text6" :url "markdown2.html#text6"}]
             (analyze/create-toc-items
              [{:name "markdown1.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "heading"
                                  :depth 1
                                  :children [{:type "text" :value "text1"}]
                                  :slug "text1"}
                                 {:type "text" :value "text2"}
                                 {:type "heading"
                                  :depth 2
                                  :children [{:type "textDirective"
                                              :name "label"
                                              :attributes {:id "ID"}}
                                             {:type "text" :value "text3"}]
                                  :slug "text3"}
                                 {:type "text" :value "text4"}
                                 {:type "heading"
                                  :depth 2
                                  :children [{:type "text" :value "text5"}]
                                  :slug "text5"}]}}
               {:name "markdown2.md"
                :type :appendices
                :ast {:type "root"
                      :children [{:type "heading"
                                  :depth 1
                                  :children [{:type "text" :value "text6"}]
                                  :slug "text6"}]}}]))))

  (t/testing "Documents do not have headings."
    (t/is (= []
             (analyze/create-toc-items
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "text" :value "value"}]}}]))))

  (t/testing "Documents have invalid AST."
    (t/are [documents] (thrown-with-msg?
                        js/Error #"Assert failed:"
                        (analyze/create-toc-items documents))
      [{:name "markdown.md" :type :chapters :ast "not-map"}]
      [{:name "markdown.md" :type :chapters :ast nil}])))

(t/deftest has-valid-id-or-root-depth?-test
  (t/testing "Node has valid ID."
    (t/is (true? (analyze/has-valid-id-or-root-depth?
                  {:type "heading"
                   :depth 2
                   :children [{:type "textDirective"
                               :name "label"
                               :attributes {:id "ID"}}]}))))

  (t/testing "Node has root depth."
    (t/is (true? (analyze/has-valid-id-or-root-depth?
                  {:type "heading"
                   :depth 1
                   :children [{:type "text" :value "value"}]}))))

  (t/testing "Node has valid ID and root depth."
    (t/is (true? (analyze/has-valid-id-or-root-depth?
                  {:type "heading"
                   :depth 1
                   :children [{:type "textDirective"
                               :name "label"
                               :attributes {:id "ID"}}]}))))

  (t/testing "Node does not have valid ID or root depth."
    (t/is (false? (analyze/has-valid-id-or-root-depth?
                   {:type "heading"
                    :depth 2
                    :children [{:type "text" :value "value"}]}))))

  (t/testing "Node has label without attributes."
    (t/is (false? (analyze/has-valid-id-or-root-depth?
                   {:type "heading"
                    :depth 2
                    :children [{:type "textDirective"
                                :name "label"}]}))))

  (t/testing "Node has label without ID."
    (t/is (false? (analyze/has-valid-id-or-root-depth?
                   {:type "heading"
                    :depth 2
                    :children [{:type "textDirective"
                                :name "label"
                                :attributes {:not-id "notID"}}]}))))

  (t/testing "Node has multiple labels."
    (t/is (true? (analyze/has-valid-id-or-root-depth?
                  {:type "heading"
                   :depth 2
                   :children [{:type "textDirective"
                               :name "label"
                               :attributes {:id "ID1"}}
                              {:type "textDirective"
                               :name "label"
                               :attributes {:id "ID2"}}]})))))

(t/deftest create-heading-info-test
  (t/testing "Node has valid ID."
    (t/is (= {:id "markdown|ID"
              :depth 2
              :caption "text1"
              :url "markdown.html#text1"}
             (analyze/create-heading-info
              "markdown.md"
              {:type "heading"
               :depth 2
               :children [{:type "textDirective"
                           :name "label"
                           :attributes {:id "ID"}}
                          {:type "text" :value "text1"}]
               :slug "text1"}))))

  (t/testing "Node has root depth."
    (t/is (= {:id "markdown"
              :depth 1
              :caption "text2"
              :url "markdown.html#text2"}
             (analyze/create-heading-info
              "markdown.md"
              {:type "heading"
               :depth 1
               :children [{:type "text" :value "text2"}]
               :slug "text2"}))))

  (t/testing "Node has valid ID and root depth."
    (t/is (= {:id "markdown"
              :depth 1
              :caption "text3"
              :url "markdown.html#text3"}
             (analyze/create-heading-info
              "markdown.md"
              {:type "heading"
               :depth 1
               :children [{:type "textDirective"
                           :name "label"
                           :attributes {:id "ID"}}
                          {:type "text" :value "text3"}]
               :slug "text3"}))))

  (t/testing "Node does not have valid ID or root depth."
    (t/is (nil? (analyze/create-heading-info
                 "markdown.md"
                 {:type "heading"
                  :depth 2
                  :children [{:type "text" :value "text4"}]
                  :slug "text4"}))))

  (t/testing "Node does not have slug."
    (let [node {:type "heading"
                :depth 2
                :children [{:type "textDirective"
                            :name "label"
                            :attributes {:id "ID"}}
                           {:type "text" :value "text5"}]}]
      (try
        (analyze/create-heading-info "markdown.md" node)
        (catch js/Error e
          (let [data (ex-data e)]
            (t/is (= "Node does not have slug." (ex-message e)))
            (t/is (= "markdown.md" (:file-name data)))
            (t/is (= node (:node data))))))))

  (t/testing "File name is invalid."
    (t/are [file-name] (thrown-with-msg?
                        js/Error #"Assert failed:"
                        (analyze/create-heading-info file-name
                                                     {:type "heading"}))
      ""
      nil)))

(t/deftest create-heading-dic-test
  (t/testing "Documents have headings."
    (t/is (= {"markdown1" {:id "markdown1"
                           :depth 1
                           :caption "value1"
                           :url "markdown1.html#value1"}
              "markdown1|ID" {:id "markdown1|ID"
                              :depth 2
                              :caption "value3"
                              :url "markdown1.html#value3"}
              "markdown2" {:id "markdown2"
                           :depth 1
                           :caption "value6"
                           :url "markdown2.html#value6"}}
             (analyze/create-heading-dic
              [{:name "markdown1.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "heading"
                                  :depth 1
                                  :children [{:type "text" :value "value1"}]
                                  :slug "value1"}
                                 {:type "text" :value "value2"}
                                 {:type "heading"
                                  :depth 2
                                  :children [{:type "textDirective"
                                              :name "label"
                                              :attributes {:id "ID"}}
                                             {:type "text" :value "value3"}]
                                  :slug "value3"}
                                 {:type "text" :value "value4"}
                                 {:type "heading"
                                  :depth 2
                                  :children [{:type "text" :value "value5"}]
                                  :slug "value5"}]}}
               {:name "markdown2.md"
                :type :appendices
                :ast {:type "root"
                      :children [{:type "heading"
                                  :depth 1
                                  :children [{:type "text" :value "value6"}]
                                  :slug "value6"}]}}]))))

  (t/testing "Documents do not have headings."
    (t/is (= {}
             (analyze/create-heading-dic
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "text" :value "value"}]}}]))))

  (t/testing "Documents have invalid AST."
    (t/are [documents] (thrown-with-msg?
                        js/Error #"Assert failed:"
                        (analyze/create-heading-dic documents))
      {:name "markdown.md" :type :chapters :ast "not-map"}
      {:name "markdown.md" :type :chapters :ast nil}))

  (t/testing "Documents have headings without slug."
    (t/is (thrown-with-msg?
           js/Error #"Node does not have slug\."
           (analyze/create-heading-dic
            [{:name "markdown.md"
              :type :chapters
              :ast {:type "root"
                    :children [{:type "heading"
                                :depth 1
                                :children [{:type "text"
                                            :value "value"}]}]}}])))))

(t/deftest create-footnote-dic-test
  (t/testing "Documents have footnotes."
    (t/is (= {"markdown1|footnote1" {:type "text" :value "Footnote-1."}
              "markdown1|footnote2" {:type "text" :value "Footnote-2."}
              "markdown2|footnote3" {:type "text" :value "Footnote-3."}}
             (analyze/create-footnote-dic
              [{:name "markdown1.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "footnoteDefinition"
                                  :identifier "footnote1"
                                  :children [{:type "text"
                                              :value "Footnote-1."}]}
                                 {:type "text" :value "I am not footnote."}
                                 {:type "footnoteDefinition"
                                  :identifier "footnote2"
                                  :children [{:type "text"
                                              :value "Footnote-2."}]}]}}
               {:name "markdown2.md"
                :type :appendices
                :ast {:type "root"
                      :children [{:type "footnoteDefinition"
                                  :identifier "footnote3"
                                  :children [{:type "text"
                                              :value "Footnote-3."}]}]}}]))))

  (t/testing "Documents do not have footnotes."
    (t/is (= {}
             (analyze/create-footnote-dic
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "text" :value "value"}]}}]))))

  (t/testing "Documents have footnotes without children."
    (t/is (= {}
             (analyze/create-footnote-dic
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "footnoteDefinition"
                                  :identifier "footnote"}]}}])))))

(t/deftest build-index-entry-test
  (t/testing "Node is valid."
    (t/is (= {:order 1
              :text "テスト"
              :ruby "てすと"
              :url "markdown.html#index-1"}
             (analyze/build-index-entry
              "markdown"
              {:type "textDirective"
               :name "index"
               :attributes {:ruby "てすと"}
               :children [{:type "text" :value "テスト"}]
               :id "index-1"
               :order 1}))))

  (t/testing "Node does not have order."
    (let [node {:type "textDirective"
                :name "index"
                :attributes {:ruby "てすと"}
                :children [{:type "text" :value "テスト"}]
                :id "index-1"}]
      (try
        (analyze/build-index-entry "markdown" node)
        (catch js/Error e
          (let [data (ex-data e)]
            (t/is (= "Node is invalid." (ex-message e)))
            (t/is (= "markdown" (:base-name data)))
            (t/is (= node (:node data)))
            (t/is (= :order (:missing data))))))))

  (t/testing "Node does not have ID."
    (let [node {:type "textDirective"
                :name "index"
                :attributes {:ruby "てすと"}
                :children [{:type "text" :value "テスト"}]
                :order 1}]
      (try
        (analyze/build-index-entry "markdown" node)
        (catch js/Error e
          (let [data (ex-data e)]
            (t/is (= "Node is invalid." (ex-message e)))
            (t/is (= "markdown" (:base-name data)))
            (t/is (= node (:node data)))
            (t/is (= :id (:missing data)))))))))

(t/deftest english-ruby?-test
  (t/testing "Ruby is English."
    (t/are [ruby] (true? (analyze/english-ruby? ruby))
      "ruby"
      "12345"
      "!!!!!"
      "rubyルビ")))

  (t/testing "Ruby is not English."
    (t/are [ruby] (false? (analyze/english-ruby? ruby))
      "ルビ"
      "ルビruby"))

(t/deftest normalize-hiragana-test
  (t/testing "Seion is given."
    (t/is (= "あいうえお" (analyze/normalize-hiragana "あいうえお"))))

  (t/testing "Dakuon is given."
    (t/is (= "あいうえお" (analyze/normalize-hiragana "あいゔえお")))
    (t/is (= "かきくけこ" (analyze/normalize-hiragana "がぎぐげご")))
    (t/is (= "さしすせそ" (analyze/normalize-hiragana "ざじずぜぞ")))
    (t/is (= "たちつてと" (analyze/normalize-hiragana "だぢづでど")))
    (t/is (= "はひふへほ" (analyze/normalize-hiragana "ばびぶべぼ"))))

  (t/testing "Handakuon is given."
    (t/is (= "はひふへほ" (analyze/normalize-hiragana "ぱぴぷぺぽ"))))

  (t/testing "Youon is given."
    (t/is (= "やゆよ" (analyze/normalize-hiragana "ゃゅょ")))
    (t/is (= "わをん" (analyze/normalize-hiragana "ゎをん"))))

  (t/testing "Sokuon is given."
    (t/is (= "たちつてと" (analyze/normalize-hiragana "たちってと"))))

  (t/testing "Kogakimoji is given."
    (t/is (= "あいうえお" (analyze/normalize-hiragana "ぁぃぅぇぉ"))))

  (t/testing "Onbiki is given."
    (t/is (= "ああ" (analyze/normalize-hiragana "あー")))
    (t/is (= "きい" (analyze/normalize-hiragana "きー")))
    (t/is (= "くう" (analyze/normalize-hiragana "ぐー")))))

(t/deftest ruby->caption-test
  (t/testing "Ruby is English."
    (t/is (= "英数字" (analyze/ruby->caption "ruby"))))

  (t/testing "Ruby is Hiragana."
    (t/is (= "あ行" (analyze/ruby->caption "あかさたな")))
    (t/is (= "か行" (analyze/ruby->caption "きしちにひ")))
    (t/is (= "か行" (analyze/ruby->caption "ぐずづぬぶ")))
    (t/is (= "さ行" (analyze/ruby->caption "せてねへめ")))
    (t/is (= "さ行" (analyze/ruby->caption "ぞどのぼも")))
    (t/is (= "た行" (analyze/ruby->caption "たなはまや")))
    (t/is (= "た行" (analyze/ruby->caption "ぢにびみり")))
    (t/is (= "な行" (analyze/ruby->caption "ぬふむゆる")))
    (t/is (= "は行" (analyze/ruby->caption "へめれえけ")))
    (t/is (= "は行" (analyze/ruby->caption "ぼもよろを")))
    (t/is (= "は行" (analyze/ruby->caption "ぱまやらわ")))
    (t/is (= "ま行" (analyze/ruby->caption "みりいきし")))
    (t/is (= "や行" (analyze/ruby->caption "ゆるうくす")))
    (t/is (= "ら行" (analyze/ruby->caption "ろをおこそ")))
    (t/is (= "わ行" (analyze/ruby->caption "わあかさた"))))

  (t/testing "Ruby is invalid."
    (t/is (= "その他" (analyze/ruby->caption "！あかさた")))))

(t/deftest insert-row-captions-test
  (t/testing "All captions are required."
    (t/is (= [{:type :caption :text "英数字"}
              {:type :item :text "ABC" :ruby "abc" :urls ["url01"]}
              {:type :caption :text "あ行"}
              {:type :item :text "あいう" :ruby "あいう" :urls ["url02"]}
              {:type :caption :text "か行"}
              {:type :item :text "かきく" :ruby "かきく" :urls ["url03"]}
              {:type :caption :text "さ行"}
              {:type :item :text "さしす" :ruby "さしす" :urls ["url04"]}
              {:type :caption :text "た行"}
              {:type :item :text "たちつ" :ruby "たちつ" :urls ["url05"]}
              {:type :caption :text "な行"}
              {:type :item :text "なにぬ" :ruby "なにぬ" :urls ["url06"]}
              {:type :caption :text "は行"}
              {:type :item :text "はひふ" :ruby "はひふ" :urls ["url07"]}
              {:type :caption :text "ま行"}
              {:type :item :text "まみむ" :ruby "まみむ" :urls ["url08"]}
              {:type :caption :text "や行"}
              {:type :item :text "やゆよ" :ruby "やゆよ" :urls ["url09"]}
              {:type :caption :text "ら行"}
              {:type :item :text "らりる" :ruby "らりる" :urls ["url10"]}
              {:type :caption :text "わ行"}
              {:type :item :text "わをん" :ruby "わをん" :urls ["url11"]}]
             (analyze/insert-row-captions
              [{:type :item :text "ABC" :ruby "abc" :urls ["url01"]}
               {:type :item :text "あいう" :ruby "あいう" :urls ["url02"]}
               {:type :item :text "かきく" :ruby "かきく" :urls ["url03"]}
               {:type :item :text "さしす" :ruby "さしす" :urls ["url04"]}
               {:type :item :text "たちつ" :ruby "たちつ" :urls ["url05"]}
               {:type :item :text "なにぬ" :ruby "なにぬ" :urls ["url06"]}
               {:type :item :text "はひふ" :ruby "はひふ" :urls ["url07"]}
               {:type :item :text "まみむ" :ruby "まみむ" :urls ["url08"]}
               {:type :item :text "やゆよ" :ruby "やゆよ" :urls ["url09"]}
               {:type :item :text "らりる" :ruby "らりる" :urls ["url10"]}
               {:type :item :text "わをん" :ruby "わをん" :urls ["url11"]}]))))

  (t/testing "Groups have multiple items."
    (t/is (= [{:type :caption :text "英数字"}
              {:type :item :text "ABC" :ruby "abc" :urls ["url01"]}
              {:type :caption :text "か行"}
              {:type :item :text "かきく" :ruby "かきく" :urls ["url02"]}
              {:type :item :text "きくけ" :ruby "きくけ" :urls ["url03"]}
              {:type :caption :text "た行"}
              {:type :item :text "たちつ" :ruby "たちつ" :urls ["url04"]}
              {:type :item :text "ちつて" :ruby "ちつて" :urls ["url05"]}
              {:type :item :text "つてと" :ruby "つてと" :urls ["url06"]}]
             (analyze/insert-row-captions
              [{:type :item :text "ABC" :ruby "abc" :urls ["url01"]}
               {:type :item :text "かきく" :ruby "かきく" :urls ["url02"]}
               {:type :item :text "きくけ" :ruby "きくけ" :urls ["url03"]}
               {:type :item :text "たちつ" :ruby "たちつ" :urls ["url04"]}
               {:type :item :text "ちつて" :ruby "ちつて" :urls ["url05"]}
               {:type :item :text "つてと" :ruby "つてと" :urls ["url06"]}]))))

  (t/testing "Items are empty."
    (t/is (= [] (analyze/insert-row-captions [])))))
