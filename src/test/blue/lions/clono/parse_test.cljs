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
