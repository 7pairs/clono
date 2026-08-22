(ns clono.directive-validation
  (:require
   [clono.ast :as ast]
   [clono.diagnostic :as diagnostic]))

(defn unknown-directive? [node known-directive-names]
  (and (ast/directive-node? node)
       (not (contains? known-directive-names (.-name node)))))

(defn validation-children [node known-directive-names]
  (when-not (and (= "containerDirective" (.-type node))
                 (unknown-directive? node known-directive-names))
    (ast/children node)))

(defn validation-nodes [tree known-directive-names]
  (tree-seq
   #(some? (validation-children % known-directive-names))
   #(validation-children % known-directive-names)
   tree))

(defn unknown-diagnostics [tree source-name known-directive-names]
  (->> (validation-nodes tree known-directive-names)
       (keep (fn [node]
               (when (unknown-directive? node known-directive-names)
                 (diagnostic/for-node
                  source-name
                  node
                  (str "`" (.-name node)
                       "`は登録されていないdirectiveです。")))))
       vec))
