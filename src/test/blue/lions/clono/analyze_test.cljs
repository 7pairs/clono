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
            [blue.lions.clono.analyze :as analyze]
            [blue.lions.clono.spec :as spec]))

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
        (t/is false "Exception should be thrown.")
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
    (let [documents-list [{:name "markdown.md" :type :chapters :ast "not-map"}
                          {:name "markdown.md" :type :chapters :ast nil}]]
      (doseq [documents documents-list]
        (try
          (analyze/create-toc-items documents)
          (t/is false "Exception should be thrown.")
          (catch js/Error e
            (t/is (= "Invalid documents are given." (ex-message e)))
            (t/is (= {:value documents :spec ::spec/documents}
                     (ex-data e)))))))))

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
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)]
            (t/is (= "Node does not have slug." (ex-message e)))
            (t/is (= "markdown.md" (:file-name data)))
            (t/is (= node (:node data))))))))

  (t/testing "File name is invalid."
    (let [file-names [""
                      nil]]
      (doseq [file-name file-names]
        (try
          (analyze/create-heading-info file-name {:type "heading"})
          (t/is false "Exception should be thrown.")
          (catch js/Error e
            (t/is (= "Invalid file name is given." (ex-message e)))
            (t/is (= {:value file-name :spec ::spec/file-name}
                     (ex-data e)))))))))

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
    (let [documents-list [{:name "markdown.md" :type :chapters :ast "not-map"}
                          {:name "markdown.md" :type :chapters :ast nil}]]
      (doseq [documents documents-list]
        (try
          (analyze/create-heading-dic documents)
          (t/is false "Exception should be thrown.")
          (catch js/Error e
            (t/is (= "Invalid documents are given." (ex-message e)))
            (t/is (= {:value documents :spec ::spec/documents}
                     (ex-data e))))))))

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
        (t/is false "Exception should be thrown.")
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
        (t/is false "Exception should be thrown.")
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

(t/deftest non-seion->seion-test
  (t/testing "Seion is given."
    (t/is (= "あいうえお" (analyze/non-seion->seion "あいうえお"))))

  (t/testing "Dakuon is given."
    (t/is (= "あいうえお" (analyze/non-seion->seion "あいゔえお")))
    (t/is (= "かきくけこ" (analyze/non-seion->seion "がぎぐげご")))
    (t/is (= "さしすせそ" (analyze/non-seion->seion "ざじずぜぞ")))
    (t/is (= "たちつてと" (analyze/non-seion->seion "だぢづでど")))
    (t/is (= "はひふへほ" (analyze/non-seion->seion "ばびぶべぼ"))))

  (t/testing "Handakuon is given."
    (t/is (= "はひふへほ" (analyze/non-seion->seion "ぱぴぷぺぽ"))))

  (t/testing "Youon is given."
    (t/is (= "やゆよ" (analyze/non-seion->seion "ゃゅょ")))
    (t/is (= "わをん" (analyze/non-seion->seion "ゎをん"))))

  (t/testing "Sokuon is given."
    (t/is (= "たちつてと" (analyze/non-seion->seion "たちってと"))))

  (t/testing "Kogakimoji is given."
    (t/is (= "あいうえお" (analyze/non-seion->seion "ぁぃぅぇぉ")))))

(t/deftest onbiki->vowel-test
  (t/testing "Onbiki is given."
    (t/is (= "ああ" (analyze/onbiki->vowel "あー")))
    (t/is (= "きい" (analyze/onbiki->vowel "きー")))
    (t/is (= "くう" (analyze/onbiki->vowel "ぐー"))))

  (t/testing "Onbiki is not given."
    (t/is (= "あいうえお" (analyze/onbiki->vowel "あいうえお")))))

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

