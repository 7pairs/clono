(ns clono.research.directive-syntax
  (:require
   ["micromark" :refer [parse postprocess preprocess]]
   ["micromark-extension-directive" :refer [directive]]
   [clono.research.markdown-ast :as markdown-ast]
   [goog.object :as gobj]))

(def directive-node-types
  #{"containerDirective" "leafDirective" "textDirective"})

(def micromark-directive-types
  {"directiveContainer" "containerDirective"
   "directiveLeaf" "leafDirective"
   "directiveText" "textDirective"})

(def micromark-attribute-types
  #{"directiveContainerAttributes"
    "directiveLeafAttributes"
    "directiveTextAttributes"})

(defn children [node]
  (when (js/Array.isArray (.-children node))
    (array-seq (.-children node))))

(defn nodes [tree]
  (tree-seq #(some? (children %)) children tree))

(defn property [object & names]
  (reduce gobj/get object names))

(defn micromark-events [markdown]
  (let [chunks ((preprocess) markdown nil true)
        parser (parse #js {:extensions #js [(directive)]})]
    (postprocess (.write (.document parser) chunks))))

(defn event-kind [event]
  (aget event 0))

(defn event-token [event]
  (aget event 1))

(defn event-type [event]
  (.-type (event-token event)))

(defn event-source [event]
  (let [context (aget event 2)
        slice-serialize (gobj/get context "sliceSerialize")]
    (.call slice-serialize context (event-token event))))

(defn update-top [stack f]
  (conj (pop stack) (f (peek stack))))

(defn diagnostic [source-name directive-name point kind message]
  {:file source-name
   :line (gobj/get point "line")
   :column (gobj/get point "column")
   :directive directive-name
   :kind kind
   :message message
   ::offset (gobj/get point "offset")})

(defn unclosed-container-diagnostics [events source-name known-names]
  (loop [remaining (seq events)
         stack []
         diagnostics []]
    (if-let [event (first remaining)]
      (let [kind (event-kind event)
            type (event-type event)]
        (cond
          (and (= "enter" kind) (= "directiveContainer" type))
          (recur (next remaining)
                 (conj stack {:name nil
                              :fence-count 0
                              :token (event-token event)})
                 diagnostics)

          (and (= "exit" kind)
               (= "directiveContainerName" type)
               (seq stack))
          (recur (next remaining)
                 (update-top stack #(assoc % :name (event-source event)))
                 diagnostics)

          (and (= "enter" kind)
               (= "directiveContainerFence" type)
               (seq stack))
          (recur (next remaining)
                 (update-top stack #(update % :fence-count inc))
                 diagnostics)

          (and (= "exit" kind)
               (= "directiveContainer" type)
               (seq stack))
          (let [{:keys [name fence-count token]} (peek stack)
                unclosed? (and (contains? known-names name)
                               (< fence-count 2))]
            (recur (next remaining)
                   (pop stack)
                   (cond-> diagnostics
                     unclosed?
                     (conj (diagnostic
                            source-name
                            name
                            (.-start token)
                            :unclosed-container
                            (str "`" name "`の終了マーカーがありません。"))))))

          :else
          (recur (next remaining) stack diagnostics)))
      diagnostics)))

(defn parsed-attribute-directive-keys [events]
  (loop [remaining (seq events)
         stack []
         result #{}]
    (if-let [event (first remaining)]
      (let [kind (event-kind event)
            type (event-type event)]
        (cond
          (and (= "enter" kind)
               (contains? micromark-directive-types type))
          (recur (next remaining)
                 (conj stack
                       {:key [(get micromark-directive-types type)
                              (property (event-token event) "start" "offset")]
                        :attributes-parsed? false})
                 result)

          (and (= "enter" kind)
               (contains? micromark-attribute-types type)
               (seq stack))
          (recur (next remaining)
                 (update-top stack #(assoc % :attributes-parsed? true))
                 result)

          (and (= "exit" kind)
               (contains? micromark-directive-types type)
               (seq stack))
          (let [{:keys [key attributes-parsed?]} (peek stack)]
            (recur (next remaining)
                   (pop stack)
                   (cond-> result
                     attributes-parsed? (conj key))))

          :else
          (recur (next remaining) stack result)))
      result)))

(defn malformed-attribute-diagnostics
  [markdown tree events source-name required-attribute-names]
  (let [parsed-attribute-keys (parsed-attribute-directive-keys events)]
    (->> (nodes tree)
         (keep (fn [node]
                 (let [type (.-type node)
                       name (.-name node)
                       start-offset (property node "position" "start" "offset")
                       end (property node "position" "end")
                       end-offset (when end (.-offset end))]
                   (when (and (contains? directive-node-types type)
                              (contains? required-attribute-names name)
                              (number? start-offset)
                              (number? end-offset)
                              (= "{" (.charAt markdown end-offset))
                              (not (contains? parsed-attribute-keys
                                              [type start-offset])))
                     (diagnostic
                      source-name
                      name
                      end
                      :malformed-attributes
                      (str "`" name "`の属性を解析できません。"))))))
         vec)))

(defn syntax-diagnostics
  [markdown source-name {:keys [known-names required-attribute-names]}]
  (let [tree (markdown-ast/parse markdown)
        events (micromark-events markdown)]
    (->> (concat
          (unclosed-container-diagnostics events source-name known-names)
          (malformed-attribute-diagnostics
           markdown
           tree
           events
           source-name
           required-attribute-names))
         (sort-by ::offset)
         (mapv #(dissoc % ::offset)))))
