(ns clono.ast
  (:require
   [goog.object :as gobj]))

(def directive-node-types
  #{"containerDirective" "leafDirective" "textDirective"})

(defn children [node]
  (when (js/Array.isArray (.-children node))
    (array-seq (.-children node))))

(defn nodes [tree]
  (tree-seq #(some? (children %)) children tree))

(defn property [object & names]
  (reduce gobj/get object names))

(defn directive-node? [node]
  (contains? directive-node-types (.-type node)))
