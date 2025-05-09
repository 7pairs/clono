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

(ns blue.lions.clono.parse-test
  (:require [cljs.test :as t]
            [blue.lions.clono.parse :as parse]
            [blue.lions.clono.spec :as spec]))

(t/deftest remove-comments-test
  (t/testing "Target is single node."
    (t/are [node] (= node (parse/remove-comments node))
      {:type "html" :value "<!-- comment -->"}
      {:type "text" :value "not-comment"}))

  (t/testing "Node has comments."
    (t/is (= {:type "root" :children [{:type "text" :value "text1"}
                                      {:type "text" :value "text2"}]}
             (parse/remove-comments
              {:type "root" :children [{:type "text" :value "text1"}
                                       {:type "html" :value "<!-- comment -->"}
                                       {:type "text" :value "text2"}]}))))

  (t/testing "Node has children."
    (t/is (= {:type "root"
              :children [{:type "node"
                          :children [{:type "text" :value "text1"}
                                     {:type "text" :value "text2"}]}
                         {:type "text" :value "text3"}]}
             (parse/remove-comments
              {:type "root"
               :children [{:type "node"
                           :children [{:type "text" :value "text1"}
                                      {:type "html" :value "<!-- comment -->"}
                                      {:type "text" :value "text2"}]}
                          {:type "html" :value "<!-- comment -->"}
                          {:type "text" :value "text3"}]}))))

  (t/testing "Node has empty children."
    (t/is (= {:type "root" :children [{:type "node" :children []}]}
             (parse/remove-comments
              {:type "root" :children [{:type "node" :children []}]}))))

  (t/testing "Node does not have comments."
    (t/is (= {:type "root"
              :children [{:type "node"
                          :children [{:type "text" :value "text1"}
                                     {:type "text" :value "text2"}]}
                         {:type "text" :value "text3"}]}
             (parse/remove-comments
              {:type "root"
               :children [{:type "node"
                           :children [{:type "text" :value "text1"}
                                      {:type "text" :value "text2"}]}
                          {:type "text" :value "text3"}]})))))

(t/deftest remove-positions-test
  (t/testing "Node has positions."
    (t/is (= {:type "root"}
             (parse/remove-positions
              {:type "root" :position 1}))))

  (t/testing "Node has children with positions."
    (t/is (= {:type "root" :children [{:type "child"}
                                      {:type "child"}]}
             (parse/remove-positions
              {:type "root"
               :position 1
               :children [{:type "child" :position 2}
                          {:type "child" :position 3}]}))))

  (t/testing "Node has children without positions."
    (t/is (= {:type "root" :children [{:type "child"}]}
             (parse/remove-positions
              {:type "root" :position 1 :children [{:type "child"}]}))))

  (t/testing "Node does not have positions."
    (t/are [node] (= node (parse/remove-positions node))
      {:type "root"}
      {:type "root" :children [{:type "child"}]}))

  (t/testing "Node has empty children."
    (t/is (= {:type "root" :children []}
             (parse/remove-positions
              {:type "root" :children []})))))

(t/deftest generate-heading-slug-test
  (t/testing "Node has single text."
    (t/is (= "value2"
             (parse/generate-heading-slug
              {:type "heading"
               :children [{:type "html" :value "<value1>"}
                          {:type "text" :value "value2"}
                          {:type "html" :value "<value3>"}]}))))

  (t/testing "Node has multiple texts."
    (t/is (= "value1value3"
             (parse/generate-heading-slug
              {:type "heading" :children [{:type "text" :value "value1"}
                                          {:type "html" :value "<value2>"}
                                          {:type "text" :value "value3"}]}))))

  (t/testing "Node does not have texts."
    (let [node {:type "heading" :children [{:type "html" :value "<value>"}]}]
      (try
        (parse/generate-heading-slug node)
        (t/is false "Exception should be thrown.")
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)]
            (t/is (= "Failed to generate heading slug." (ex-message e)))
            (t/is (= node (:node data)))
            (t/is (= "" (:caption data)))
            (t/is (= "Invalid caption is given." (ex-message cause)))
            (t/is (= {:value "" :spec ::spec/caption} (ex-data cause)))))))))

