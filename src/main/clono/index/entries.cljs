(ns clono.index.entries
  (:require
   [clono.index.sorting :as sorting]))

(def ^:private group-ranks
  (into {}
        (map-indexed (fn [index {:keys [id]}]
                       [id index]))
        sorting/group-definitions))

(defn- compare-code-points [left right]
  (let [left-characters (js/Array.from left)
        right-characters (js/Array.from right)
        shared-length (min (.-length left-characters)
                           (.-length right-characters))]
    (loop [index 0]
      (if (= index shared-length)
        (compare (.-length left-characters)
                 (.-length right-characters))
        (let [left-code-point (.codePointAt (aget left-characters index) 0)
              right-code-point (.codePointAt (aget right-characters index) 0)]
          (if (= left-code-point right-code-point)
            (recur (inc index))
            (compare left-code-point right-code-point)))))))

(defn- compare-entries [left right]
  (let [comparisons
        [(compare (get group-ranks (:group-id left))
                  (get group-ranks (:group-id right)))
         (compare-code-points (:sort-key left) (:sort-key right))
         (compare-code-points (:normalized-reading left)
                              (:normalized-reading right))
         (compare-code-points (:normalized-term left)
                              (:normalized-term right))
         (compare-code-points (:term left) (:term right))
         (compare (::first-position left) (::first-position right))]]
    (or (first (drop-while zero? comparisons)) 0)))

(defn- merge-term-entries [term-entries]
  (let [ordered (sort-by ::position term-entries)
        first-entry (first ordered)
        normalized-readings (vec (distinct (map :normalized-reading ordered)))]
    (when (< 1 (count normalized-readings))
      (throw (ex-info "Index term has conflicting normalized readings"
                      {:code :index-entry-reading-conflict
                       :term (:term first-entry)
                       :normalized-readings normalized-readings})))
    (assoc (select-keys first-entry
                        [:term
                         :normalized-term
                         :reading
                         :normalized-reading
                         :sort-key
                         :group-id])
           ::first-position (::position first-entry)
           :occurrences (mapv #(dissoc % ::position) ordered))))

(defn merge-and-sort [entries]
  (->> entries
       (map-indexed #(assoc %2 ::position %1))
       (group-by :term)
       vals
       (map merge-term-entries)
       (sort compare-entries)
       (mapv #(dissoc % ::first-position))))
