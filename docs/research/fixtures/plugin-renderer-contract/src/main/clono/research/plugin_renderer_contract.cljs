(ns clono.research.plugin-renderer-contract
  (:require
   [clojure.string :as str]
   [goog.object :as gobj]
   [goog.string :as gstring]))

(defn render-column [renderer input]
  (renderer input))

(defn- thenable? [value]
  (and (some? value)
       (contains? #{"object" "function"} (goog/typeOf value))
       (fn? (gobj/get value "then"))))

(defn- diagnostic [context message]
  {:file (:source-name context)
   :line (:line context)
   :column (:column context)
   :directive "column"
   :message message})

(defn- error-message [error]
  (let [message (when (some? error) (gobj/get error "message"))]
    (if (string? message)
      message
      (str error))))

(defn render-column-result [renderer input context]
  (let [invocation (try
                     {:threw? false
                      :output (render-column renderer input)}
                     (catch :default error
                       {:threw? true
                        :error error}))]
    (if (:threw? invocation)
      {:ok? false
       :output nil
       :diagnostics
       [(diagnostic
         context
         (str "コラムrendererの実行に失敗しました: "
              (error-message (:error invocation))))]}
      (let [output (:output invocation)]
        (cond
          (thenable? output)
          {:ok? false
           :output nil
           :diagnostics
           [(diagnostic
             context
             "コラムrendererはPromiseではなく文字列を同期的に返す必要があります。")]}

          (not (string? output))
          {:ok? false
           :output nil
           :diagnostics
           [(diagnostic
             context
             (str "コラムrendererは文字列を返す必要があります: "
                  (goog/typeOf output)))]}

          :else
          {:ok? true
           :output output
           :diagnostics []})))))

(defn render-columns [renderer columns]
  (loop [remaining columns
         outputs []]
    (if-let [{:keys [input context]} (first remaining)]
      (let [result (render-column-result renderer input context)]
        (if (:ok? result)
          (recur (next remaining) (conj outputs (:output result)))
          result))
      {:ok? true
       :output (str/join "\n\n" outputs)
       :diagnostics []})))

(defn default-column-renderer [input]
  (let [title (gobj/get input "title")
        body (gobj/get input "body")]
    (str "<aside class=\"clono-column\">\n\n"
         "<p class=\"clono-column-title\">"
         (gstring/htmlEscape title)
         "</p>\n\n"
         body
         "\n\n</aside>")))
