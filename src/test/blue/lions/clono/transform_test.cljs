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

(t/deftest update-ref-heading-node-test
  (t/testing "Node is refHeading."
    (t/is (= {:type "textDirective"
              :name "refHeading"
              :attributes {:id "id1"}
              :depth 1
              :url "url1"}
             (transform/update-ref-heading-node
              {:type "textDirective"
               :name "refHeading"
               :attributes {:id "id1"}}
              "base-name1"
              {:heading {"id1" {:id "id1"
                                :depth 1
                                :caption "caption1"
                                :url "url1"}
                         "id2" {:id "id2"
                                :depth 2
                                :caption "caption2"
                                :url "url2"}
                         "base-name1|id1" {:id "base-name1|id1"
                                           :depth 3
                                           :caption "caption3"
                                           :url "url3"}}}
              "refHeading")))
    (t/is (= {:type "textDirective"
              :name "refHeading"
              :attributes {:id "id1"}
              :depth 3
              :url "url3"}
             (transform/update-ref-heading-node
              {:type "textDirective"
               :name "refHeading"
               :attributes {:id "id1"}}
              "base-name1"
              {:heading {"id2" {:id "id2"
                                :depth 1
                                :caption "caption1"
                                :url "url1"}
                         "id3" {:id "id3"
                                :depth 2
                                :caption "caption2"
                                :url "url2"}
                         "base-name1|id1" {:id "base-name1|id1"
                                           :depth 3
                                           :caption "caption3"
                                           :url "url3"}}}
              "refHeading"))))

  (t/testing "Node is refHeadingName."
    (t/is (= {:type "textDirective"
              :name "refHeadingName"
              :attributes {:id "id1"}
              :depth 1
              :url "url1"
              :caption "caption1"}
             (transform/update-ref-heading-node
              {:type "textDirective"
               :name "refHeadingName"
               :attributes {:id "id1"}}
              "base-name1"
              {:heading {"id1" {:id "id1"
                                :depth 1
                                :caption "caption1"
                                :url "url1"}
                         "id2" {:id "id2"
                                :depth 2
                                :caption "caption2"
                                :url "url2"}
                         "base-name1|id1" {:id "base-name1|id1"
                                           :depth 3
                                           :caption "caption3"
                                           :url "url3"}}}
              "refHeadingName")))
    (t/is (= {:type "textDirective"
              :name "refHeadingName"
              :attributes {:id "id1"}
              :depth 3
              :url "url3"
              :caption "caption3"}
             (transform/update-ref-heading-node
              {:type "textDirective"
               :name "refHeadingName"
               :attributes {:id "id1"}}
              "base-name1"
              {:heading {"id2" {:id "id2"
                                :depth 1
                                :caption "caption1"
                                :url "url1"}
                         "id3" {:id "id3"
                                :depth 2
                                :caption "caption2"
                                :url "url2"}
                         "base-name1|id1" {:id "base-name1|id1"
                                           :depth 3
                                           :caption "caption3"
                                           :url "url3"}}}
              "refHeadingName"))))

  (t/testing "ID is not found."
    (reset! logger/enabled? false)
    (reset! logger/entries [])
    (t/is (= {:type "textDirective" :name "refHeading" :attributes {:id "id1"}}
             (transform/update-ref-heading-node
              {:type "textDirective"
               :name "refHeading"
               :attributes {:id "id1"}}
              "base-name1"
              {:heading {"id2" {:id "id2"
                                :depth 1
                                :caption "caption1"
                                :url "url1"}}}
              "refHeading")))
    (t/is (= [{:level :error
               :message "Heading is not found in dictionary."
               :data {:base-name "base-name1"
                      :id "id1"
                      :dics {:heading {"id2" {:id "id2"
                                              :depth 1
                                              :caption "caption1"
                                              :url "url1"}}}}}]
             @logger/entries)))

  (t/testing "Node does not have ID."
    (reset! logger/enabled? false)
    (reset! logger/entries [])
    (t/is (= {:type "textDirective" :name "refHeading" :attributes {}}
             (transform/update-ref-heading-node
              {:type "textDirective" :name "refHeading" :attributes {}}
              "base-name1"
              {:heading {"id1" {:id "id1"
                                :depth 1
                                :caption "caption1"
                                :url "url1"}}}
              "refHeading")))
    (t/is (= [{:level :error
               :message "Reference node does not have ID."
               :data {:node {:type "textDirective"
                             :name "refHeading"
                             :attributes {}}
                      :base-name "base-name1"
                      :dics {:heading {"id1" {:id "id1"
                                              :depth 1
                                              :caption "caption1"
                                              :url "url1"}}}}}]
             @logger/entries)))

  (t/testing "Heading dictionary is not found."
    (let [node {:type "refHeading" :attributes {:id "id1"}}
          dics {:not-heading {"key" "value"}}]
      (try
        (transform/update-ref-heading-node node "base-name1" dics "refHeading")
        (catch js/Error e
          (let [data (ex-data e)]
            (t/is (= "Heading dictionary is not found." (ex-message e)))
            (t/is (= dics (:dics data)))
            (t/is (= node (:node data)))))))))