(t/deftest add-heading-slugs-test
  (t/testing "Node has single heading."
    (t/is (= {:type "root"
              :children [{:type "text" :value "valueA1"}
                         {:type "heading"
                          :children [{:type "text" :value "valueA2"}]
                          :slug "valuea2"}
                         {:type "text" :value "valueA3"}]}
             (parse/add-heading-slugs
              {:type "root"
               :children [{:type "text" :value "valueA1"}
                          {:type "heading"
                           :children [{:type "text" :value "valueA2"}]}
                          {:type "text" :value "valueA3"}]}))))

  (t/testing "Node has multiple headings."
    (t/is (= {:type "root"
              :children [{:type "heading"
                          :children [{:type "text" :value "valueB1"}]
                          :slug "valueb1"}
                         {:type "text" :value "valueB2"}
                         {:type "heading"
                          :children [{:type "text" :value "valueB3"}]
                          :slug "valueb3"}]}
             (parse/add-heading-slugs
              {:type "root"
               :children [{:type "heading"
                           :children [{:type "text" :value "valueB1"}]}
                          {:type "text" :value "valueB2"}
                          {:type "heading"
                           :children [{:type "text" :value "valueB3"}]}]}))))

  (t/testing "Node does not have headings."
    (t/are [node] (= node (parse/add-heading-slugs node))
      {:type "root"}
      {:type "root" :children [{:type "text" :value "value"}]}))

  (t/testing "Node has empty children."
    (t/are [node] (= node (parse/add-heading-slugs node))
      {:type "root" :children []}
      {:type "root" :children [{:type "node" :children []}]})))