(t/deftest create-indices-test
  (t/testing "Indices are not duplicated."
    (t/is (= [{:type :caption :text "さ行"}
              {:type :item
               :text "索引"
               :ruby "さくいん"
               :urls ["markdown.html#index-1"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "text" :value "value1"}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "さくいん"}
                                  :children [{:type "text" :value "索引"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "text" :value "value2"}]}}])))
    (t/is (= [{:type :caption :text "あ行"}
              {:type :item
               :text "いい"
               :ruby "いい"
               :urls ["markdown.html#index-9"]}
              {:type :item
               :text "いー"
               :ruby "いー"
               :urls ["markdown.html#index-8"]}
              {:type :caption :text "か行"}
              {:type :item
               :text "かっと"
               :ruby "かっと"
               :urls ["markdown.html#index-5"]}
              {:type :item
               :text "かつと"
               :ruby "かつと"
               :urls ["markdown.html#index-4"]}
              {:type :caption :text "さ行"}
              {:type :item
               :text "しょう"
               :ruby "しょう"
               :urls ["markdown.html#index-7"]}
              {:type :item
               :text "しよう"
               :ruby "しよう"
               :urls ["markdown.html#index-6"]}
              {:type :caption :text "は行"}
              {:type :item
               :text "はい"
               :ruby "はい"
               :urls ["markdown.html#index-1"]}
              {:type :item
               :text "ばい"
               :ruby "ばい"
               :urls ["markdown.html#index-2"]}
              {:type :item
               :text "ぱい"
               :ruby "ぱい"
               :urls ["markdown.html#index-3"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "はい"}
                                  :children [{:type "text" :value "はい"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ばい"}
                                  :children [{:type "text" :value "ばい"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぱい"}
                                  :children [{:type "text" :value "ぱい"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "かつと"}
                                  :children [{:type "text" :value "かつと"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "かっと"}
                                  :children [{:type "text" :value "かっと"}]
                                  :id "index-5"
                                  :order 5}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "しよう"}
                                  :children [{:type "text" :value "しよう"}]
                                  :id "index-6"
                                  :order 6}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "しょう"}
                                  :children [{:type "text" :value "しょう"}]
                                  :id "index-7"
                                  :order 7}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "いー"}
                                  :children [{:type "text" :value "いー"}]
                                  :id "index-8"
                                  :order 8}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "いい"}
                                  :children [{:type "text" :value "いい"}]
                                  :id "index-9"
                                  :order 9}]}}]))))

  (t/testing "Indices are duplicated."
    (t/is (= [{:type :caption :text "さ行"}
              {:type :item
               :text "索引"
               :ruby "さくいん"
               :urls ["markdown.html#index-1"
                      "markdown.html#index-2"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "text" :value "value1"}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "さくいん"}
                                  :children [{:type "text" :value "索引"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "text" :value "value2"}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "さくいん"}
                                  :children [{:type "text" :value "索引"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "text" :value "value3"}]}}])))
    (t/is (= [{:type :caption :text "英数字"}
              {:type :item
               :text "Indexデータ"
               :ruby "indexでーた"
               :urls ["markdown1.html#index-1"
                      "markdown2.html#index-2"]}]
             (analyze/create-indices
              [{:name "markdown1.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "text" :value "value1"}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "indexでーた"}
                                  :children [{:type "text"
                                              :value "Indexデータ"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "text" :value "value2"}]}}
               {:name "markdown2.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "text" :value "value3"}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "indexでーた"}
                                  :children [{:type "text"
                                              :value "Indexデータ"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "text" :value "value4"}]}}])))
    (t/is (= [{:type :caption :text "英数字"}
              {:type :item
               :text "9m"
               :ruby "9m"
               :urls ["markdown.html#index-1"
                      "markdown.html#index-2"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "text" :value "value1"}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "9m"}
                                  :children [{:type "text" :value "9m"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "text" :value "value2"}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "9m"}
                                  :children [{:type "text" :value "9m"}]
                                  :id "index-2"
                                  :order 2}]}}]))))

  (t/testing "Indices contain number and alphabet."
    (t/is (= [{:type :caption :text "英数字"}
              {:type :item
               :text "1m"
               :ruby "1m"
               :urls ["markdown.html#index-3"]}
              {:type :item
               :text "2m"
               :ruby "2m"
               :urls ["markdown.html#index-4"]}
              {:type :item
               :text "5m"
               :ruby "5m"
               :urls ["markdown.html#index-1"]}
              {:type :item
               :text "6m"
               :ruby "6m"
               :urls ["markdown.html#index-5"]}
              {:type :item
               :text "9m"
               :ruby "9m"
               :urls ["markdown.html#index-2"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "5m"}
                                  :children [{:type "text" :value "5m"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "9m"}
                                  :children [{:type "text" :value "9m"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "1m"}
                                  :children [{:type "text" :value "1m"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "2m"}
                                  :children [{:type "text" :value "2m"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "6m"}
                                  :children [{:type "text" :value "6m"}]
                                  :id "index-5"
                                  :order 5}]}}])))
    (t/is (= [{:type :caption :text "英数字"}
              {:type :item
               :text "12m"
               :ruby "12m"
               :urls ["markdown.html#index-4"]}
              {:type :item
               :text "15m"
               :ruby "15m"
               :urls ["markdown.html#index-1"]}
              {:type :item
               :text "21m"
               :ruby "21m"
               :urls ["markdown.html#index-3"]}
              {:type :item
               :text "26m"
               :ruby "26m"
               :urls ["markdown.html#index-5"]}
              {:type :item
               :text "29m"
               :ruby "29m"
               :urls ["markdown.html#index-2"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "15m"}
                                  :children [{:type "text" :value "15m"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "29m"}
                                  :children [{:type "text" :value "29m"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "21m"}
                                  :children [{:type "text" :value "21m"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "12m"}
                                  :children [{:type "text" :value "12m"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "26m"}
                                  :children [{:type "text" :value "26m"}]
                                  :id "index-5"
                                  :order 5}]}}])))
    (t/is (= [{:type :caption :text "英数字"}
              {:type :item
               :text "15m"
               :ruby "15m"
               :urls ["markdown.html#index-2"]}
              {:type :item
               :text "19m"
               :ruby "19m"
               :urls ["markdown.html#index-1"]}
              {:type :item
               :text "1m"
               :ruby "1m"
               :urls ["markdown.html#index-3"]}
              {:type :item
               :text "2m"
               :ruby "2m"
               :urls ["markdown.html#index-5"]}
              {:type :item
               :text "m1"
               :ruby "m1"
               :urls ["markdown.html#index-4"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "19m"}
                                  :children [{:type "text" :value "19m"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "15m"}
                                  :children [{:type "text" :value "15m"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "1m"}
                                  :children [{:type "text" :value "1m"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "m1"}
                                  :children [{:type "text" :value "m1"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "2m"}
                                  :children [{:type "text" :value "2m"}]
                                  :id "index-5"
                                  :order 5}]}}]))))

  (t/testing "Indices are alphabetic."
    (t/is (= [{:type :caption :text "英数字"}
              {:type :item
               :text "A"
               :ruby "a"
               :urls ["markdown.html#index-3"]}
              {:type :item
               :text "B"
               :ruby "b"
               :urls ["markdown.html#index-1"]}
              {:type :item
               :text "C"
               :ruby "c"
               :urls ["markdown.html#index-4"]}
              {:type :item
               :text "D"
               :ruby "d"
               :urls ["markdown.html#index-2"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "b"}
                                  :children [{:type "text" :value "B"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "d"}
                                  :children [{:type "text" :value "D"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "a"}
                                  :children [{:type "text" :value "A"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "c"}
                                  :children [{:type "text" :value "C"}]
                                  :id "index-4"
                                  :order 4}]}}])))
    (t/is (= [{:type :caption :text "英数字"}
              {:type :item
               :text "AA"
               :ruby "aa"
               :urls ["markdown.html#index-1"]}
              {:type :item
               :text "AAA"
               :ruby "aaa"
               :urls ["markdown.html#index-3"]}
              {:type :item
               :text "AC"
               :ruby "ac"
               :urls ["markdown.html#index-5"]}
              {:type :item
               :text "AI"
               :ruby "ai"
               :urls ["markdown.html#index-4"]}
              {:type :item
               :text "BA"
               :ruby "ba"
               :urls ["markdown.html#index-2"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "aa"}
                                  :children [{:type "text" :value "AA"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ba"}
                                  :children [{:type "text" :value "BA"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "aaa"}
                                  :children [{:type "text" :value "AAA"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ai"}
                                  :children [{:type "text" :value "AI"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ac"}
                                  :children [{:type "text" :value "AC"}]
                                  :id "index-5"
                                  :order 5}]}}]))))

  (t/testing "Indices contain alphabet and hiragana."
    (t/is (= [{:type :caption :text "英数字"}
              {:type :item
               :text "B"
               :ruby "b"
               :urls ["markdown.html#index-2"]}
              {:type :item
               :text "B1リーグ"
               :ruby "b1りーぐ"
               :urls ["markdown.html#index-5"]}
              {:type :item
               :text "Bリーグ"
               :ruby "bりーぐ"
               :urls ["markdown.html#index-1"]}
              {:type :item
               :text "C"
               :ruby "c"
               :urls ["markdown.html#index-4"]}
              {:type :caption :text "あ行"}
              {:type :item
               :text "あ"
               :ruby "あ"
               :urls ["markdown.html#index-3"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "bりーぐ"}
                                  :children [{:type "text" :value "Bリーグ"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "b"}
                                  :children [{:type "text" :value "B"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あ"}
                                  :children [{:type "text" :value "あ"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "c"}
                                  :children [{:type "text" :value "C"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "b1りーぐ"}
                                  :children [{:type "text" :value "B1リーグ"}]
                                  :id "index-5"
                                  :order 5}]}}]))))

  (t/testing "Indices are seion."
    (t/is (= [{:type :caption :text "あ行"}
              {:type :item
               :text "あ"
               :ruby "あ"
               :urls ["markdown.html#index-3"]}
              {:type :item
               :text "い"
               :ruby "い"
               :urls ["markdown.html#index-2"]}
              {:type :item
               :text "う"
               :ruby "う"
               :urls ["markdown.html#index-5"]}
              {:type :item
               :text "え"
               :ruby "え"
               :urls ["markdown.html#index-1"]}
              {:type :item
               :text "お"
               :ruby "お"
               :urls ["markdown.html#index-4"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "え"}
                                  :children [{:type "text" :value "え"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "い"}
                                  :children [{:type "text" :value "い"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あ"}
                                  :children [{:type "text" :value "あ"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "お"}
                                  :children [{:type "text" :value "お"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "う"}
                                  :children [{:type "text" :value "う"}]
                                  :id "index-5"
                                  :order 5}]}}])))
    (t/is (= [{:type :caption :text "あ行"}
              {:type :item
               :text "あ"
               :ruby "あ"
               :urls ["markdown.html#index-7"]}
              {:type :item
               :text "ああ"
               :ruby "ああ"
               :urls ["markdown.html#index-3"]}
              {:type :item
               :text "あえ"
               :ruby "あえ"
               :urls ["markdown.html#index-1"]}
              {:type :item
               :text "あお"
               :ruby "あお"
               :urls ["markdown.html#index-6"]}
              {:type :item
               :text "お"
               :ruby "お"
               :urls ["markdown.html#index-5"]}
              {:type :item
               :text "おあ"
               :ruby "おあ"
               :urls ["markdown.html#index-4"]}
              {:type :item
               :text "おい"
               :ruby "おい"
               :urls ["markdown.html#index-2"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あえ"}
                                  :children [{:type "text" :value "あえ"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "おい"}
                                  :children [{:type "text" :value "おい"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ああ"}
                                  :children [{:type "text" :value "ああ"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "おあ"}
                                  :children [{:type "text" :value "おあ"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "お"}
                                  :children [{:type "text" :value "お"}]
                                  :id "index-5"
                                  :order 5}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あお"}
                                  :children [{:type "text" :value "あお"}]
                                  :id "index-6"
                                  :order 6}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あ"}
                                  :children [{:type "text" :value "あ"}]
                                  :id "index-7"
                                  :order 7}]}}]))))

  (t/testing "Indices contain dakuon."
    (t/is (= [{:type :caption :text "か行"}
              {:type :item
               :text "か"
               :ruby "か"
               :urls ["markdown.html#index-6"]}
              {:type :item
               :text "が"
               :ruby "が"
               :urls ["markdown.html#index-10"]}
              {:type :item
               :text "き"
               :ruby "き"
               :urls ["markdown.html#index-7"]}
              {:type :item
               :text "ぎ"
               :ruby "ぎ"
               :urls ["markdown.html#index-4"]}
              {:type :item
               :text "く"
               :ruby "く"
               :urls ["markdown.html#index-3"]}
              {:type :item
               :text "ぐ"
               :ruby "ぐ"
               :urls ["markdown.html#index-1"]}
              {:type :item
               :text "け"
               :ruby "け"
               :urls ["markdown.html#index-2"]}
              {:type :item
               :text "げ"
               :ruby "げ"
               :urls ["markdown.html#index-8"]}
              {:type :item
               :text "こ"
               :ruby "こ"
               :urls ["markdown.html#index-9"]}
              {:type :item
               :text "ご"
               :ruby "ご"
               :urls ["markdown.html#index-5"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぐ"}
                                  :children [{:type "text" :value "ぐ"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "け"}
                                  :children [{:type "text" :value "け"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "く"}
                                  :children [{:type "text" :value "く"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぎ"}
                                  :children [{:type "text" :value "ぎ"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ご"}
                                  :children [{:type "text" :value "ご"}]
                                  :id "index-5"
                                  :order 5}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "か"}
                                  :children [{:type "text" :value "か"}]
                                  :id "index-6"
                                  :order 6}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "き"}
                                  :children [{:type "text" :value "き"}]
                                  :id "index-7"
                                  :order 7}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "げ"}
                                  :children [{:type "text" :value "げ"}]
                                  :id "index-8"
                                  :order 8}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "こ"}
                                  :children [{:type "text" :value "こ"}]
                                  :id "index-9"
                                  :order 9}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "が"}
                                  :children [{:type "text" :value "が"}]
                                  :id "index-10"
                                  :order 10}]}}])))
    (t/is (= [{:type :caption :text "か行"}
              {:type :item
               :text "か"
               :ruby "か"
               :urls ["markdown.html#index-5"]}
              {:type :item
               :text "かか"
               :ruby "かか"
               :urls ["markdown.html#index-9"]}
              {:type :item
               :text "かが"
               :ruby "かが"
               :urls ["markdown.html#index-2"]}
              {:type :item
               :text "かぎ"
               :ruby "かぎ"
               :urls ["markdown.html#index-3"]}
              {:type :item
               :text "かく"
               :ruby "かく"
               :urls ["markdown.html#index-4"]}
              {:type :item
               :text "き"
               :ruby "き"
               :urls ["markdown.html#index-6"]}
              {:type :item
               :text "きか"
               :ruby "きか"
               :urls ["markdown.html#index-8"]}
              {:type :item
               :text "きが"
               :ruby "きが"
               :urls ["markdown.html#index-1"]}
              {:type :item
               :text "きご"
               :ruby "きご"
               :urls ["markdown.html#index-7"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "きが"}
                                  :children [{:type "text" :value "きが"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "かが"}
                                  :children [{:type "text" :value "かが"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "かぎ"}
                                  :children [{:type "text" :value "かぎ"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "かく"}
                                  :children [{:type "text" :value "かく"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "か"}
                                  :children [{:type "text" :value "か"}]
                                  :id "index-5"
                                  :order 5}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "き"}
                                  :children [{:type "text" :value "き"}]
                                  :id "index-6"
                                  :order 6}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "きご"}
                                  :children [{:type "text" :value "きご"}]
                                  :id "index-7"
                                  :order 7}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "きか"}
                                  :children [{:type "text" :value "きか"}]
                                  :id "index-8"
                                  :order 8}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "かか"}
                                  :children [{:type "text" :value "かか"}]
                                  :id "index-9"
                                  :order 9}]}}]))))

  (t/testing "Indices contain dakuon and handakuon."
    (t/is (= [{:type :caption :text "は行"}
              {:type :item
               :text "は"
               :ruby "は"
               :urls ["markdown.html#index-3"]}
              {:type :item
               :text "ば"
               :ruby "ば"
               :urls ["markdown.html#index-8"]}
              {:type :item
               :text "ぱ"
               :ruby "ぱ"
               :urls ["markdown.html#index-15"]}
              {:type :item
               :text "ひ"
               :ruby "ひ"
               :urls ["markdown.html#index-11"]}
              {:type :item
               :text "び"
               :ruby "び"
               :urls ["markdown.html#index-10"]}
              {:type :item
               :text "ぴ"
               :ruby "ぴ"
               :urls ["markdown.html#index-1"]}
              {:type :item
               :text "ふ"
               :ruby "ふ"
               :urls ["markdown.html#index-12"]}
              {:type :item
               :text "ぶ"
               :ruby "ぶ"
               :urls ["markdown.html#index-9"]}
              {:type :item
               :text "ぷ"
               :ruby "ぷ"
               :urls ["markdown.html#index-4"]}
              {:type :item
               :text "へ"
               :ruby "へ"
               :urls ["markdown.html#index-5"]}
              {:type :item
               :text "べ"
               :ruby "べ"
               :urls ["markdown.html#index-2"]}
              {:type :item
               :text "ぺ"
               :ruby "ぺ"
               :urls ["markdown.html#index-13"]}
              {:type :item
               :text "ほ"
               :ruby "ほ"
               :urls ["markdown.html#index-6"]}
              {:type :item
               :text "ぼ"
               :ruby "ぼ"
               :urls ["markdown.html#index-7"]}
              {:type :item
               :text "ぽ"
               :ruby "ぽ"
               :urls ["markdown.html#index-14"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぴ"}
                                  :children [{:type "text" :value "ぴ"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "べ"}
                                  :children [{:type "text" :value "べ"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "は"}
                                  :children [{:type "text" :value "は"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぷ"}
                                  :children [{:type "text" :value "ぷ"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "へ"}
                                  :children [{:type "text" :value "へ"}]
                                  :id "index-5"
                                  :order 5}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ほ"}
                                  :children [{:type "text" :value "ほ"}]
                                  :id "index-6"
                                  :order 6}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぼ"}
                                  :children [{:type "text" :value "ぼ"}]
                                  :id "index-7"
                                  :order 7}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ば"}
                                  :children [{:type "text" :value "ば"}]
                                  :id "index-8"
                                  :order 8}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぶ"}
                                  :children [{:type "text" :value "ぶ"}]
                                  :id "index-9"
                                  :order 9}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "び"}
                                  :children [{:type "text" :value "び"}]
                                  :id "index-10"
                                  :order 10}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ひ"}
                                  :children [{:type "text" :value "ひ"}]
                                  :id "index-11"
                                  :order 11}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ふ"}
                                  :children [{:type "text" :value "ふ"}]
                                  :id "index-12"
                                  :order 12}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぺ"}
                                  :children [{:type "text" :value "ぺ"}]
                                  :id "index-13"
                                  :order 13}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぽ"}
                                  :children [{:type "text" :value "ぽ"}]
                                  :id "index-14"
                                  :order 14}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぱ"}
                                  :children [{:type "text" :value "ぱ"}]
                                  :id "index-15"
                                  :order 15}]}}])))
    (t/is (= [{:type :caption :text "は行"}
              {:type :item
               :text "は"
               :ruby "は"
               :urls ["markdown.html#index-10"]}
              {:type :item
               :text "ぱ"
               :ruby "ぱ"
               :urls ["markdown.html#index-8"]}
              {:type :item
               :text "はは"
               :ruby "はは"
               :urls ["markdown.html#index-6"]}
              {:type :item
               :text "はば"
               :ruby "はば"
               :urls ["markdown.html#index-7"]}
              {:type :item
               :text "はぱ"
               :ruby "はぱ"
               :urls ["markdown.html#index-2"]}
              {:type :item
               :text "ばは"
               :ruby "ばは"
               :urls ["markdown.html#index-13"]}
              {:type :item
               :text "ばぱ"
               :ruby "ばぱ"
               :urls ["markdown.html#index-1"]}
              {:type :item
               :text "ぱぴ"
               :ruby "ぱぴ"
               :urls ["markdown.html#index-9"]}
              {:type :item
               :text "ぱふ"
               :ruby "ぱふ"
               :urls ["markdown.html#index-11"]}
              {:type :item
               :text "ぱぶ"
               :ruby "ぱぶ"
               :urls ["markdown.html#index-3"]}
              {:type :item
               :text "ぶは"
               :ruby "ぶは"
               :urls ["markdown.html#index-14"]}
              {:type :item
               :text "ぶば"
               :ruby "ぶば"
               :urls ["markdown.html#index-4"]}
              {:type :item
               :text "ぺぺ"
               :ruby "ぺぺ"
               :urls ["markdown.html#index-12"]}
              {:type :item
               :text "ぼ"
               :ruby "ぼ"
               :urls ["markdown.html#index-5"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ばぱ"}
                                  :children [{:type "text" :value "ばぱ"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "はぱ"}
                                  :children [{:type "text" :value "はぱ"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぱぶ"}
                                  :children [{:type "text" :value "ぱぶ"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぶば"}
                                  :children [{:type "text" :value "ぶば"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぼ"}
                                  :children [{:type "text" :value "ぼ"}]
                                  :id "index-5"
                                  :order 5}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "はは"}
                                  :children [{:type "text" :value "はは"}]
                                  :id "index-6"
                                  :order 6}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "はば"}
                                  :children [{:type "text" :value "はば"}]
                                  :id "index-7"
                                  :order 7}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぱ"}
                                  :children [{:type "text" :value "ぱ"}]
                                  :id "index-8"
                                  :order 8}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぱぴ"}
                                  :children [{:type "text" :value "ぱぴ"}]
                                  :id "index-9"
                                  :order 9}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "は"}
                                  :children [{:type "text" :value "は"}]
                                  :id "index-10"
                                  :order 10}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぱふ"}
                                  :children [{:type "text" :value "ぱふ"}]
                                  :id "index-11"
                                  :order 11}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぺぺ"}
                                  :children [{:type "text" :value "ぺぺ"}]
                                  :id "index-12"
                                  :order 12}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ばは"}
                                  :children [{:type "text" :value "ばは"}]
                                  :id "index-13"
                                  :order 13}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ぶは"}
                                  :children [{:type "text" :value "ぶは"}]
                                  :id "index-14"
                                  :order 14}]}}]))))

  (t/testing "Indices contain sokuon."
    (t/is (= [{:type :caption :text "あ行"}
              {:type :item
               :text "あ"
               :ruby "あ"
               :urls ["markdown.html#index-6"]}
              {:type :item
               :text "あっ"
               :ruby "あっ"
               :urls ["markdown.html#index-2"]}
              {:type :item
               :text "あつ"
               :ruby "あつ"
               :urls ["markdown.html#index-1"]}
              {:type :item
               :text "あつあ"
               :ruby "あつあ"
               :urls ["markdown.html#index-4"]}
              {:type :item
               :text "あつさ"
               :ruby "あつさ"
               :urls ["markdown.html#index-5"]}
              {:type :item
               :text "あっと"
               :ruby "あっと"
               :urls ["markdown.html#index-3"]}
              {:type :item
               :text "あつと"
               :ruby "あつと"
               :urls ["markdown.html#index-7"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あつ"}
                                  :children [{:type "text" :value "あつ"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あっ"}
                                  :children [{:type "text" :value "あっ"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あっと"}
                                  :children [{:type "text" :value "あっと"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あつあ"}
                                  :children [{:type "text" :value "あつあ"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あつさ"}
                                  :children [{:type "text" :value "あつさ"}]
                                  :id "index-5"
                                  :order 5}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あ"}
                                  :children [{:type "text" :value "あ"}]
                                  :id "index-6"
                                  :order 6}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あつと"}
                                  :children [{:type "text" :value "あつと"}]
                                  :id "index-7"
                                  :order 7}]}}]))))

  (t/testing "Indices contain youon."
    (t/is (= [{:type :caption :text "な行"}
              {:type :item
               :text "にゃ"
               :ruby "にゃ"
               :urls ["markdown.html#index-6"]}
              {:type :item
               :text "にや"
               :ruby "にや"
               :urls ["markdown.html#index-3"]}
              {:type :item
               :text "にゃあ"
               :ruby "にゃあ"
               :urls ["markdown.html#index-8"]}
              {:type :item
               :text "にやり"
               :ruby "にやり"
               :urls ["markdown.html#index-4"]}
              {:type :item
               :text "にゅ"
               :ruby "にゅ"
               :urls ["markdown.html#index-1"]}
              {:type :item
               :text "にょ"
               :ruby "にょ"
               :urls ["markdown.html#index-2"]}
              {:type :item
               :text "によ"
               :ruby "によ"
               :urls ["markdown.html#index-7"]}
              {:type :item
               :text "にょん"
               :ruby "にょん"
               :urls ["markdown.html#index-5"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "にゅ"}
                                  :children [{:type "text" :value "にゅ"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "にょ"}
                                  :children [{:type "text" :value "にょ"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "にや"}
                                  :children [{:type "text" :value "にや"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "にやり"}
                                  :children [{:type "text" :value "にやり"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "にょん"}
                                  :children [{:type "text" :value "にょん"}]
                                  :id "index-5"
                                  :order 5}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "にゃ"}
                                  :children [{:type "text" :value "にゃ"}]
                                  :id "index-6"
                                  :order 6}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "によ"}
                                  :children [{:type "text" :value "によ"}]
                                  :id "index-7"
                                  :order 7}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "にゃあ"}
                                  :children [{:type "text" :value "にゃあ"}]
                                  :id "index-8"
                                  :order 8}]}}]))))

  (t/testing "Indices contain onbiki."
    (t/is (= [{:type :caption :text "あ行"}
              {:type :item
               :text "あ"
               :ruby "あ"
               :urls ["markdown.html#index-3"]}
              {:type :item
               :text "ああ"
               :ruby "ああ"
               :urls ["markdown.html#index-5"]}
              {:type :item
               :text "あー"
               :ruby "あー"
               :urls ["markdown.html#index-6"]}
              {:type :item
               :text "あか"
               :ruby "あか"
               :urls ["markdown.html#index-12"]}
              {:type :item
               :text "いー"
               :ruby "いー"
               :urls ["markdown.html#index-4"]}
              {:type :item
               :text "いか"
               :ruby "いか"
               :urls ["markdown.html#index-1"]}
              {:type :item
               :text "うー"
               :ruby "うー"
               :urls ["markdown.html#index-9"]}
              {:type :item
               :text "うか"
               :ruby "うか"
               :urls ["markdown.html#index-11"]}
              {:type :item
               :text "えー"
               :ruby "えー"
               :urls ["markdown.html#index-7"]}
              {:type :item
               :text "えか"
               :ruby "えか"
               :urls ["markdown.html#index-10"]}
              {:type :item
               :text "おあ"
               :ruby "おあ"
               :urls ["markdown.html#index-8"]}
              {:type :item
               :text "おー"
               :ruby "おー"
               :urls ["markdown.html#index-2"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "いか"}
                                  :children [{:type "text" :value "いか"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "おー"}
                                  :children [{:type "text" :value "おー"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あ"}
                                  :children [{:type "text" :value "あ"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "いー"}
                                  :children [{:type "text" :value "いー"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ああ"}
                                  :children [{:type "text" :value "ああ"}]
                                  :id "index-5"
                                  :order 5}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あー"}
                                  :children [{:type "text" :value "あー"}]
                                  :id "index-6"
                                  :order 6}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "えー"}
                                  :children [{:type "text" :value "えー"}]
                                  :id "index-7"
                                  :order 7}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "おあ"}
                                  :children [{:type "text" :value "おあ"}]
                                  :id "index-8"
                                  :order 8}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "うー"}
                                  :children [{:type "text" :value "うー"}]
                                  :id "index-9"
                                  :order 9}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "えか"}
                                  :children [{:type "text" :value "えか"}]
                                  :id "index-10"
                                  :order 10}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "うか"}
                                  :children [{:type "text" :value "うか"}]
                                  :id "index-11"
                                  :order 11}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あか"}
                                  :children [{:type "text" :value "あか"}]
                                  :id "index-12"
                                  :order 12}]}}]))))

  (t/testing "All captions exist."
    (t/is (= [{:type :caption :text "英数字"}
              {:type :item
               :text "Mt.Fuji"
               :ruby "mt.fuji"
               :urls ["markdown.html#index-9"]}
              {:type :caption :text "あ行"}
              {:type :item
               :text "阿寒摩周"
               :ruby "あかんましゅう"
               :urls ["markdown.html#index-5"]}
              {:type :caption :text "か行"}
              {:type :item
               :text "釧路湿原"
               :ruby "くしろしつげん"
               :urls ["markdown.html#index-3"]}
              {:type :caption :text "さ行"}
              {:type :item
               :text "知床"
               :ruby "しれとこ"
               :urls ["markdown.html#index-7"]}
              {:type :caption :text "た行"}
              {:type :item
               :text "大雪山"
               :ruby "たいせつざん"
               :urls ["markdown.html#index-4"]}
              {:type :caption :text "な行"}
              {:type :item
               :text "日光"
               :ruby "にっこう"
               :urls ["markdown.html#index-10"]}
              {:type :caption :text "は行"}
              {:type :item
               :text "磐梯朝日"
               :ruby "ばんだいあさひ"
               :urls ["markdown.html#index-2"]}
              {:type :caption :text "ま行"}
              {:type :item
               :text "南アルプス"
               :ruby "みなみあるぷす"
               :urls ["markdown.html#index-6"]}
              {:type :caption :text "や行"}
              {:type :item
               :text "吉野熊野"
               :ruby "よしのくまの"
               :urls ["markdown.html#index-8"]}
              {:type :caption :text "ら行"}
              {:type :item
               :text "利尻礼文サロベツ"
               :ruby "りしりれぶんさろべつ"
               :urls ["markdown.html#index-11"]}
              {:type :caption :text "わ行"}
              {:type :item
               :text "若狭湾"
               :ruby "わかさわん"
               :urls ["markdown.html#index-1"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "わかさわん"}
                                  :children [{:type "text" :value "若狭湾"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ばんだいあさひ"}
                                  :children [{:type "text" :value "磐梯朝日"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "くしろしつげん"}
                                  :children [{:type "text" :value "釧路湿原"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "たいせつざん"}
                                  :children [{:type "text" :value "大雪山"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あかんましゅう"}
                                  :children [{:type "text" :value "阿寒摩周"}]
                                  :id "index-5"
                                  :order 5}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "みなみあるぷす"}
                                  :children [{:type "text"
                                              :value "南アルプス"}]
                                  :id "index-6"
                                  :order 6}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "しれとこ"}
                                  :children [{:type "text" :value "知床"}]
                                  :id "index-7"
                                  :order 7}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "よしのくまの"}
                                  :children [{:type "text" :value "吉野熊野"}]
                                  :id "index-8"
                                  :order 8}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "mt.fuji"}
                                  :children [{:type "text" :value "Mt.Fuji"}]
                                  :id "index-9"
                                  :order 9}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "にっこう"}
                                  :children [{:type "text" :value "日光"}]
                                  :id "index-10"
                                  :order 10}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "りしりれぶんさろべつ"}
                                  :children [{:type "text"
                                              :value "利尻礼文サロベツ"}]
                                  :id "index-11"
                                  :order 11}]}}]))))

  (t/testing "Some captions do not exist."
    (t/is (= [{:type :caption :text "あ行"}
              {:type :item
               :text "阿寒摩周"
               :ruby "あかんましゅう"
               :urls ["markdown.html#index-4"]}
              {:type :caption :text "か行"}
              {:type :item
               :text "釧路湿原"
               :ruby "くしろしつげん"
               :urls ["markdown.html#index-9"]}
              {:type :caption :text "さ行"}
              {:type :item
               :text "知床"
               :ruby "しれとこ"
               :urls ["markdown.html#index-2"]}
              {:type :caption :text "た行"}
              {:type :item
               :text "大雪山"
               :ruby "たいせつざん"
               :urls ["markdown.html#index-1"]}
              {:type :caption :text "な行"}
              {:type :item
               :text "日光"
               :ruby "にっこう"
               :urls ["markdown.html#index-7"]}
              {:type :caption :text "は行"}
              {:type :item
               :text "磐梯朝日"
               :ruby "ばんだいあさひ"
               :urls ["markdown.html#index-8"]}
              {:type :caption :text "ま行"}
              {:type :item
               :text "南アルプス"
               :ruby "みなみあるぷす"
               :urls ["markdown.html#index-6"]}
              {:type :caption :text "や行"}
              {:type :item
               :text "吉野熊野"
               :ruby "よしのくまの"
               :urls ["markdown.html#index-3"]}
              {:type :caption :text "ら行"}
              {:type :item
               :text "利尻礼文サロベツ"
               :ruby "りしりれぶんさろべつ"
               :urls ["markdown.html#index-10"]}
              {:type :caption :text "わ行"}
              {:type :item
               :text "若狭湾"
               :ruby "わかさわん"
               :urls ["markdown.html#index-5"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "たいせつざん"}
                                  :children [{:type "text" :value "大雪山"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "しれとこ"}
                                  :children [{:type "text" :value "知床"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "よしのくまの"}
                                  :children [{:type "text" :value "吉野熊野"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あかんましゅう"}
                                  :children [{:type "text" :value "阿寒摩周"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "わかさわん"}
                                  :children [{:type "text" :value "若狭湾"}]
                                  :id "index-5"
                                  :order 5}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "みなみあるぷす"}
                                  :children [{:type "text"
                                              :value "南アルプス"}]
                                  :id "index-6"
                                  :order 6}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "にっこう"}
                                  :children [{:type "text" :value "日光"}]
                                  :id "index-7"
                                  :order 7}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ばんだいあさひ"}
                                  :children [{:type "text" :value "磐梯朝日"}]
                                  :id "index-8"
                                  :order 8}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "くしろしつげん"}
                                  :children [{:type "text" :value "釧路湿原"}]
                                  :id "index-9"
                                  :order 9}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "りしりれぶんさろべつ"}
                                  :children [{:type "text"
                                              :value "利尻礼文サロベツ"}]
                                  :id "index-10"
                                  :order 10}]}}])))
    (t/is (= [{:type :caption :text "英数字"}
              {:type :item
               :text "Mt.Fuji"
               :ruby "mt.fuji"
               :urls ["markdown.html#index-8"]}
              {:type :caption :text "あ行"}
              {:type :item
               :text "阿寒摩周"
               :ruby "あかんましゅう"
               :urls ["markdown.html#index-5"]}
              {:type :caption :text "か行"}
              {:type :item
               :text "釧路湿原"
               :ruby "くしろしつげん"
               :urls ["markdown.html#index-7"]}
              {:type :caption :text "さ行"}
              {:type :item
               :text "知床"
               :ruby "しれとこ"
               :urls ["markdown.html#index-10"]}
              {:type :caption :text "た行"}
              {:type :item
               :text "大雪山"
               :ruby "たいせつざん"
               :urls ["markdown.html#index-9"]}
              {:type :caption :text "な行"}
              {:type :item
               :text "日光"
               :ruby "にっこう"
               :urls ["markdown.html#index-2"]}
              {:type :caption :text "は行"}
              {:type :item
               :text "磐梯朝日"
               :ruby "ばんだいあさひ"
               :urls ["markdown.html#index-3"]}
              {:type :caption :text "ま行"}
              {:type :item
               :text "南アルプス"
               :ruby "みなみあるぷす"
               :urls ["markdown.html#index-6"]}
              {:type :caption :text "や行"}
              {:type :item
               :text "吉野熊野"
               :ruby "よしのくまの"
               :urls ["markdown.html#index-4"]}
              {:type :caption :text "わ行"}
              {:type :item
               :text "若狭湾"
               :ruby "わかさわん"
               :urls ["markdown.html#index-1"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "わかさわん"}
                                  :children [{:type "text" :value "若狭湾"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "にっこう"}
                                  :children [{:type "text" :value "日光"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ばんだいあさひ"}
                                  :children [{:type "text" :value "磐梯朝日"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "よしのくまの"}
                                  :children [{:type "text" :value "吉野熊野"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あかんましゅう"}
                                  :children [{:type "text" :value "阿寒摩周"}]
                                  :id "index-5"
                                  :order 5}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "みなみあるぷす"}
                                  :children [{:type "text"
                                              :value "南アルプス"}]
                                  :id "index-6"
                                  :order 6}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "くしろしつげん"}
                                  :children [{:type "text" :value "釧路湿原"}]
                                  :id "index-7"
                                  :order 7}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "mt.fuji"}
                                  :children [{:type "text" :value "Mt.Fuji"}]
                                  :id "index-8"
                                  :order 8}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "たいせつざん"}
                                  :children [{:type "text" :value "大雪山"}]
                                  :id "index-9"
                                  :order 9}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "しれとこ"}
                                  :children [{:type "text" :value "知床"}]
                                  :id "index-10"
                                  :order 10}]}}])))
    (t/is (= [{:type :caption :text "英数字"}
              {:type :item
               :text "Mt.Fuji"
               :ruby "mt.fuji"
               :urls ["markdown.html#index-5"]}
              {:type :caption :text "あ行"}
              {:type :item
               :text "阿寒摩周"
               :ruby "あかんましゅう"
               :urls ["markdown.html#index-9"]}
              {:type :caption :text "か行"}
              {:type :item
               :text "釧路湿原"
               :ruby "くしろしつげん"
               :urls ["markdown.html#index-1"]}
              {:type :caption :text "さ行"}
              {:type :item
               :text "知床"
               :ruby "しれとこ"
               :urls ["markdown.html#index-2"]}
              {:type :caption :text "た行"}
              {:type :item
               :text "大雪山"
               :ruby "たいせつざん"
               :urls ["markdown.html#index-3"]}
              {:type :caption :text "な行"}
              {:type :item
               :text "日光"
               :ruby "にっこう"
               :urls ["markdown.html#index-4"]}
              {:type :caption :text "は行"}
              {:type :item
               :text "磐梯朝日"
               :ruby "ばんだいあさひ"
               :urls ["markdown.html#index-8"]}
              {:type :caption :text "ま行"}
              {:type :item
               :text "南アルプス"
               :ruby "みなみあるぷす"
               :urls ["markdown.html#index-7"]}
              {:type :caption :text "わ行"}
              {:type :item
               :text "若狭湾"
               :ruby "わかさわん"
               :urls ["markdown.html#index-6"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "くしろしつげん"}
                                  :children [{:type "text" :value "釧路湿原"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "しれとこ"}
                                  :children [{:type "text" :value "知床"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "たいせつざん"}
                                  :children [{:type "text" :value "大雪山"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "にっこう"}
                                  :children [{:type "text" :value "日光"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "mt.fuji"}
                                  :children [{:type "text" :value "Mt.Fuji"}]
                                  :id "index-5"
                                  :order 5}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "わかさわん"}
                                  :children [{:type "text" :value "若狭湾"}]
                                  :id "index-6"
                                  :order 6}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "みなみあるぷす"}
                                  :children [{:type "text"
                                              :value "南アルプス"}]
                                  :id "index-7"
                                  :order 7}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ばんだいあさひ"}
                                  :children [{:type "text" :value "磐梯朝日"}]
                                  :id "index-8"
                                  :order 8}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あかんましゅう"}
                                  :children [{:type "text" :value "阿寒摩周"}]
                                  :id "index-9"
                                  :order 9}]}}])))
    (t/is (= [{:type :caption :text "英数字"}
              {:type :item
               :text "Mt.Fuji"
               :ruby "mt.fuji"
               :urls ["markdown.html#index-3"]}
              {:type :caption :text "あ行"}
              {:type :item
               :text "阿寒摩周"
               :ruby "あかんましゅう"
               :urls ["markdown.html#index-6"]}
              {:type :caption :text "か行"}
              {:type :item
               :text "釧路湿原"
               :ruby "くしろしつげん"
               :urls ["markdown.html#index-8"]}
              {:type :caption :text "さ行"}
              {:type :item
               :text "知床"
               :ruby "しれとこ"
               :urls ["markdown.html#index-7"]}
              {:type :caption :text "た行"}
              {:type :item
               :text "大雪山"
               :ruby "たいせつざん"
               :urls ["markdown.html#index-9"]}
              {:type :caption :text "な行"}
              {:type :item
               :text "日光"
               :ruby "にっこう"
               :urls ["markdown.html#index-10"]}
              {:type :caption :text "は行"}
              {:type :item
               :text "磐梯朝日"
               :ruby "ばんだいあさひ"
               :urls ["markdown.html#index-5"]}
              {:type :caption :text "ま行"}
              {:type :item
               :text "南アルプス"
               :ruby "みなみあるぷす"
               :urls ["markdown.html#index-4"]}
              {:type :caption :text "や行"}
              {:type :item
               :text "吉野熊野"
               :ruby "よしのくまの"
               :urls ["markdown.html#index-1"]}
              {:type :caption :text "ら行"}
              {:type :item
               :text "利尻礼文サロベツ"
               :ruby "りしりれぶんさろべつ"
               :urls ["markdown.html#index-2"]}]
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "よしのくまの"}
                                  :children [{:type "text" :value "吉野熊野"}]
                                  :id "index-1"
                                  :order 1}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "りしりれぶんさろべつ"}
                                  :children [{:type "text"
                                              :value "利尻礼文サロベツ"}]
                                  :id "index-2"
                                  :order 2}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "mt.fuji"}
                                  :children [{:type "text" :value "Mt.Fuji"}]
                                  :id "index-3"
                                  :order 3}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "みなみあるぷす"}
                                  :children [{:type "text"
                                              :value "南アルプス"}]
                                  :id "index-4"
                                  :order 4}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "ばんだいあさひ"}
                                  :children [{:type "text" :value "磐梯朝日"}]
                                  :id "index-5"
                                  :order 5}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "あかんましゅう"}
                                  :children [{:type "text" :value "阿寒摩周"}]
                                  :id "index-6"
                                  :order 6}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "しれとこ"}
                                  :children [{:type "text" :value "知床"}]
                                  :id "index-7"
                                  :order 7}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "くしろしつげん"}
                                  :children [{:type "text" :value "釧路湿原"}]
                                  :id "index-8"
                                  :order 8}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "たいせつざん"}
                                  :children [{:type "text" :value "大雪山"}]
                                  :id "index-9"
                                  :order 9}
                                 {:type "textDirective"
                                  :name "index"
                                  :attributes {:ruby "にっこう"}
                                  :children [{:type "text" :value "日光"}]
                                  :id "index-10"
                                  :order 10}]}}]))))

  (t/testing "Documents do not have indices."
    (t/is (= []
             (analyze/create-indices
              [{:name "markdown.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "text" :value "value"}]}}]))))

  (t/testing "Documents have indices without order."
    (let [node {:type "textDirective"
                :name "index"
                :attributes {:ruby "さくいん"}
                :children [{:type "text" :value "索引"}]
                :id "index-1"}]
      (try
        (analyze/create-indices
         [{:name "markdown.md"
           :type :chapters
           :ast {:type "root"
                 :children [{:type "text" :value "value1"}
                            node
                            {:type "text" :value "value2"}]}}])
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)]
            (t/is (= "Node is invalid." (ex-message e)))
            (t/is (= "markdown" (:base-name data)))
            (t/is (= node (:node data)))
            (t/is (= :order (:missing data))))))))

  (t/testing "Documents have indices without ID."
    (let [node {:type "textDirective"
                :name "index"
                :attributes {:ruby "さくいん"}
                :children [{:type "text" :value "索引"}]
                :order 1}]
      (try
        (analyze/create-indices
         [{:name "markdown.md"
           :type :chapters
           :ast {:type "root"
                 :children [{:type "text" :value "value1"}
                            node
                            {:type "text" :value "value2"}]}}])
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)]
            (t/is (= "Node is invalid." (ex-message e)))
            (t/is (= "markdown" (:base-name data)))
            (t/is (= node (:node data)))
            (t/is (= :id (:missing data)))))))))
