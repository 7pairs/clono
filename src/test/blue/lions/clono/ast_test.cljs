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

(ns blue.lions.clono.ast-test
  (:require [cljs.test :refer [are deftest is testing]]
            [blue.lions.clono.ast :as ast]))

(deftest comment?-test
  (testing "Target node is comment."
    (are [target] (true? (ast/comment? target))
      {:type "html" :value "<!-- comment -->"}
      {:type "html" :value "<!--\nmulti-line comment\n-->"}))

  (testing "Target node is not comment."
    (are [target] (false? (ast/comment? target))
      {:type "html" :value "<not-comment>"}
      {:type "html" :value "<!-- invalid -- comment -->"}
      {:type "html" :value ""}
      {:type "text" :value "<!-- comment -->"})))

(deftest extract-nodes-test
  (testing "AST itself is target."
    (is (= [{:type "text" :value "value"}]
           (ast/extract-nodes #(= (:type %) "text")
                              {:type "text" :value "value"}))))

  (testing "AST has target nodes."
    (is (= [{:type "text" :value "value1"} {:type "text" :value "value3"}]
           (ast/extract-nodes #(= (:type %) "text")
                              {:type "root"
                               :children [{:type "text" :value "value1"}
                                          {:type "html" :value "<value2>"}
                                          {:type "text" :value "value3"}]}))))

  (testing "AST has children."
    (is (= [{:type "text" :value "value1"}
            {:type "text" :value "value4"}
            {:type "text" :value "value7"}]
           (ast/extract-nodes
            #(= (:type %) "text")
            {:type "root"
             :children [{:type "text" :value "value1"}
                        {:type "html" :value "<value2>"}
                        {:type "node" :children [{:type "html"
                                                  :value "<value3>"}
                                                 {:type "text"
                                                  :value "value4"}
                                                 {:type "html"
                                                  :value "<value5>"}]}
                        {:type "html" :value "<value6>"}
                        {:type "text" :value "value7"}]}))))

  (testing "AST does not have target nodes."
    (are [target] (= [] (ast/extract-nodes #(= (:type %) "text") target))
      {:type "root"}
      {:type "root" :children [{:type "html" :value "<value>"}]}))

  (testing "Predication function is invalid."
    (are [target] (thrown-with-msg? js/Error #"Assert failed:"
                                    (ast/extract-nodes target {:type "text"
                                                               :value "value"}))
      "not-function"
      nil))

  (testing "AST is invalid."
    (are [target] (thrown-with-msg? js/Error #"Assert failed:"
                                    (ast/extract-nodes #(= (:type %) "text")
                                                       target))
      "not-node"
      nil)))
