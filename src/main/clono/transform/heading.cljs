(ns clono.transform.heading
  (:require
   [clono.ast :as ast]
   [clono.diagnostic :as diagnostic]
   [clono.reference-id :as reference-id]))

(def ^:private supported-depths
  #{1 2 3})

(def ^:private atx-heading-prefix-pattern
  #"^[ \t]{0,3}(#{1,6})(?:[ \t]+|$)")

(def ^:private heading-id-suffix-pattern
  #"([ \t]+\{#([^{}\r\n]*)\})[ \t]*$")

(def ^:private numbered-document-kinds
  #{"chapter" "appendix"})

(defn- source-fragment [source node]
  (let [start (ast/property node "position" "start" "offset")
        end (ast/property node "position" "end" "offset")]
    (when (and (string? source)
               (int? start)
               (int? end)
               (<= 0 start end (count source)))
      (.slice source start end))))

(defn- supported-atx-heading? [source node]
  (when (and (= "heading" (.-type node))
             (contains? supported-depths (.-depth node)))
    (when-let [fragment (source-fragment source node)]
      (when-let [[_ hashes] (re-find atx-heading-prefix-pattern fragment)]
        (= (.-depth node) (count hashes))))))

(defn explicit-id-candidate [source heading]
  (when (supported-atx-heading? source heading)
    (when-let [last-child (last (ast/children heading))]
      (when (= "text" (.-type last-child))
        (when-let [fragment (source-fragment source heading)]
          (when-let [[_ suffix value]
                     (re-find heading-id-suffix-pattern fragment)]
            {:value value
             :suffix suffix}))))))

(defn diagnostics [source tree context]
  (->> (ast/nodes tree)
       (keep (fn [node]
               (when-let [candidate (explicit-id-candidate source node)]
                 (when-not (reference-id/valid? (:value candidate))
                   (diagnostic/at-markdown-point
                    (:source-name context)
                    (ast/property node "position" "start")
                    (str "見出しのIDには英小文字で始まる英小文字、"
                         "数字、ハイフンだけの値を指定してください。"))))))
       vec))

(defn- document-kind [context]
  (if (= :transform (:mode context))
    "chapter"
    (get-in context [:publication-entry :kind])))

(defn collect-reference-targets [tree context]
  (let [source (:source context)
        kind (document-kind context)]
    (->> (ast/nodes tree)
         (keep (fn [node]
                 (when-let [candidate (explicit-id-candidate source node)]
                   (let [logical-id (:value candidate)
                         start (ast/property node "position" "start")]
                     (when (reference-id/valid? logical-id)
                       {:logical-id logical-id
                        :type "heading"
                        :target-id logical-id
                        :title-target-id logical-id
                        :numbered? (contains? numbered-document-kinds kind)
                        :heading-depth (.-depth node)
                        :document-kind kind
                        :source-name (:source-name context)
                        :line (ast/property start "line")
                        :column (ast/property start "column")})))))
         vec)))
