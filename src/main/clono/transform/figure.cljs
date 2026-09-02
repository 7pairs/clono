(ns clono.transform.figure
  (:require
   ["node:fs" :as fs]
   ["node:path" :as path]
   [clojure.string :as str]
   [clono.ast :as ast]
   [clono.diagnostic :as diagnostic]
   [clono.directive-validation :as directive-validation]
   [goog.object :as gobj]
   [goog.string :as gstring]))

(def logical-id-pattern
  #"^[a-z][a-z0-9-]*$")

(def allowed-document-kinds
  #{"chapter" "appendix"})

(def scheme-pattern
  #"^[A-Za-z][A-Za-z0-9+.-]*:")

(defn- attributes [node]
  (or (.-attributes node) #js {}))

(defn- directive-label? [node]
  (and (some? (.-data node))
       (true? (ast/property node "data" "directiveLabel"))))

(defn- label-node [node]
  (first (filter directive-label? (ast/children node))))

(defn- body-children [node]
  (remove directive-label? (ast/children node)))

(defn- label-value [label]
  (apply str (map #(.-value %) (ast/children label))))

(defn- logical-id [node]
  (gobj/get (attributes node) "id"))

(defn- figure-id [id]
  (str "figure-" id))

(defn- caption-id [id]
  (str (figure-id id) "-caption"))

(defn- node-diagnostic [context node message]
  (diagnostic/at-point
   (:source-name context)
   "figure"
   (ast/property node "position" "start")
   message))

(defn- caption-diagnostics [node context]
  (if-let [label (label-node node)]
    (let [children (vec (ast/children label))]
      (if (or (not-every? #(= "text" (.-type %)) children)
              (str/blank? (label-value label)))
        [(node-diagnostic
          context
          label
          "`figure`のキャプションには空白ではないプレーンテキストが必要です。")]
        []))
    [(node-diagnostic
      context
      node
      "`figure`にはプレーンテキストのキャプションが必要です。")]))

(defn- attribute-diagnostics [node context]
  (let [node-attributes (attributes node)
        id (gobj/get node-attributes "id")
        names (set (array-seq (js/Object.keys node-attributes)))]
    (cond-> []
      (nil? id)
      (conj (node-diagnostic context node "`figure`には`id`属性が必要です。"))

      (and (some? id) (not (re-matches logical-id-pattern id)))
      (conj (node-diagnostic
             context
             node
             "`figure`の`id`属性には英小文字で始まる英小文字、数字、ハイフンだけの値を指定してください。"))

      (not (every? #{"id"} names))
      (conj (node-diagnostic
             context
             node
             "`figure`には`id`以外の属性を指定できません。")))))

(defn- image-node [node]
  (let [body (vec (body-children node))]
    (when (and (= 1 (count body))
               (= "paragraph" (.-type (first body))))
      (let [children (vec (ast/children (first body)))]
        (when (and (= 1 (count children))
                   (= "image" (.-type (first children))))
          (first children))))))

(defn- contains-unknown-directive? [node known-directive-names]
  (->> (rest (ast/nodes node))
       (some #(directive-validation/unknown-directive?
               %
               known-directive-names))))

(defn- content-diagnostics [node context known-directive-names]
  (if (contains-unknown-directive? node known-directive-names)
    []

    (if-let [image (image-node node)]
      (if (some? (.-title image))
        [(node-diagnostic
          context
          image
          "`figure`のMarkdown画像にはタイトルを指定できません。")]
        [])
      [(node-diagnostic
        context
        node
        "`figure`の直下には一つのMarkdown画像だけを含む段落を一つ記述してください。")])))

(defn- invalid-image-url-message [url]
  (cond
    (str/blank? url)
    "`figure`の画像URLに空の値を指定できません。"

    (str/includes? url "\\")
    "`figure`の画像URLにはバックスラッシュを使用できません。"

    (str/starts-with? url "/")
    "`figure`の画像URLにはルート相対パスまたはネットワークパス参照を指定できません。"

    (re-find scheme-pattern url)
    "`figure`の画像URLにはスキームを指定できません。"

    (or (str/includes? url "?")
        (str/includes? url "#"))
    "`figure`の画像URLにはクエリ文字列またはフラグメントを指定できません。"))

(defn- descendant-or-same? [root candidate]
  (let [relative (.relative path root candidate)]
    (or (empty? relative)
        (and (not= relative "..")
             (not (.startsWith relative (str ".." (.-sep path))))
             (not (.isAbsolute path relative))))))

(defn- portable-to-native [portable-path]
  (str/replace portable-path "/" (.-sep path)))

(defn- build-image-path-diagnostics [image context]
  (let [url (.-url image)
        source-root (:source-root-path context)
        input-path (:input-path context)]
    (if (or (nil? source-root) (nil? input-path))
      [(node-diagnostic
        context
        image
        "`figure`の画像パスを検証するための書籍プロジェクト情報がありません。")]
      (try
        (let [decoded (js/decodeURIComponent url)]
          (if (str/includes? decoded "\\")
            [(node-diagnostic
              context
              image
              "`figure`の画像URLをデコードしたパスにバックスラッシュを使用できません。")]
            (let [candidate (.resolve path
                                      (.dirname path input-path)
                                      (portable-to-native decoded))]
              (cond
                (not (descendant-or-same? source-root candidate))
                [(node-diagnostic
                  context
                  image
                  "`figure`の画像パスは入力原稿ルートの内側を指す必要があります。")]

                (not (.existsSync fs candidate))
                [(node-diagnostic
                  context
                  image
                  "`figure`の画像ファイルが存在しません。")]

                :else
                (let [candidate-stat (.lstatSync fs candidate)]
                  (cond
                    (.isSymbolicLink candidate-stat)
                    [(node-diagnostic
                      context
                      image
                      "`figure`の画像ファイルにシンボリックリンクを使用できません。")]

                    (not (.isFile candidate-stat))
                    [(node-diagnostic
                      context
                      image
                      "`figure`の画像パスには通常ファイルを指定してください。")]

                    (not (descendant-or-same?
                          (.realpathSync fs source-root)
                          (.realpathSync fs candidate)))
                    [(node-diagnostic
                      context
                      image
                      "`figure`の画像パスは入力原稿ルートの外側を指すことができません。")]

                    :else
                    []))))))
        (catch :default _
          [(node-diagnostic
            context
            image
            "`figure`の画像URLを安全なファイルパスとして解決できません。")])))))

(defn- image-path-diagnostics [node context]
  (if-let [image (image-node node)]
    (let [url (.-url image)]
      (if-let [message (invalid-image-url-message url)]
        [(node-diagnostic context image message)]
        (if (= :build (:mode context))
          (build-image-path-diagnostics image context)
          [])))
    []))

(defn diagnostics [node context known-directive-names]
  (if (not= "containerDirective" (.-type node))
    [(node-diagnostic
      context
      node
      "`figure`はContainer directiveとして記述する必要があります。")]
    (vec (concat (caption-diagnostics node context)
                 (attribute-diagnostics node context)
                 (content-diagnostics node context known-directive-names)
                 (image-path-diagnostics node context)))))

(defn- parent-map [tree]
  (let [result (js/Map.)]
    (doseq [parent (ast/nodes tree)
            child (ast/children parent)]
      (.set result child parent))
    result))

(defn- directive-ancestor? [node parents]
  (loop [ancestor (.get parents node)]
    (cond
      (nil? ancestor) false
      (ast/directive-node? ancestor) true
      :else (recur (.get parents ancestor)))))

(defn- figure-nodes [tree known-directive-names]
  (->> (directive-validation/validation-nodes tree known-directive-names)
       (filter #(and (= "figure" (.-name %))
                     (= "containerDirective" (.-type %))))
       vec))

(defn- placement-diagnostics [tree figures context]
  (let [parents (parent-map tree)]
    (->> figures
         (keep (fn [node]
                 (when (and (not (identical? tree (.get parents node)))
                            (not (directive-ancestor? node parents)))
                   (node-diagnostic
                    context
                    node
                    "`figure`はMarkdown文書のトップレベルに記述する必要があります。"))))
         vec)))

(defn- document-kind-diagnostics [figures context]
  (let [entry (:publication-entry context)]
    (if (and (= :build (:mode context))
             (not (contains? allowed-document-kinds (:kind entry))))
      (mapv #(node-diagnostic
              context
              %
              "`figure`は本文または付録の掲載Markdownにだけ記述できます。")
            figures)
      [])))

(defn- valid-logical-id [node]
  (let [id (logical-id node)]
    (when (and (string? id) (re-matches logical-id-pattern id))
      id)))

(defn- duplicate-id-diagnostics [figures context]
  (:diagnostics
   (reduce
    (fn [{:keys [logical-ids html-ids diagnostics] :as result} node]
      (if-let [id (valid-logical-id node)]
        (if (contains? logical-ids id)
          (update result
                  :diagnostics
                  conj
                  (node-diagnostic
                   context
                   node
                   (str "`figure`の論理ID`" id "`が重複しています。")))
          (let [generated-ids [(figure-id id) (caption-id id)]
                collision (first (filter #(contains? html-ids %) generated-ids))]
            (cond-> (assoc result
                           :logical-ids (conj logical-ids id)
                           :html-ids (into html-ids generated-ids))
              collision
              (update :diagnostics
                      conj
                      (node-diagnostic
                       context
                       node
                       (str "`figure`から生成するHTML ID`" collision
                            "`が重複しています。"))))))
        result))
    {:logical-ids #{}
     :html-ids #{}
     :diagnostics []}
    figures)))

(defn document-diagnostics [tree context known-directive-names]
  (let [figures (figure-nodes tree known-directive-names)]
    (concat (placement-diagnostics tree figures context)
            (document-kind-diagnostics figures context)
            (duplicate-id-diagnostics figures context))))

(defn collect-reference-targets [node context]
  (let [id (logical-id node)
        start (ast/property node "position" "start")]
    [{:logical-id id
      :type "figure"
      :target-id (figure-id id)
      :title-target-id (caption-id id)
      :numbered? true
      :source-name (:source-name context)
      :line (ast/property start "line")
      :column (ast/property start "column")}]))

(defn- html-node [value]
  #js {:type "html" :value value})

(defn transform [node _context]
  (let [id (logical-id node)
        caption (label-value (label-node node))
        image (image-node node)]
    [(html-node
      (str "<figure class=\"clono-numbered-figure\" id=\""
           (figure-id id)
           "\">\n"
           "<img src=\""
           (gstring/htmlEscape (.-url image))
           "\" alt=\""
           (gstring/htmlEscape (.-alt image))
           "\">\n"
           "<figcaption class=\"clono-figure-caption\" id=\""
           (caption-id id)
           "\">"
           (gstring/htmlEscape caption)
           "</figcaption>\n"
           "</figure>"))]))

(def rule
  {:node-type "containerDirective"
   :allowed-attribute-names #{"id"}
   :diagnostics diagnostics
   :document-diagnostics document-diagnostics
   :collect-reference-targets collect-reference-targets
   :transform transform})
