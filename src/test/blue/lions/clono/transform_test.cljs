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

(ns blue.lions.clono.transform-test
  (:require [cljs.test :as t]
            [clojure.string :as str]
            [blue.lions.clono.transform :as transform]))

(t/deftest deletable-node?-test
  (t/testing "Node is deletable."
    (t/are [node] (true? (transform/deletable-node? node))
      {:type "footnoteDefinition"}
      {:type "label"}))

  (t/testing "Node is not deletable."
    (t/is (false? (transform/deletable-node? {:type "text"}))))

  (t/testing "Node does not have type."
    (let [node {:value "value"}]
      (try
        (transform/deletable-node? node)
        (catch js/Error e
          (let [data (ex-data e)]
            (t/is (= "Failed to determine node type." (ex-message e)))
            (t/is (= node (:node data)))
            (t/is (str/starts-with? (ex-message (:cause data))
                                    "Assert failed:")))))))

  (t/testing "Node has invalid type."
    (let [node {:type :not-string :value "value"}]
      (try
        (transform/deletable-node? node)
        (catch js/Error e
          (let [data (ex-data e)]
            (t/is (= "Failed to determine node type." (ex-message e)))
            (t/is (= node (:node data)))
            (t/is (str/starts-with? (ex-message (:cause data))
                                    "Assert failed:"))))))))