(t/deftest add-index-ids-test
  (let [create-generator (fn []
                           (let [counter (atom 0)]
                             #(swap! counter inc)))]

    (t/testing "Node has indices."
      (t/is (= {:type "root" :children [{:type "textDirective"
                                         :name "index"
                                         :id "index-1"
                                         :order 1}
                                        {:type "text" :value "value"}
                                        {:type "textDirective"
                                         :name "index"
                                         :id "index-2"
                                         :order 2}]}
               (parse/add-index-ids
                {:type "root" :children [{:type "textDirective"
                                          :name "index"}
                                         {:type "text" :value "value"}
                                         {:type "textDirective"
                                          :name "index"}]}
                (create-generator)))))

    (t/testing "Node has indices and children."
      (t/is (= {:type "root"
                :children [{:type "textDirective"
                            :name "index"
                            :id "index-1"
                            :order 1}
                           {:type "text" :value "value1"}
                           {:type "node"
                            :children [{:type "textDirective"
                                        :name "index"
                                        :id "index-2"
                                        :order 2}
                                       {:type "text" :value "value2"}
                                       {:type "textDirective"
                                        :name "index"
                                        :id "index-3"
                                        :order 3}]}
                           {:type "text" :value "value3"}
                           {:type "textDirective"
                            :name "index"
                            :id "index-4"
                            :order 4}]}
               (parse/add-index-ids
                {:type "root"
                 :children [{:type "textDirective" :name "index"}
                            {:type "text" :value "value1"}
                            {:type "node"
                             :children [{:type "textDirective" :name "index"}
                                        {:type "text" :value "value2"}
                                        {:type "textDirective" :name "index"}]}
                            {:type "text" :value "value3"}
                            {:type "textDirective" :name "index"}]}
                (create-generator)))))

    (t/testing "Node does not have indices."
      (t/are [node] (= node (parse/add-index-ids node (create-generator)))
        {:type "root"}
        {:type "root" :children [{:type "text" :value "value"}]}))

    (t/testing "Node has empty children."
      (t/are [node] (= node (parse/add-index-ids node (create-generator)))
        {:type "root" :children []}
        {:type "root" :children [{:type "node" :children []}]}))))

(t/deftest create-order-generator-test
  (t/testing "Initial order is set."
    (let [generator (parse/create-order-generator 5)]
      (t/is (= 6 (generator)))
      (t/is (= 7 (generator)))
      (t/is (= 8 (generator)))))

  (t/testing "Initial order is not set."
    (let [generator (parse/create-order-generator)]
      (t/is (= 1 (generator)))
      (t/is (= 2 (generator)))
      (t/is (= 3 (generator)))))

  (t/testing "Multiple generators are called."
    (let [generator1 (parse/create-order-generator)
          generator2 (parse/create-order-generator 10)]
      (t/is (= 1 (generator1)))
      (t/is (= 2 (generator1)))
      (t/is (= 11 (generator2)))
      (t/is (= 12 (generator2)))
      (t/is (= 3 (generator1)))
      (t/is (= 13 (generator2))))))

(t/deftest markdown->ast-test
  (t/testing "String is valid as Markdown."
    (t/is (= {:type "root"
              :children [{:type "heading"
                          :depth 1
                          :children [{:type "text" :value "Markdown"}]
                          :slug "markdown"}]}
             (parse/markdown->ast "# Markdown" (fn [] 1)))))

  (t/testing "String is empty."
    (t/is (= {:type "root" :children []}
             (parse/markdown->ast "" (fn [] 1)))))

  (t/testing "Markdown has footnotes."
    (t/is (= {:type "root"
              :children [{:type "paragraph"
                          :children [{:type "text" :value "I have footnote"}
                                     {:type "footnoteReference"
                                      :identifier "1"
                                      :label "1"}
                                     {:type "text" :value "."}]}
                         {:type "footnoteDefinition"
                          :identifier "1"
                          :label "1"
                          :children [{:type "paragraph"
                                      :children [{:type "text"
                                                  :value "I'm footnote."}]}]}]}
             (parse/markdown->ast
              "I have footnote[^1].\n\n[^1]: I'm footnote." (fn [] 1)))))

  (t/testing "Markdown has directives."
    (t/is (= {:type "root"
              :children [{:type "paragraph"
                          :children [{:type "textDirective"
                                      :name "element"
                                      :attributes {:key "value"}
                                      :children [{:type "text"
                                                  :value "content"}]}]}]}
             (parse/markdown->ast ":element[content]{key=value}" (fn [] 1))))))

(t/deftest parse-manuscripts-test
  (t/testing "All texts are valid as Markdown."
    (t/is (= [{:name "markdown1.md"
               :type :chapters
               :ast {:type "root"
                     :children [{:type "heading"
                                 :depth 1
                                 :children [{:type "text" :value "Markdown1"}]
                                 :slug "markdown1"}]}}
              {:name "markdown2.md"
               :type :appendices
               :ast {:type "root"
                     :children [{:type "heading"
                                 :depth 1
                                 :children [{:type "text" :value "Markdown2"}]
                                 :slug "markdown2"}]}}]
             (parse/parse-manuscripts
              [{:name "markdown1.md"
                :type :chapters
                :markdown "# Markdown1"}
               {:name "markdown2.md"
                :type :appendices
                :markdown "# Markdown2"}]
              (fn [] 1)))))

  (t/testing "Manuscript list is empty."
    (t/is (= [] (parse/parse-manuscripts [] (fn [] 1)))))

  (t/testing "Order generator is invalid."
    (try
      (parse/parse-manuscripts [{:name "markdown.md"
                                 :type :chapters
                                 :markdown "# Markdown"}]
                               nil)
      (t/is false "Exception should be thrown.")
      (catch js/Error e
        (t/is (= "Invalid generator is given." (ex-message e)))
        (t/is (= {:value nil :spec ::spec/function} (ex-data e)))))))
