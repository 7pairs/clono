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

(ns blue.lions.clono.parse
  (:require [clojure.string :as str]
            [blue.lions.clono.ast :as ast]
            [blue.lions.clono.esm :as esm]
            [blue.lions.clono.log :as logger]
            [blue.lions.clono.spec :as spec]))

(defn remove-comments
  [node]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (if-let [children (:children node)]
    (let [updated-children (mapv #(remove-comments %)
                                 (remove ast/comment? children))]
      (assoc node :children updated-children))
    node))

(defn remove-positions
  [node]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (let [updated-node (dissoc node :position)]
    (if-let [children (seq (:children node))]
      (assoc updated-node :children (mapv remove-positions children))
      updated-node)))

(defn generate-heading-slug
  [node]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/slug % "Invalid slug is returned.")]}
  (let [caption (->> node
                     ast/extract-texts
                     (map :value)
                     str/join)]
    (try
      (esm/generate-slug caption)
      (catch js/Error e
        (throw (ex-info "Failed to generate heading slug."
                        {:node node :caption caption :cause e}))))))

(defn add-heading-slugs
  [node]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (letfn [(update-node
           [target]
           (let [updated-node (if (ast/heading? target)
                                (assoc target
                                       :slug (generate-heading-slug target))
                                target)]
             (if (seq (:children target))
               (update updated-node :children #(mapv update-node %))
               updated-node)))]
    (update-node node)))

(defn add-index-ids
  [node generate-order]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/function generate-order
                        "Invalid generator is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (letfn [(update-node
           [target]
           (let [updated-node (if (ast/index? target)
                                (let [order (generate-order)]
                                  (assoc target
                                         :id (str "index-" order)
                                         :order order))
                                target)]
             (if (seq (:children target))
               (update updated-node :children #(mapv update-node %))
               updated-node)))]
    (update-node node)))

(defn create-order-generator
  ([]
   {:post [(spec/validate ::spec/function % "Invalid generator is returned.")]}
   (create-order-generator 0))
  ([initial-order]
   {:pre [(spec/validate ::spec/order initial-order "Invalid order is given.")]
    :post [(spec/validate ::spec/function % "Invalid generator is returned.")]}
   (let [counter (atom initial-order)]
     #(swap! counter inc))))

(defn markdown->ast
  [markdown order-generator]
  {:pre [(spec/validate ::spec/markdown markdown "Invalid Markdown is given.")
         (spec/validate ::spec/function order-generator
                        "Invalid generator is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (try
    (let [js-ast (esm/from-markdown
                  markdown
                  (clj->js {:extensions [(esm/gfm-footnote)
                                         (esm/directive)]
                            :mdastExtensions [(esm/gfm-footnote-from-markdown)
                                              (esm/directive-from-markdown)]}))
          ast (js->clj js-ast {:keywordize-keys true})]
      (-> ast
          remove-comments
          remove-positions
          add-heading-slugs
          (add-index-ids order-generator)))
    (catch js/Error e
      (throw (ex-info "Failed to convert Markdown to AST."
                      {:markdown markdown :cause e})))))

(defn parse-manuscripts
  [manuscripts order-generator]
  {:pre [(spec/validate ::spec/manuscripts manuscripts
                        "Invalid manuscripts are given.")
         (spec/validate ::spec/function order-generator
                        "Invalid generator is given.")]
   :post [(spec/validate ::spec/documents %
                         "Invalid documents are returned.")]}
  (vec
   (keep
    (fn [{:keys [name type markdown]}]
      (try
        {:name name
         :type type
         :ast (markdown->ast markdown order-generator)}
        (catch js/Error e
          (logger/error "Failed to parse markdown."
                        {:name name
                         :type type
                         :cause (ex-message e)})
          nil)))
    manuscripts)))
