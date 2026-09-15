(ns clono.transform.space
  (:require
   [clono.ast :as ast]
   [clono.diagnostic :as diagnostic]
   [clono.directive-validation :as directive-validation]))

(def non-rendered-root-node-types
  #{"definition" "footnoteDefinition"})

(def image-node-types
  #{"image" "imageReference"})

(defn attributes [node]
  (or (.-attributes node) #js {}))

(defn valid-leaf? [node]
  (and (= "leafDirective" (.-type node))
       (empty? (ast/children node))
       (empty? (js/Object.keys (attributes node)))))

(defn diagnostics [node context _known-directive-names]
  (let [source-name (:source-name context)]
    (cond
      (not= "leafDirective" (.-type node))
      [(diagnostic/for-node
        source-name
        node
        "`space`はLeaf directiveとして記述する必要があります。")]

      (seq (ast/children node))
      [(diagnostic/for-node
        source-name
        node
        "`space`にはラベルを指定できません。")]

      (seq (js/Object.keys (attributes node)))
      [(diagnostic/for-node
        source-name
        node
        "`space`には属性を指定できません。")]

      :else
      [])))

(defn parent-map [tree]
  (let [result (js/Map.)]
    (doseq [parent (ast/nodes tree)
            child (ast/children parent)]
      (.set result child parent))
    result))

(defn directive-ancestor? [node parents]
  (loop [ancestor (.get parents node)]
    (cond
      (nil? ancestor) false
      (ast/directive-node? ancestor) true
      :else (recur (.get parents ancestor)))))

(defn placement-diagnostic [source-name node]
  (diagnostic/for-node
   source-name
   node
   "`space`はMarkdown文書のトップレベルに記述する必要があります。"))

(defn image-only-paragraph? [node]
  (let [children (ast/children node)]
    (and (= "paragraph" (.-type node))
         (seq children)
         (every? #(contains? image-node-types (.-type %)) children))))

(defn normal-paragraph? [node]
  (and (some? node)
       (= "paragraph" (.-type node))
       (not (image-only-paragraph? node))))

(defn significant-root-children [tree]
  (->> (ast/children tree)
       (remove #(contains? non-rendered-root-node-types (.-type %)))
       vec))

(defn previous-significant-node [nodes index]
  (when (pos? index)
    (nth nodes (dec index))))

(defn next-significant-node [nodes index]
  (when (< (inc index) (count nodes))
    (nth nodes (inc index))))

(defn boundary-diagnostics [tree source-name]
  (let [nodes (significant-root-children tree)]
    (->> nodes
         (map-indexed vector)
         (mapcat
          (fn [[index node]]
            (when (and (= "space" (.-name node))
                       (valid-leaf? node))
              (let [previous-node (previous-significant-node nodes index)
                    next-node (next-significant-node nodes index)]
                (cond-> []
                  (and (some? previous-node)
                       (= "space" (.-name previous-node)))
                  (conj (diagnostic/for-node
                         source-name
                         node
                         "`space`を連続して記述できません。"))

                  (and (not= "space" (some-> previous-node .-name))
                       (not (normal-paragraph? previous-node)))
                  (conj (diagnostic/for-node
                         source-name
                         node
                         "`space`の前には通常段落が必要です。"))

                  (and (not= "space" (some-> next-node .-name))
                       (not (normal-paragraph? next-node)))
                  (conj (diagnostic/for-node
                         source-name
                         node
                         "`space`の後には通常段落が必要です。")))))))
         vec)))

(defn document-diagnostics [tree context known-directive-names]
  (let [source-name (:source-name context)
        parents (parent-map tree)
        nested-diagnostics
        (->> (directive-validation/validation-nodes
              tree
              known-directive-names)
             (keep
              (fn [node]
                (when (and (= "space" (.-name node))
                           (valid-leaf? node)
                           (not (identical? tree (.get parents node)))
                           (not (directive-ancestor? node parents)))
                  (placement-diagnostic source-name node))))
             vec)]
    (concat nested-diagnostics
            (boundary-diagnostics tree source-name))))

(defn html-node [value]
  #js {:type "html" :value value})

(defn transform [_node _context]
  [(html-node
    "<div class=\"clono-space\" aria-hidden=\"true\"></div>")])

(def rule
  {:node-type "leafDirective"
   :allowed-attribute-names #{}
   :diagnostics diagnostics
   :document-diagnostics document-diagnostics
   :transform transform})
