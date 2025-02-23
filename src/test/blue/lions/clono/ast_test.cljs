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
  (:require [cljs.test :as t]
            [blue.lions.clono.ast :as ast]))

(t/deftest comment?-test
  (t/testing "Node is comment."
    (t/are [node] (true? (ast/comment? node))
      {:type "html" :value "<!-- comment -->"}
      {:type "html" :value "<!--\nmulti-line comment\n-->"}))

  (t/testing "Node is not comment."
    (t/are [node] (false? (ast/comment? node))
      {:type "html" :value "<not-comment>"}
      {:type "html" :value "<!-- invalid -- comment -->"}
      {:type "html" :value ""}
      {:type "html"}
      {:type "text" :value "<!-- comment -->"})))

(t/deftest node-type?-test
  (t/testing "Types match."
    (t/is (true? (ast/node-type? "text" {:type "text" :value "value"}))))

  (t/testing "Types do not match."
    (t/is (false? (ast/node-type? "text" {:type "html" :value "<value>"}))))

  (t/testing "Type is invalid."
    (t/is (thrown-with-msg?
           js/Error #"Assert failed:"
           (ast/node-type? nil {:type "text" :value "value"}))))

  (t/testing "Node is invalid."
    (t/are [node] (thrown-with-msg? js/Error #"Assert failed:"
                                    (ast/node-type? "text" node))
      {:value "value"}
      "not-node"
      nil)))

(t/deftest text-directive?-test
  (t/testing "Node is text directive."
    (t/is (true? (ast/text-directive? "directive"
                                      {:type "textDirective"
                                       :name "directive"}))))

  (t/testing "Node is not text directive."
    (t/is (false? (ast/text-directive? "directive"
                                       {:type "text" :name "directive"}))))

  (t/testing "Directive names do not match."
    (t/is (false? (ast/text-directive? "directive"
                                       {:type "textDirective" :name "name"}))))

  (t/testing "Directive name is invalid."
    (t/is (thrown-with-msg? js/Error #"Assert failed:"
                            (ast/text-directive? nil {:type "textDirective"
                                                      :name "directive"}))))

  (t/testing "Node is invalid."
    (t/are [node] (thrown-with-msg? js/Error #"Assert failed:"
                                    (ast/text-directive? "directive" node))
      {:name "directive"}
      "not-node"
      nil)))

(t/deftest extract-nodes-test
  (t/testing "Node itself is target."
    (t/is (= [{:type "text" :value "value"}]
             (ast/extract-nodes
              #(= (:type %) "text")
              {:type "text" :value "value"}))))

  (t/testing "Node has target nodes."
    (t/is (= [{:type "text" :value "value1"}
              {:type "text" :value "value3"}]
           (ast/extract-nodes
            #(= (:type %) "text")
            {:type "root"
             :children [{:type "text" :value "value1"}
                        {:type "html" :value "<value2>"}
                        {:type "text" :value "value3"}]}))))

  (t/testing "Node has children."
    (t/is (= [{:type "text" :value "value1"}
              {:type "text" :value "value4"}
              {:type "text" :value "value7"}]
             (ast/extract-nodes
              #(= (:type %) "text")
              {:type "root"
               :children [{:type "text" :value "value1"}
                          {:type "html" :value "<value2>"}
                          {:type "node"
                           :children [{:type "html" :value "<value3>"}
                                      {:type "text" :value "value4"}
                                      {:type "html" :value "<value5>"}]}
                          {:type "html" :value "<value6>"}
                          {:type "text" :value "value7"}]}))))

  (t/testing "Node does not have target."
    (t/are [node] (= [] (ast/extract-nodes #(= (:type %) "text") node))
      {:type "root"}
      {:type "root" :children [{:type "html" :value "<value>"}]}))

  (t/testing "Predication function is invalid."
    (t/are [pred] (thrown-with-msg?
                   js/Error #"Assert failed:"
                   (ast/extract-nodes pred {:type "text" :value "value"}))
      "not-function"
      nil))

  (t/testing "Node is invalid."
    (t/are [node] (thrown-with-msg?
                   js/Error #"Assert failed:"
                   (ast/extract-nodes #(= (:type %) "text") node))
      "not-node"
      nil)))
