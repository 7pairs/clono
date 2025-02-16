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
  (:require [cljs.spec.alpha :as s]
            [blue.lions.clono.ast :as ast]
            [blue.lions.clono.spec :as spec]))

(defn remove-comments
  [node]
  {:pre [(s/valid? ::spec/node node)]
   :post [(s/valid? ::spec/node %)]}
  (if-let [children (:children node)]
    (let [updated-children (mapv #(remove-comments %)
                                 (remove ast/comment? children))]
      (assoc node :children updated-children))
    node))

(defn remove-positions
  [node]
  {:pre [(s/valid? ::spec/node node)]
   :post [(s/valid? ::spec/node %)]}
  (let [updated-node (dissoc node :position)]
    (if-let [children (seq (:children node))]
      (assoc updated-node :children (mapv remove-positions children))
      updated-node)))
