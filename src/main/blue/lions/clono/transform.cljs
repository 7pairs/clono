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

(ns blue.lions.clono.transform
  (:require [cljs.spec.alpha :as s]
            [blue.lions.clono.ast :as ast]
            [blue.lions.clono.identifier :as id]
            [blue.lions.clono.log :as logger]
            [blue.lions.clono.spec :as spec]))

(defmulti deletable-node?
  (fn [node]
    (try
      (ast/get-type node)
      (catch js/Error e
        (throw (ex-info "Failed to determine node type."
                        {:node node :cause e}))))))

(defmethod deletable-node? :default
  [node]
  {:pre [(s/valid? ::spec/node node)]
   :post [(s/valid? ::spec/pred-result %)]}
  false)

(defmethod deletable-node? "footnoteDefinition"
  [node]
  {:pre [(s/valid? ::spec/node node)]
   :post [(s/valid? ::spec/pred-result %)]}
  true)

(defmethod deletable-node? "label"
  [node]
  {:pre [(s/valid? ::spec/node node)]
   :post [(s/valid? ::spec/pred-result %)]}
  true)

(defmulti update-node
  (fn [node _ _]
    (try
      (ast/get-type node)
      (catch js/Error e
        (throw (ex-info "Failed to determine node type for update."
                        {:node node :cause e}))))))

(defmethod update-node :default
  [node base-name dics]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)
         (s/valid? ::spec/dics dics)]
   :post [(s/valid? ::spec/node %)]}
  node)

(defmethod update-node "footnoteReference"
  [node base-name dics]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)
         (s/valid? ::spec/dics dics)]
   :post [(s/valid? ::spec/node %)]}
  (let [dic (:footnote dics)]
    (when-not dic
      (throw (ex-info "Footnote dictionary is not found."
                      {:dics dics :node node})))
    (let [key (id/build-dic-key base-name (:identifier node))
          footnote (dic key)]
      (assoc node :children
             (if footnote
               [footnote]
               (do
                 (logger/log :error
                             "Footnote is not found in dictionary."
                             {:key key :node node})
                 []))))))

(defn update-ref-heading-node
  [node base-name dics node-type]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)
         (s/valid? ::spec/dics dics)
         (s/valid? ::spec/node-type node-type)]
   :post [(s/valid? ::spec/node %)]}
  (let [dic (:heading dics)]
    (when-not dic
      (throw (ex-info "Heading dictionary is not found."
                      {:dics dics :node node})))
    (let [node-id (-> node
                      :attributes
                      :id)]
      (if-not node-id
        (do
          (logger/log :error
                      "Reference node does not have ID."
                      {:node node :base-name base-name :dics dics})
          node)
        (let [data (or (dic node-id)
                       (dic (id/build-dic-key base-name node-id)))]
          (if data
            (cond-> (assoc node :url (:url data))
              (= node-type "refHeadingName") (assoc :caption (:caption data)))
            (do
              (logger/log :error
                          "Heading is not found in dictionary."
                          {:base-name base-name :id node-id :dics dics})
              node)))))))

(defmethod update-node "refHeading"
  [node base-name dics]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)
         (s/valid? ::spec/dics dics)]
   :post [(s/valid? ::spec/node %)]}
  (update-ref-heading-node node base-name dics "refHeading"))

(defmethod update-node "refHeadingName"
  [node base-name dics]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)
         (s/valid? ::spec/dics dics)]
   :post [(s/valid? ::spec/node %)]}
  (update-ref-heading-node node base-name dics "refHeadingName"))
