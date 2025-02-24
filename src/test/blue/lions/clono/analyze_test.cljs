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
