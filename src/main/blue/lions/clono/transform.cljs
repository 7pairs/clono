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
  (:require [blue.lions.clono.ast :as ast]
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
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/pred-result % "Invalid result is returned.")]}
  false)

(defmethod deletable-node? "footnoteDefinition"
  [node]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/pred-result % "Invalid result is returned.")]}
  true)

(defmethod deletable-node? "label"
  [node]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/pred-result % "Invalid result is returned.")]}
  true)

(defmulti update-node
  (fn [node _base-name _dics]
    (try
      (ast/get-type node)
      (catch js/Error e
        (throw (ex-info "Failed to determine node type for update."
                        {:node node :cause e}))))))

(defmethod update-node :default
  [node base-name dics]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")
         (spec/validate ::spec/dics dics "Invalid dictionaries are given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  node)

(defmethod update-node "footnoteReference"
  [node base-name dics]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")
         (spec/validate ::spec/dics dics "Invalid dictionaries are given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
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
                 (logger/error "Footnote is not found in dictionary."
                               {:key key :node node})
                 []))))))

(defn update-ref-heading-node
  [node base-name dics node-type]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")
         (spec/validate ::spec/dics dics "Invalid dictionaries are given.")
         (spec/validate ::spec/node-type node-type
                        "Invalid node type is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (let [dic (:heading dics)]
    (when-not dic
      (throw (ex-info "Heading dictionary is not found."
                      {:dics dics :node node})))
    (let [node-id (-> node
                      :attributes
                      :id)]
      (if-not node-id
        (do
          (logger/error "Reference node does not have ID."
                        {:node node :base-name base-name :dics dics})
          node)
        (let [data (or (dic node-id)
                       (dic (id/build-dic-key base-name node-id)))]
          (if data
            (cond-> (assoc node :depth (:depth data) :url (:url data))
              (= node-type "refHeadingName") (assoc :caption (:caption data)))
            (do
              (logger/error "Heading is not found in dictionary."
                            {:base-name base-name :id node-id :dics dics})
              node)))))))

(defmethod update-node "refHeading"
  [node base-name dics]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")
         (spec/validate ::spec/dics dics "Invalid dictionaries are given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (update-ref-heading-node node base-name dics "refHeading"))

(defmethod update-node "refHeadingName"
  [node base-name dics]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")
         (spec/validate ::spec/dics dics "Invalid dictionaries are given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (update-ref-heading-node node base-name dics "refHeadingName"))

(defn transform-node
  [node base-name dics]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")
         (spec/validate ::spec/dics dics "Invalid dictionaries are given.")]
   :post [(spec/validate ::spec/node-or-nil % "Invalid node is returned.")]}
  (when-not (deletable-node? node)
    (let [updated-node (update-node node base-name dics)
          children (:children updated-node)]
      (if (seq children)
        (assoc updated-node
               :children (->> children
                              (keep #(transform-node % base-name dics))
                              vec))
        updated-node))))

(defn transform-documents
  [documents dics]
  {:pre [(spec/validate ::spec/documents documents
                        "Invalid documents are given.")
         (spec/validate ::spec/dics dics "Invalid dictionaries are given.")]
   :post [(spec/validate ::spec/documents %
                         "Invalid documents are returned.")]}
  (vec
   (keep
    (fn [{:keys [name type ast]}]
      (try
        {:name name
         :type type
         :ast (transform-node ast (id/extract-base-name name) dics)}
        (catch js/Error e
          (logger/error "Failed to transform AST."
                        {:file-name name :cause (ex-message e)})
          nil)))
    documents)))
