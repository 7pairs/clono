(ns clono.transform.definition-list
  (:require
   [clojure.string :as str]
   [clono.ast :as ast]
   [clono.diagnostic :as diagnostic]
   [clono.directive-validation :as directive-validation]
   [goog.string :as gstring]))

(def internal-directive-names
  #{"definition-list" "definition" "term"})

(def allowed-term-node-types
  #{"text" "inlineCode"})

(def allowed-description-node-types
  #{"text"
    "emphasis"
    "strong"
    "inlineCode"
    "link"
    "linkReference"
    "break"})

(def node-descriptions
  {"heading" "見出し"
   "list" "リスト"
   "code" "コードブロック"
   "image" "画像"
   "imageReference" "参照形式の画像"
   "html" "raw HTML"
   "footnoteReference" "脚注参照"
   "footnoteDefinition" "脚注定義"
   "containerDirective" "directive"
   "leafDirective" "directive"
   "textDirective" "directive"})

(defn attributes [node]
  (or (.-attributes node) #js {}))

(defn directive-label? [node]
  (and (some? (.-data node))
       (true? (ast/property node "data" "directiveLabel"))))

(defn label-node [node]
  (first (filter directive-label? (ast/children node))))

(defn body-children [node]
  (remove directive-label? (ast/children node)))

(defn node-description [node]
  (get node-descriptions (.-type node) (str "`" (.-type node) "`")))

(defn node-diagnostic [source-name directive-name node message]
  (diagnostic/at-point
   source-name
   directive-name
   (ast/property node "position" "start")
   message))

(defn attribute-diagnostic [node source-name directive-name]
  (when (seq (js/Object.keys (attributes node)))
    (diagnostic/for-node
     source-name
     node
     (str "`" directive-name "`には属性を指定できません。"))))

(defn prohibited-label-diagnostic [node source-name directive-name]
  (when-let [label (label-node node)]
    (node-diagnostic
     source-name
     directive-name
     label
     (str "`" directive-name "`にはラベルを指定できません。"))))

(defn unknown-directive? [node known-directive-names]
  (and (ast/directive-node? node)
       (not (contains? known-directive-names (.-name node)))))

(defn definition-node? [node]
  (= "definition" (.-name node)))

(defn term-node? [node]
  (= "term" (.-name node)))

(defn list-content-diagnostics [node source-name known-directive-names]
  (let [body (vec (body-children node))]
    (cond
      (empty? body)
      [(diagnostic/for-node
        source-name
        node
        "`definition-list`には1個以上の`definition`が必要です。")]

      :else
      (->> body
           (keep
            (fn [child]
              (cond
                (definition-node? child)
                nil

                (unknown-directive? child known-directive-names)
                nil

                :else
                (node-diagnostic
                 source-name
                 "definition-list"
                 child
                 (str "`definition-list`の直下には`definition`だけを記述できます（"
                      (node-description child) "を検出しました）。")))))
           vec))))

(defn definition-list-diagnostics [node context known-directive-names]
  (let [source-name (:source-name context)]
    (if (not= "containerDirective" (.-type node))
      [(diagnostic/for-node
        source-name
        node
        "`definition-list`はContainer directiveとして記述する必要があります。")]
      (let [label-problem
            (prohibited-label-diagnostic node source-name "definition-list")
            attribute-problem
            (attribute-diagnostic node source-name "definition-list")]
        (cond-> (list-content-diagnostics
                 node source-name known-directive-names)
          (some? label-problem) (conj label-problem)
          (some? attribute-problem) (conj attribute-problem))))))

(defn valid-definition-structure? [body]
  (and (= 2 (count body))
       (term-node? (first body))
       (= "paragraph" (.-type (second body)))))

(defn invalid-description-nodes [paragraph known-directive-names]
  (loop [remaining (seq (ast/children paragraph))
         invalid []]
    (if-let [node (first remaining)]
      (let [type (.-type node)]
        (cond
          (unknown-directive? node known-directive-names)
          (recur (next remaining) invalid)

          (contains? allowed-description-node-types type)
          (recur (concat (ast/children node) (next remaining)) invalid)

          :else
          (recur (next remaining) (conj invalid node))))
      invalid)))

(defn definition-content-diagnostics
  [node source-name known-directive-names]
  (let [body (vec (body-children node))
        known-body (vec (remove #(unknown-directive?
                                  % known-directive-names)
                                body))]
    (cond
      (not (valid-definition-structure? known-body))
      [(diagnostic/for-node
        source-name
        node
        (str "`definition`の直下には、1個の`term`と"
             "1個の説明段落をこの順序で記述する必要があります。"))]

      :else
      (mapv
       (fn [invalid-node]
         (node-diagnostic
          source-name
          "definition"
          invalid-node
          (str "`definition`の説明内では"
               (node-description invalid-node) "を使用できません。")))
       (invalid-description-nodes (second known-body)
                                  known-directive-names)))))

(defn definition-diagnostics [node context known-directive-names]
  (let [source-name (:source-name context)]
    (if (not= "containerDirective" (.-type node))
      [(diagnostic/for-node
        source-name
        node
        "`definition`はContainer directiveとして記述する必要があります。")]
      (let [label-problem
            (prohibited-label-diagnostic node source-name "definition")
            attribute-problem
            (attribute-diagnostic node source-name "definition")]
        (cond-> (definition-content-diagnostics
                 node source-name known-directive-names)
          (some? label-problem) (conj label-problem)
          (some? attribute-problem) (conj attribute-problem))))))

(defn term-value [node]
  (apply str (map #(.-value %) (ast/children node))))

(defn invalid-term-nodes [node known-directive-names]
  (->> (ast/children node)
       (remove
        (fn [child]
          (or (contains? allowed-term-node-types (.-type child))
              (unknown-directive? child known-directive-names))))
       vec))

(defn term-content-diagnostics [node source-name known-directive-names]
  (let [children (vec (ast/children node))
        contains-unknown? (some #(unknown-directive?
                                  % known-directive-names)
                                children)
        invalid-nodes (invalid-term-nodes node known-directive-names)]
    (cond
      (seq invalid-nodes)
      (mapv
       (fn [invalid-node]
         (node-diagnostic
          source-name
          "term"
          invalid-node
          (str "`term`の用語では"
               (node-description invalid-node) "を使用できません。")))
       invalid-nodes)

      (or (empty? children)
          (and (not contains-unknown?)
               (str/blank? (term-value node))))
      [(diagnostic/for-node
        source-name
        node
        "`term`には空白ではない用語が必要です。")]

      :else
      [])))

(defn term-diagnostics [node context known-directive-names]
  (let [source-name (:source-name context)]
    (if (not= "leafDirective" (.-type node))
      [(diagnostic/for-node
        source-name
        node
        "`term`はLeaf directiveとして記述する必要があります。")]
      (let [attribute-problem
            (attribute-diagnostic node source-name "term")]
        (cond-> (term-content-diagnostics
                 node source-name known-directive-names)
          (some? attribute-problem) (conj attribute-problem))))))

(defn parent-map [tree]
  (reduce
   (fn [result parent]
     (reduce #(assoc %1 %2 parent) result (ast/children parent)))
   {}
   (ast/nodes tree)))

(defn expected-parent? [tree parents node]
  (let [parent (get parents node)]
    (case (.-name node)
      "definition-list" (identical? tree parent)
      "definition" (and (= "containerDirective" (.-type parent))
                        (= "definition-list" (.-name parent)))
      "term" (and (= "containerDirective" (.-type parent))
                  (= "definition" (.-name parent)))
      true)))

(defn placement-message [node]
  (case (.-name node)
    "definition-list"
    "`definition-list`はMarkdown文書のトップレベルに記述する必要があります。"

    "definition"
    "`definition`は`definition-list`の直接の子として記述する必要があります。"

    "term"
    "`term`は`definition`の直接の子として記述する必要があります。"))

(defn expected-node-type [node]
  (case (.-name node)
    "definition-list" "containerDirective"
    "definition" "containerDirective"
    "term" "leafDirective"))

(defn inside-directive? [parents node]
  (loop [ancestor (get parents node)]
    (cond
      (nil? ancestor) false
      (ast/directive-node? ancestor) true
      :else (recur (get parents ancestor)))))

(defn placement-diagnostics [tree context known-directive-names]
  (let [source-name (:source-name context)
        parents (parent-map tree)]
    (->> (directive-validation/validation-nodes tree known-directive-names)
         (filter #(contains? internal-directive-names (.-name %)))
         (filter #(= (expected-node-type %) (.-type %)))
         (keep
          (fn [node]
            (when (and (not (expected-parent? tree parents node))
                       (not (inside-directive? parents node)))
              (diagnostic/for-node
               source-name
               node
               (placement-message node)))))
         vec)))

(defn html-node [value]
  #js {:type "html" :value value})

(defn term-html [node]
  (apply
   str
   (map
    (fn [child]
      (let [value (gstring/htmlEscape (.-value child))]
        (if (= "inlineCode" (.-type child))
          (str "<code>" value "</code>")
          value)))
    (ast/children node))))

(defn transform-term [node _context]
  [(html-node (str "<dt>" (term-html node) "</dt>"))])

(defn transform-definition [node _context]
  (let [[term description] (body-children node)]
    [(html-node "<div class=\"clono-definition-item\">")
     term
     (html-node "<dd>")
     description
     (html-node "</dd>")
     (html-node "</div>")]))

(defn transform-definition-list [node _context]
  (concat [(html-node "<dl class=\"clono-definition-list\">")]
          (body-children node)
          [(html-node "</dl>")]))

(def definition-list-rule
  {:node-type "containerDirective"
   :allowed-attribute-names #{}
   :diagnostics definition-list-diagnostics
   :document-diagnostics placement-diagnostics
   :transform transform-definition-list})

(def definition-rule
  {:node-type "containerDirective"
   :allowed-attribute-names #{}
   :diagnostics definition-diagnostics
   :transform transform-definition})

(def term-rule
  {:node-type "leafDirective"
   :allowed-attribute-names #{}
   :diagnostics term-diagnostics
   :transform transform-term})
