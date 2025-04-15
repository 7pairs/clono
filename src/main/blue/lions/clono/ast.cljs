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

(ns blue.lions.clono.ast
  (:require [cljs.spec.alpha :as s]
            [blue.lions.clono.spec :as spec]))

; HTML comments must:
; 1. Start with <!--
; 2. Not contain -- within the comment
; 3. End with -->
(def html-comment-regex #"(?s)<!--(?!>)[^-]*(-[^-][^-]*)*-->")

(defn comment?
  [node]
  {:pre [(s/valid? ::spec/node node)]
   :post [(s/valid? ::spec/pred-result %)]}
  (let [value (when (= (:type node) "html") (:value node))]
    (boolean
     (and (some? value)
          (string? value)
          (re-matches html-comment-regex value)))))

(defn node-type?
  [type node]
  {:pre [(or (s/valid? ::spec/node-type type)
             (throw (ex-info "Invalid node type." {:type type})))
         (or (s/valid? ::spec/node node)
             (throw (ex-info "Invalid node." {:node node})))]
   :post [(s/valid? ::spec/pred-result %)]}
  (= (:type node) type))

(def footnote-definition? (partial node-type? "footnoteDefinition"))

(def heading? (partial node-type? "heading"))

(def text? (partial node-type? "text"))

(defn text-directive?
  [name node]
  {:pre [(or (s/valid? ::spec/directive-name name)
             (throw (ex-info "Invalid directive name." {:name name})))
         (or (s/valid? ::spec/node node)
             (throw (ex-info "Invalid node." {:node node})))]
   :post [(s/valid? ::spec/pred-result %)]}
  (and (= (:type node) "textDirective")
       (= (:name node) name)))

(def index? (partial text-directive? "index"))

(def label? (partial text-directive? "label"))

(defn extract-nodes
  [pred node]
  {:pre [(or (s/valid? ::spec/function pred)
             (throw (ex-info "Invalid predicate function." {:pred pred})))
         (or (s/valid? ::spec/node node)
             (throw (ex-info "Invalid node." {:node node})))]
   :post [(s/valid? ::spec/nodes %)]}
  (vec
   (concat (if (pred node) [node] [])
           (mapcat #(extract-nodes pred %) (get node :children [])))))

(def extract-footnote-definition
  (partial extract-nodes footnote-definition?))

(def extract-headings (partial extract-nodes heading?))

(def extract-indices (partial extract-nodes index?))

(def extract-labels (partial extract-nodes label?))

(def extract-texts (partial extract-nodes text?))

(defmulti get-type
  (fn [node]
    (:type node)))

(defmethod get-type :default
  [node]
  {:pre [(s/valid? ::spec/node node)]
   :post [(s/valid? ::spec/node-type %)]}
  (:type node))

(defn directive-type
  [node]
  {:pre [(or (s/valid? ::spec/directive-node node)
             (throw (ex-info "Invalid directive node." {:node node})))]
   :post [(s/valid? ::spec/node-type %)]}
  (:name node))

(defmethod get-type "textDirective"
  [node]
  {:pre [(s/valid? ::spec/node node)]
   :post [(s/valid? ::spec/node-type %)]}
  (directive-type node))

(defmethod get-type "containerDirective"
  [node]
  {:pre [(s/valid? ::spec/node node)]
   :post [(s/valid? ::spec/node-type %)]}
  (directive-type node))
