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
