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
