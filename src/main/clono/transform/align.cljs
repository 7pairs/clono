(ns clono.transform.align
  (:require
   [clono.ast :as ast]
   [clono.diagnostic :as diagnostic]
   [goog.object :as gobj]))

(def allowed-inline-node-types
  #{"text"
    "emphasis"
    "strong"
    "inlineCode"
    "link"
    "linkReference"
    "break"
    "footnoteReference"})

(def node-descriptions
  {"heading" "見出し"
   "list" "リスト"
   "code" "コードブロック"
   "image" "画像"
   "imageReference" "参照形式の画像"
   "table" "表"
   "html" "raw HTML"
   "containerDirective" "directive"
   "leafDirective" "directive"
   "textDirective" "directive"})

(defn attributes [node]
  (or (.-attributes node) #js {}))

(defn node-description [node]
  (get node-descriptions (.-type node) (str "`" (.-type node) "`")))

(defn node-diagnostic [source-name node message]
  (diagnostic/at-point
   source-name
   "align"
   (ast/property node "position" "start")
   message))

(defn attribute-diagnostic [node source-name]
  (let [node-attributes (attributes node)
        position (gobj/get node-attributes "position")
        attribute-names (set (array-seq (js/Object.keys node-attributes)))]
    (cond
      (nil? position)
      (diagnostic/for-node
       source-name
       node
       "`align`には`position=\"right\"`が必要です。")

      (not= "right" position)
      (diagnostic/for-node
       source-name
       node
       "`align`の`position`属性には`right`を指定する必要があります。")

      (not= #{"position"} attribute-names)
      (diagnostic/for-node
       source-name
       node
       "`align`には`position`以外の属性を指定できません。"))))

(defn invalid-inline-nodes [paragraph known-directive-names]
  (loop [remaining (seq (ast/children paragraph))
         invalid []]
    (if-let [node (first remaining)]
      (let [type (.-type node)
            known-directive? (and (ast/directive-node? node)
                                  (contains? known-directive-names (.-name node)))
            allowed? (contains? allowed-inline-node-types type)]
        (cond
          allowed?
          (recur (concat (ast/children node) (next remaining)) invalid)

          (and (ast/directive-node? node) (not known-directive?))
          (recur (next remaining) invalid)

          :else
          (recur (next remaining) (conj invalid node))))
      invalid)))

(defn content-diagnostics [node source-name known-directive-names]
  (let [node-children (vec (ast/children node))]
    (if (empty? node-children)
      [(diagnostic/for-node
        source-name
        node
        "`align`には1個以上の段落が必要です。")]
      (->> node-children
           (mapcat
            (fn [child]
              (cond
                (not= "paragraph" (.-type child))
                (if (and (ast/directive-node? child)
                         (not (contains? known-directive-names (.-name child))))
                  []
                  [(node-diagnostic
                    source-name
                    child
                    (str "`align`の直下には段落だけを記述できます（"
                         (node-description child) "を検出しました）。"))])

                :else
                (mapv
                 (fn [invalid-node]
                   (node-diagnostic
                    source-name
                    invalid-node
                    (str "`align`の段落内では"
                         (node-description invalid-node) "を使用できません。")))
                 (invalid-inline-nodes child known-directive-names)))))
           vec))))

(defn diagnostics [node source-name known-directive-names]
  (if (not= "containerDirective" (.-type node))
    [(diagnostic/for-node
      source-name
      node
      "`align`はContainer directiveとして記述する必要があります。")]
    (let [attribute-problem (attribute-diagnostic node source-name)]
      (cond-> (content-diagnostics node source-name known-directive-names)
        (some? attribute-problem) (conj attribute-problem)))))

(defn html-node [value]
  #js {:type "html" :value value})

(defn transform [node]
  (concat [(html-node "<div class=\"clono-align-right\">")]
          (ast/children node)
          [(html-node "</div>")]))

(def rule
  {:node-type "containerDirective"
   :allowed-attribute-names #{"position"}
   :diagnostics diagnostics
   :transform transform})
