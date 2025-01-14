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
  (:require [cljs.test :refer [are deftest is testing]]
            [clojure.string :as str]
            [blue.lions.clono.parse :as parse]))

(deftest remove-comments-test
  (testing "Target AST is single node."
    (are [target] (= target (parse/remove-comments target))
      {:type "html" :value "<!-- comment -->"}
      {:type "text" :value "not-comment"}))

  (testing "Target AST has comments."
    (is (= {:type "root" :children [{:type "text" :value "text1"}
                                    {:type "text" :value "text2"}]}
           (parse/remove-comments {:type "root"
                                   :children [{:type "text"
                                               :value "text1"}
                                              {:type "html"
                                               :value "<!-- comment -->"}
                                              {:type "text"
                                               :value "text2"}]}))))

  (testing "Target AST has children."
    (is (= {:type "root" :children [{:type "node" :children [{:type "text"
                                                              :value "text1"}
                                                             {:type "text"
                                                              :value "text2"}]}
                                    {:type "text" :value "text3"}]}
           (parse/remove-comments
            {:type "root" :children [{:type "node"
                                      :children [{:type "text"
                                                  :value "text1"}
                                                 {:type "html"
                                                  :value "<!-- comment -->"}
                                                 {:type "text"
                                                  :value "text2"}]}
                                     {:type "html" :value "<!-- comment -->"}
                                     {:type "text" :value "text3"}]}))))

  (testing "Target AST does not have comments."
    (is (= {:type "root" :children [{:type "node" :children [{:type "text"
                                                              :value "text1"}
                                                             {:type "text"
                                                              :value "text2"}]}
                                    {:type "text" :value "text3"}]}
           (parse/remove-comments {:type "root"
                                   :children [{:type "node"
                                               :children [{:type "text"
                                                           :value "text1"}
                                                          {:type "text"
                                                           :value "text2"}]}
                                              {:type "text"
                                               :value "text3"}]}))))

  (testing "Target AST has empty children."
    (is (= {:type "root" :children [{:type "node" :children []}]}
           (parse/remove-comments {:type "root"
                                   :children [{:type "node" :children []}]})))))

(deftest remove-positions-test
  (testing "Target AST has positions."
    (is (= {:type "root"}
           (parse/remove-positions {:type "root" :position 1}))))

  (testing "Target AST has children with positions."
    (is (= {:type "root" :children [{:type "child"} {:type "child"}]}
           (parse/remove-positions {:type "root"
                                    :position 1
                                    :children [{:type "child" :position 2}
                                               {:type "child" :position 3}]}))))

  (testing "Target AST has children without positions."
    (is (= {:type "root" :children [{:type "child"}]}
           (parse/remove-positions {:type "root"
                                    :position 1
                                    :children [{:type "child"}]}))))

  (testing "Target AST does not have positions."
    (are [ast] (= ast (parse/remove-positions ast))
      {:type "root"}
      {:type "root" :children [{:type "child"}]}))

  (testing "Target AST has empty children."
    (is (= {:type "root" :children []}
           (parse/remove-positions {:type "root" :children []})))))

(deftest generate-slug-test
  (testing "Target text contains upper case letters."
    (is (= "pascalcase" (parse/generate-slug "PascalCase")))
    (is (= "uppercase" (parse/generate-slug "UPPERCASE"))))

  (testing "Target text does not contain upper case letters."
    (is (= "lowercase" (parse/generate-slug "lowercase"))))

  (testing "Target text contains symbols."
    (is (= "helloworld" (parse/generate-slug "Hello,World!!")))
    (is (= "334" (parse/generate-slug "33:4"))))

  (testing "Target text contains spaces."
    (is (= "1-2-3" (parse/generate-slug "1 2 3"))))

  (testing "Target text contains Japanese letters."
    (is (= "日本語" (parse/generate-slug "日本語")))
    (is (= "ａｎｄｒｏｉｄ" (parse/generate-slug "Ａｎｄｒｏｉｄ")))
    (is (= "こんにちは世界" (parse/generate-slug "こんにちは、世界！"))))

  (testing "Target texts are duplicated."
    (is (= "duplicated" (parse/generate-slug "duplicated")))
    (is (= "duplicated-1" (parse/generate-slug "duplicated"))))

  (testing "Target text is invalid."
    (are [target] (thrown-with-msg? js/Error #"Assert failed:"
                                    (parse/generate-slug target))
      ""
      nil)))
