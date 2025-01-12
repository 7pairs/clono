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
