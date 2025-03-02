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

(ns blue.lions.clono.analyze
  (:require [cljs.spec.alpha :as s]
            [clojure.string :as str]
            [blue.lions.clono.ast :as ast]
            [blue.lions.clono.identifier :as id]
            [blue.lions.clono.spec :as spec]))

(defn create-toc-item
  [file-name node]
  {:pre [(s/valid? ::spec/file-name file-name)
         (s/valid? ::spec/node node)]
   :post [(s/valid? ::spec/toc-item %)]}
  (let [depth (:depth node)
        caption (->> node
                     ast/extract-texts
                     (map :value)
                     str/join)
        base-name (try
                    (id/extract-base-name file-name)
                    (catch js/Error e
                      (throw (ex-info "Failed to extract base name."
                                      {:file-name file-name :cause e}))))
        slug (or (:slug node)
                 (throw (ex-info "Node does not have slug."
                                 {:file-name file-name :node node})))
        url (id/build-url base-name slug)]
    {:depth depth :caption caption :url url}))

(defn create-toc-items
  [documents]
  {:pre [(s/valid? ::spec/documents documents)]
   :post [(s/valid? ::spec/toc-items %)]}
  (vec
   (mapcat (fn [{:keys [name ast]}]
             (map #(create-toc-item name %)
                  (ast/extract-headings ast)))
           documents)))

(defn has-valid-id-or-root-depth?
  [node]
  {:pre [(s/valid? ::spec/node node)]
   :post [(s/valid? ::spec/pred-result %)]}
  (or (-> (ast/extract-labels node)
          first
          :attributes
          :id
          some?)
      (= (:depth node) 1)))

(defn create-heading-info
  [file-name node]
  {:pre [(s/valid? ::spec/file-name file-name)
         (s/valid? ::spec/node node)]
   :post [(s/valid? ::spec/heading-or-nil %)]}
  (when (has-valid-id-or-root-depth? node)
    (let [{:keys [depth caption url]} (create-toc-item file-name node)
          base-name (id/extract-base-name file-name)
          label-id (-> node
                       ast/extract-labels
                       first
                       :attributes
                       :id)
          id (if (= depth 1)
               base-name
               (id/build-dic-key base-name label-id))]
      {:id id :depth depth :caption caption :url url})))

(defn create-heading-dic
  [documents]
  {:pre [(s/valid? ::spec/documents documents)]
   :post [(s/valid? ::spec/heading-dic %)]}
  (->> (for [{:keys [name ast]} documents
             nodes (ast/extract-headings ast)
             :let [{:keys [id] :as heading-info}
                   (create-heading-info name nodes)]
             :when id]
         [id heading-info])
       (into {})))

(defn create-footnote-dic
  [documents]
  {:pre [(s/valid? ::spec/documents documents)]
   :post [(s/valid? ::spec/footnote-dic %)]}
  (->> (for [{:keys [name ast]} documents
             footnote (ast/extract-footnote-definition ast)
             :let [base-name (id/extract-base-name name)
                   id (:identifier footnote)
                   child (first (:children footnote))]
             :when child]
         [(id/build-dic-key base-name id) child])
       (into {})))

(defn english-ruby?
  [ruby]
  {:pre [(s/valid? ::spec/ruby ruby)]
   :post [(s/valid? ::spec/pred-result %)]}
  (boolean (re-matches #"^[a-z]+" ruby)))
