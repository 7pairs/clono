(ns clono.transform.column
  (:require
   [clojure.string :as str]
   [clono.ast :as ast]
   [clono.diagnostic :as diagnostic]
   [clono.markdown :as markdown]
   [goog.object :as gobj]
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

(defn- semantic-content [node]
  {:title (some-> node label-node title-value)
   :body-nodes (vec (body-children node))})

(defn normalized-data [node]
  (let [{:keys [title body-nodes]} (semantic-content node)
        body (str/replace
              (markdown/serialize
               #js {:type "root"
                    :children (into-array body-nodes)})
              #"\n$" "")]
    (js/Object.freeze #js {:title title :body body})))

(defn default-renderer [input]
  (str "<aside class=\"clono-column\">\n\n"
       "<p class=\"clono-column-title\">"
       (gstring/htmlEscape (.-title input))
       "</p>\n\n"
       (.-body input)
       "\n\n</aside>"))

(defn title-diagnostic [node source-name title]
  (if-let [label (label-node node)]
    (let [label-children (vec (ast/children label))]
      (when (or (not-every? #(= "text" (.-type %)) label-children)
                (str/blank? title))
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

          (and (= "textDirective" type)
               (= "index" (.-name node)))
          (recur (next remaining) invalid)

          (contains? allowed-content-node-types type)
          (recur (concat (ast/children node) (next remaining)) invalid)

          :else
          (recur (next remaining) (conj invalid node))))
      invalid)))

(defn content-diagnostics [node source-name body-nodes known-directive-names]
  (if (empty? body-nodes)
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
     (invalid-content-nodes body-nodes known-directive-names))))

(defn diagnostics [node context known-directive-names]
  (let [source-name (:source-name context)]
    (if (not= "containerDirective" (.-type node))
      [(diagnostic/for-node
        source-name
        node
        "`column`はContainer directiveとして記述する必要があります。")]
      (let [{:keys [title body-nodes]} (semantic-content node)
            title-problem (title-diagnostic node source-name title)
            attribute-problem (attribute-diagnostic node source-name)]
        (cond-> (content-diagnostics node source-name body-nodes
                                     known-directive-names)
          (some? title-problem) (conj title-problem)
          (some? attribute-problem) (conj attribute-problem))))))

(defn html-node [value]
  #js {:type "html" :value value})

(defn- thenable? [value]
  (and (some? value)
       (contains? #{"object" "function"} (goog/typeOf value))
       (fn? (gobj/get value "then"))))

(defn- renderer-name [registration]
  (if-let [plugin (:plugin registration)]
    (or (some-> (:definition plugin) (gobj/get "name"))
        (:name plugin)
        "外部プラグイン")
    "組み込みの既定renderer"))

(defn- renderer-failure! [node context registration reason]
  (throw
   (ex-info
    "Column renderer failed"
    {:clono/renderer-diagnostic
     (node-diagnostic
      (:source-name context)
      node
      (str "コラムrenderer（" (renderer-name registration) "）" reason))})))

(defn- error-summary [error]
  (let [message (try
                  (gobj/get error "message")
                  (catch :default _ nil))
        raw (if (and (string? message) (not (str/blank? message)))
              message
              (try
                (str error)
                (catch :default _ "")))
        first-line (first (str/split raw #"\r\n|[\r\n\u2028\u2029]" 2))
        summary (str/trim (str/replace first-line #"\t+" " "))]
    (if (str/blank? summary) "詳細不明の例外" summary)))

(defn- invoke-renderer [node context registration renderer input]
  (try
    (renderer input)
    (catch :default error
      (renderer-failure!
       node context registration
       (str "の実行に失敗しました: " (error-summary error))))))

(defn- validated-output [node context registration output]
  (cond
    (try
      (thenable? output)
      (catch :default error
        (renderer-failure!
         node context registration
         (str "の戻り値を確認できません: " (error-summary error)))))
    (renderer-failure! node context registration
                       "はPromiseなどの非同期結果を返せません。")

    (not (string? output))
    (renderer-failure! node context registration
                       "は空白ではない文字列を返してください。")

    (str/blank? output)
    (renderer-failure! node context registration
                       "は空白ではない文字列を返してください。")

    :else output))

(defn transform [node context]
  (let [registration (get-in context [:registry "column"])
        renderer (or (:renderer registration) default-renderer)
        output (invoke-renderer node context registration renderer
                                (normalized-data node))]
    [(html-node (validated-output node context registration output))]))

(def rule
  {:node-type "containerDirective"
   :allowed-attribute-names #{}
   :diagnostics diagnostics
   :transform transform})
