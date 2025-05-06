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
  (:require [blue.lions.clono.spec :as spec]))

; HTML comments must:
; 1. Start with <!--
; 2. Not contain -- within the comment
; 3. End with -->
(def html-comment-regex #"(?s)<!--(?!>)[^-]*(-[^-][^-]*)*-->")

(defn comment?
  [node]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/pred-result % "Invalid result is returned.")]}
  (let [value (when (= (:type node) "html") (:value node))]
    (boolean
     (and (some? value)
          (string? value)
          (re-matches html-comment-regex value)))))

(defn node-type?
  [type node]
  {:pre [(spec/validate ::spec/node-type type "Invalid node type is given.")
         (spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/pred-result % "Invalid result is returned.")]}
  (= (:type node) type))

(def footnote-definition? (partial node-type? "footnoteDefinition"))

(def heading? (partial node-type? "heading"))

(def text? (partial node-type? "text"))

(defn text-directive?
  [name node]
  {:pre [(spec/validate ::spec/directive-name name
                        "Invalid directive name is given.")
         (spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/pred-result % "Invalid result is returned.")]}
  (and (= (:type node) "textDirective")
       (= (:name node) name)))

(def index? (partial text-directive? "index"))

(def label? (partial text-directive? "label"))

(defn extract-nodes
  [pred node]
  {:pre [(spec/validate ::spec/function pred
                        "Invalid predicate function is given.")
         (spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/nodes % "Invalid nodes are returned.")]}
  (loop [targets [node]
         result []]
    (if (empty? targets)
      result
      (let [current (first targets)
            children (get current :children [])
            matched (if (pred current) [current] [])]
        (recur (into children (rest targets)) (into result matched))))))

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
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/node-type % "Invalid node type is returned.")]}
  (:type node))

(defn directive-type
  [node]
  {:pre [(spec/validate ::spec/directive-node node
                        "Invalid directive node is given.")]
   :post [(spec/validate ::spec/node-type % "Invalid node type is returned.")]}
  (:name node))

(defmethod get-type "textDirective"
  [node]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/node-type % "Invalid node type is returned.")]}
  (directive-type node))

(defmethod get-type "containerDirective"
  [node]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/node-type % "Invalid node type is returned.")]}
  (directive-type node))