(t/deftest transform-node-test
  (t/testing "Node has deletable nodes."
    (t/is (= {:type "root"
              :children [{:type "text" :value "value1"}
                         {:type "text" :value "value2"}]}
             (transform/transform-node
              {:type "root"
               :children [{:type "text" :value "value1"}
                          {:type "label"}
                          {:type "text" :value "value2"}]}
              "base-name"
              {}))))

  (t/testing "Node does not have deletable node."
    (let [node {:type "root"
                :children [{:type "text" :value "value1"}
                           {:type "text" :value "value2"}]}]
      (t/is (= node
               (transform/transform-node node "base-name" {})))))

  (t/testing "Node has children."
    (t/is (= {:type "root"
              :children [{:type "text" :value "value1"}
                         {:type "node"
                          :children [{:type "text" :value "value2"}]}]}
             (transform/transform-node
              {:type "root"
               :children [{:type "text" :value "value1"}
                          {:type "label"}
                          {:type "node"
                           :children [{:type "text" :value "value2"}
                                      {:type "label"}]}]}
              "base-name"
              {}))))

  (t/testing "All children are deletable."
    (t/is (= {:type "root" :children []}
             (transform/transform-node {:type "root"
                                        :children [{:type "label"}
                                                   {:type "label"}]}
                                       "base-name"
                                       {})))))

(t/deftest transform-documents-test
  (t/testing "All documents are valid."
    (t/is (= [{:name "markdown1.md"
               :type :chapters
               :ast {:type "root"
                     :children [{:type "text" :value "value1"}
                                {:type "text" :value "value3"}]}}
              {:name "markdown2.md"
               :type :appendices
               :ast {:type "root"
                     :children [{:type "text" :value "value4"}
                                {:type "text" :value "value6"}]}}]
             (transform/transform-documents
              [{:name "markdown1.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "text" :value "value1"}
                                 {:type "label" :value "value2"}
                                 {:type "text" :value "value3"}]}}
               {:name "markdown2.md"
                :type :appendices
                :ast {:type "root"
                      :children [{:type "text" :value "value4"}
                                 {:type "label" :value "value5"}
                                 {:type "text" :value "value6"}]}}]
              {}))))

  (t/testing "Some documents are invalid."
    (reset! logger/enabled? false)
    (reset! logger/entries [])
    (t/is (= [{:name "markdown1.md"
               :type :chapters
               :ast {:type "root"
                     :children [{:type "text" :value "value1"}
                                {:type "text" :value "value3"}]}}]
             (transform/transform-documents
              [{:name "markdown1.md"
                :type :chapters
                :ast {:type "root"
                      :children [{:type "text" :value "value1"}
                                 {:type "label" :value "value2"}
                                 {:type "text" :value "value3"}]}}
               {:name "markdown2.md"
                :type :appendices
                :ast {:type "root" :children ["not-node"]}}]
              {})))
    (let [entries @logger/entries]
      (t/is (= 1 (count entries)))
      (let [{:keys [level message data]} (first entries)]
        (t/is (= :error level))
        (t/is (= "Failed to transform AST." message))
        (t/is (= "markdown2.md" (:file-name data)))
        (t/is (= "Invalid node is given." (:cause data))))))

  (t/testing "All documents are invalid."
    (reset! logger/enabled? false)
    (reset! logger/entries [])
    (t/is (= []
             (transform/transform-documents
              [{:name "markdown1.md"
                :type :chapters
                :ast {:type "root" :children ["not-node"]}}
               {:name "markdown2.md"
                :type :appendices
                :ast {:type "root"
                      :children ["not-node"]}}]
              {})))
    (let [entries @logger/entries]
      (t/is (= 2 (count entries)))
      (let [{:keys [level message data]} (first entries)]
        (t/is (= :error level))
        (t/is (= "Failed to transform AST." message))
        (t/is (= "markdown1.md" (:file-name data)))
        (t/is (= "Invalid node is given." (:cause data))))
      (let [{:keys [level message data]} (second entries)]
        (t/is (= :error level))
        (t/is (= "Failed to transform AST." message))
        (t/is (= "markdown2.md" (:file-name data)))
        (t/is (= "Invalid node is given." (:cause data)))))))
