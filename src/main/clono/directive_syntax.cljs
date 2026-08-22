(ns clono.directive-syntax
  (:require
   ["micromark" :refer [parse postprocess preprocess]]
   ["micromark-extension-directive" :refer [directive]]
   [clono.ast :as ast]
   [clono.diagnostic :as diagnostic]
   [goog.object :as gobj]))

(def micromark-directive-types
  {"directiveContainer" "containerDirective"
   "directiveLeaf" "leafDirective"
   "directiveText" "textDirective"})

(def micromark-attribute-types
  #{"directiveContainerAttributes"
    "directiveLeafAttributes"
    "directiveTextAttributes"})

(defn micromark-events [source]
  (let [chunks ((preprocess) source nil true)
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

(defn unclosed-container-diagnostics
  [events source-name known-directive-names]
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
                unclosed? (and (contains? known-directive-names name)
                               (< fence-count 2))]
            (recur (next remaining)
                   (pop stack)
                   (cond-> diagnostics
                     unclosed?
                     (conj (diagnostic/at-point
                            source-name
                            name
                            (.-start token)
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
                              (ast/property (event-token event) "start" "offset")]
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

(defn malformed-text-attribute-diagnostics
  [source tree events source-name required-text-attribute-names]
  (let [parsed-attribute-keys (parsed-attribute-directive-keys events)]
    (->> (ast/nodes tree)
         (keep (fn [node]
                 (let [type (.-type node)
                       name (.-name node)
                       start-offset (ast/property node "position" "start" "offset")
                       end (ast/property node "position" "end")
                       end-offset (when end (.-offset end))]
                   (when (and (= "textDirective" type)
                              (contains? required-text-attribute-names name)
                              (number? start-offset)
                              (number? end-offset)
                              (= "{" (.charAt source end-offset))
                              (not (contains? parsed-attribute-keys
                                              [type start-offset])))
                     (diagnostic/at-point
                      source-name
                      name
                      end
                      (str "`" name "`の属性を解析できません。"))))))
         vec)))

(defn diagnostics
  [source
   tree
   source-name
   {:keys [known-directive-names required-text-attribute-names]}]
  (let [events (micromark-events source)]
    (concat
     (unclosed-container-diagnostics
      events
      source-name
      known-directive-names)
     (malformed-text-attribute-diagnostics
      source
      tree
      events
      source-name
      required-text-attribute-names))))
