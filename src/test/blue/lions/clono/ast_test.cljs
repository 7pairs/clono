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
    (let [types ["node-type"
                 :not-string
                 nil]]
      (doseq [type types]
        (try
          (ast/node-type? type {:type "text" :value "value"})
          (catch js/Error e
            (t/is (= "Invalid node type." (ex-message e)))
            (t/is (= type (:type (ex-data e)))))))))

  (t/testing "Node is invalid."
    (let [nodes [{:value "value"}
                 "not-node"
                 nil]]
      (doseq [node nodes]
        (try
          (ast/node-type? "text" node)
          (catch js/Error e
            (t/is (= "Invalid node." (ex-message e)))
            (t/is (= node (:node (ex-data e))))))))))

(t/deftest text-directive?-test
  (t/testing "Node is text directive."
    (t/is (true? (ast/text-directive? "directive"
                                      {:type "textDirective"
                                       :name "directive"}))))

  (t/testing "Node is not text directive."
    (t/is (false? (ast/text-directive? "directive"
                                       {:type "text" :name "directive"}))))

  (t/testing "Directive names do not match."
    (let [directives ["directive-name"
                      nil]]
      (doseq [directive directives]
        (try
          (ast/text-directive? directive {:type "textDirective" :name "name"})
          (catch js/Error e
            (t/is (= "Invalid directive name." (ex-message e)))
            (t/is (= directive (:name (ex-data e)))))))))

  (t/testing "Node is invalid."
    (let [nodes [{:name "directive"}
                 "not-node"
                 nil]]
      (doseq [node nodes]
        (try
          (ast/text-directive? "directive" node)
          (catch js/Error e
            (t/is (= "Invalid node." (ex-message e)))
            (t/is (= node (:node (ex-data e))))))))))

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
    (let [preds ["not-function"
                 nil]]
      (doseq [pred preds]
        (try
          (ast/extract-nodes pred {:type "text" :value "value"})
          (catch js/Error e
            (t/is (= "Invalid predicate function." (ex-message e)))
            (t/is (= pred (:pred (ex-data e)))))))))

  (t/testing "Node is invalid."
    (let [nodes ["not-node"
                 nil]]
      (doseq [node nodes]
        (try
          (ast/extract-nodes #(= (:type %) "text") node)
          (catch js/Error e
            (t/is (= "Invalid node." (ex-message e)))
            (t/is (= node (:node (ex-data e))))))))))

(t/deftest get-type-test
  (t/testing "Node is text directive."
    (t/is (= "label" (ast/get-type {:type "textDirective"
                                    :name "label"}))))

  (t/testing "Node is container directive."
    (t/is (= "table" (ast/get-type {:type "containerDirective"
                                    :name "table"}))))

  (t/testing "Node is not directive."
    (t/is (= "text" (ast/get-type {:type "text" :value "value"}))))

  (t/testing "Node is text directive without name."
    (let [node {:type "textDirective"}]
      (try
        (ast/get-type node)
        (catch js/Error e
          (t/is (= "Invalid directive node." (ex-message e)))
          (t/is (= node (:node (ex-data e))))))))

  (t/testing "Node is container directive without name."
    (let [node {:type "containerDirective"}]
      (try
        (ast/get-type node)
        (catch js/Error e
          (t/is (= "Invalid directive node." (ex-message e)))
          (t/is (= node (:node (ex-data e)))))))))

(t/deftest directive-type-test
  (t/testing "Node is text directive."
    (t/is (= "label" (ast/directive-type {:type "textDirective"
                                          :name "label"}))))

  (t/testing "Node is container directive."
    (t/is (= "table" (ast/directive-type {:type "containerDirective"
                                          :name "table"}))))

  (t/testing "Node is invalid."
    (let [nodes [{:type "textDirective" :name "1nvalid"}
                 {:type "invalid"}
                 "not-node"
                 nil]]
      (doseq [node nodes]
        (try
          (ast/directive-type node)
          (catch js/Error e
            (t/is (= "Invalid directive node." (ex-message e)))
            (t/is (= node (:node (ex-data e))))))))))
