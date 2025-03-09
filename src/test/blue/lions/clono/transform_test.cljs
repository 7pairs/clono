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
            [blue.lions.clono.log :as logger]
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

(t/deftest update-node-test
  (t/testing "Node is footnoteReference."
    (t/testing "ID is found."
      (t/is (= {:type "footnoteReference"
                :identifier "id1"
                :children [{:type "text" :value "value1"}]}
               (transform/update-node
                {:type "footnoteReference" :identifier "id1"}
                "name1"
                {:footnote {"name1|id1" {:type "text" :value "value1"}
                            "name1|id2" {:type "text" :value "value2"}
                            "name2|id1" {:type "text" :value "value3"}}}))))

    (t/testing "ID is not found."
      (let [node {:type "footnoteReference" :identifier "id1"}]
        (reset! logger/enabled? false)
        (reset! logger/entries [])
        (t/is (= {:type "footnoteReference" :identifier "id1" :children []}
                 (transform/update-node
                  node
                  "name1"
                  {:footnote {"name2|id1" {:type "text" :value "value1"}}})))
        (t/is (= [{:level :error
                   :message "Footnote is not found in dictionary."
                   :data {:key "name1|id1" :node node}}]
                 @logger/entries))))

    (t/testing "Footnote dictionary is not found."
      (let [node {:type "footnoteReference" :identifier "id1"}
            dics {:not-footnote {"key" "value"}}]
        (try
          (transform/update-node node "name" dics)
          (catch js/Error e
            (let [data (ex-data e)]
              (t/is (= "Footnote dictionary is not found." (ex-message e)))
              (t/is (= dics (:dics data)))
              (t/is (= node (:node data)))))))))

  (t/testing "Node does not to be updated."
    (let [node {:type "root" :children [{:type "text" :value "value1"}
                                        {:type "text" :value "value2"}]}]
      (t/is (= node (transform/update-node node "name" {})))))

  (t/testing "Node does not have type."
    (let [node {:value "value"}]
      (try
        (transform/update-node node "name" {})
        (catch js/Error e
          (let [data (ex-data e)]
            (t/is (= "Failed to determine node type for update."
                     (ex-message e)))
            (t/is (= node (:node data)))
            (t/is (str/starts-with? (ex-message (:cause data))
                                    "Assert failed:")))))))

  (t/testing "Node has invalid type."
    (let [node {:type :not-string :value "value"}]
      (try
        (transform/update-node node "name" {})
        (catch js/Error e
          (let [data (ex-data e)]
            (t/is (= "Failed to determine node type for update."
                     (ex-message e)))
            (t/is (= node (:node data)))
            (t/is (str/starts-with? (ex-message (:cause data))
                                    "Assert failed:"))))))))
