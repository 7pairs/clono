(ns clono.transform.column
  (:require
   [clojure.string :as str]
   [clono.ast :as ast]
   [clono.diagnostic :as diagnostic]
   [goog.string :as gstring]))

(def allowed-content-node-types
  #{"paragraph"
    "text"
    "emphasis"
    "strong"
    "inlineCode"
    "link"
    "linkReference"
    "break"
    "list"
    "listItem"
    "blockquote"
    "code"
    "image"
    "imageReference"
    "table"
    "tableRow"
    "tableCell"
    "footnoteReference"})

(def node-descriptions
  {"heading" "見出し"
   "thematicBreak" "水平線"
   "html" "raw HTML"
   "footnoteDefinition" "脚注定義"
   "definition" "リンクまたは画像の定義"
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

(defn node-diagnostic [source-name node message]
  (diagnostic/at-point
   source-name
   "column"
   (ast/property node "position" "start")
   message))

(defn title-value [label]
  (apply str (map #(.-value %) (ast/children label))))

(defn title-diagnostic [node source-name]
  (if-let [label (label-node node)]
    (let [label-children (vec (ast/children label))]
      (when (or (not-every? #(= "text" (.-type %)) label-children)
                (str/blank? (title-value label)))
        (node-diagnostic
         source-name
         label
         "`column`のタイトルには空白ではないプレーンテキストが必要です。")))
    (diagnostic/for-node
     source-name
     node
     "`column`にはプレーンテキストのタイトルが必要です。")))

(defn attribute-diagnostic [node source-name]
  (when (seq (js/Object.keys (attributes node)))
    (diagnostic/for-node
     source-name
     node
     "`column`には属性を指定できません。")))

(defn invalid-content-nodes [nodes known-directive-names]
  (loop [remaining (seq nodes)
         invalid []]
    (if-let [node (first remaining)]
      (let [type (.-type node)
            unknown-directive? (and (ast/directive-node? node)
                                    (not (contains? known-directive-names
                                                    (.-name node))))]
        (cond
          unknown-directive?
          (recur (next remaining) invalid)

          (contains? allowed-content-node-types type)
          (recur (concat (ast/children node) (next remaining)) invalid)

          :else
          (recur (next remaining) (conj invalid node))))
      invalid)))

(defn content-diagnostics [node source-name known-directive-names]
  (let [body (vec (body-children node))]
    (if (empty? body)
      [(diagnostic/for-node
        source-name
        node
        "`column`には1個以上の本文ブロックが必要です。")]
      (mapv
       (fn [invalid-node]
         (node-diagnostic
          source-name
          invalid-node
          (str "`column`内では"
               (node-description invalid-node) "を使用できません。")))
       (invalid-content-nodes body known-directive-names)))))

(defn diagnostics [node source-name known-directive-names]
  (if (not= "containerDirective" (.-type node))
    [(diagnostic/for-node
      source-name
      node
      "`column`はContainer directiveとして記述する必要があります。")]
    (let [title-problem (title-diagnostic node source-name)
          attribute-problem (attribute-diagnostic node source-name)]
      (cond-> (content-diagnostics node source-name known-directive-names)
        (some? title-problem) (conj title-problem)
        (some? attribute-problem) (conj attribute-problem)))))

(defn html-node [value]
  #js {:type "html" :value value})

(defn transform [node]
  (let [title (title-value (label-node node))]
    (concat [(html-node "<aside class=\"clono-column\">")
             (html-node
              (str "<p class=\"clono-column-title\">"
                   (gstring/htmlEscape title)
                   "</p>"))]
            (body-children node)
            [(html-node "</aside>")])))

(def rule
  {:node-type "containerDirective"
   :allowed-attribute-names #{}
   :diagnostics diagnostics
   :transform transform})
