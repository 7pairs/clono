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
            [blue.lions.clono.parse :as parse]))

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

(t/deftest generate-slug-test
  (t/testing "Caption contains upper case letters."
    (t/is (= "pascalcase" (parse/generate-slug "PascalCase")))
    (t/is (= "uppercase" (parse/generate-slug "UPPERCASE"))))

  (t/testing "Caption does not contain upper case letters."
    (t/is (= "lowercase" (parse/generate-slug "lowercase"))))

  (t/testing "Caption contains symbols."
    (t/is (= "helloworld" (parse/generate-slug "Hello,World!!")))
    (t/is (= "334" (parse/generate-slug "33:4"))))

  (t/testing "Caption contains spaces."
    (t/is (= "1-2-3" (parse/generate-slug "1 2 3"))))

  (t/testing "Caption contains Japanese letters."
    (t/is (= "日本語" (parse/generate-slug "日本語")))
    (t/is (= "ａｎｄｒｏｉｄ" (parse/generate-slug "Ａｎｄｒｏｉｄ")))
    (t/is (= "こんにちは世界" (parse/generate-slug "こんにちは、世界！"))))

  (t/testing "Captions are duplicated."
    (t/is (= "duplicated" (parse/generate-slug "duplicated")))
    (t/is (= "duplicated-1" (parse/generate-slug "duplicated"))))

  (t/testing "Caption is invalid."
    (t/are [caption] (thrown-with-msg? js/Error #"Assert failed:"
                                       (parse/generate-slug caption))
      ""
      nil)))
