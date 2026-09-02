(ns clono.transform
  (:require
   [clono.ast :as ast]
   [clono.directive-validation :as directive-validation]
   [clono.transform.align :as align]
   [clono.transform.column :as column]
   [clono.transform.page-break :as page-break]))

(def rules
  {"align" align/rule
   "column" column/rule
   "page-break" page-break/rule})

(def known-directive-names
  (set (keys rules)))

(def required-text-attribute-names
  (->> rules
       (keep (fn [[name rule]]
               (when (:required-text-attributes? rule) name)))
       set))

(defn validate [tree context]
  (let [node-diagnostics
        (->> (directive-validation/validation-nodes tree known-directive-names)
             (keep (fn [node]
                     (when-let [rule (get rules (.-name node))]
                       [node rule])))
             (mapcat (fn [[node rule]]
                       ((:diagnostics rule)
                        node
                        context
                        known-directive-names))))
        document-diagnostics
        (->> (vals rules)
             (keep :document-diagnostics)
             (mapcat #(% tree context known-directive-names)))]
    (vec (concat node-diagnostics document-diagnostics))))

(declare transform-node!)

(defn transform-children! [node context]
  (when-let [node-children (ast/children node)]
    (set! (.-children node)
          (into-array (mapcat #(transform-node! % context) node-children))))
  node)

(defn transform-node! [node context]
  (transform-children! node context)
  (if-let [rule (get rules (.-name node))]
    ((:transform rule) node context)
    [node]))

(defn transform [tree context]
  (transform-children! tree context))
