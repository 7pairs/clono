(ns clono.transform.heading
  (:require
   [clono.ast :as ast]))

(def ^:private supported-depths
  #{1 2 3})

(def ^:private atx-heading-prefix-pattern
  #"^[ \t]{0,3}(#{1,6})(?:[ \t]+|$)")

(def ^:private heading-id-suffix-pattern
  #"([ \t]+\{#([^{}\r\n]*)\})[ \t]*$")

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
