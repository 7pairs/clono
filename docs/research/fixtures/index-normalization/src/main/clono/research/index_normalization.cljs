(ns clono.research.index-normalization
  (:require
   [clojure.string :as string]))

(def group-definitions
  [{:id :alphanumeric :title "英数字"}
   {:id :a :title "あ行"}
   {:id :ka :title "か行"}
   {:id :sa :title "さ行"}
   {:id :ta :title "た行"}
   {:id :na :title "な行"}
   {:id :ha :title "は行"}
   {:id :ma :title "ま行"}
   {:id :ya :title "や行"}
   {:id :ra :title "ら行"}
   {:id :wa :title "わ行"}])

(def group-ranks
  (into {}
        (map-indexed (fn [index {:keys [id]}]
                       [id index])
                     group-definitions)))

(def small-kana
  {"ぁ" "あ"
   "ぃ" "い"
   "ぅ" "う"
   "ぇ" "え"
   "ぉ" "お"
   "っ" "つ"
   "ゃ" "や"
   "ゅ" "ゆ"
   "ょ" "よ"
   "ゎ" "わ"
   "ゕ" "か"
   "ゖ" "け"})

(def vowel-by-kana
  (into {}
        (mapcat (fn [[vowel kana]]
                  (map (fn [character]
                         [character vowel])
                       (array-seq (js/Array.from kana))))
                [["あ" "ぁあかがさざただなはばぱまゃやらゎわゕ"]
                 ["い" "ぃいきぎしじちぢにひびぴみりゐ"]
                 ["う" "ぅうくぐすずつづぬふぶぷむゅゆるゔ"]
                 ["え" "ぇえけげせぜてでねへべぺめれゑゖ"]
                 ["お" "ぉおこごそぞとのほぼぽもょよろを"]])))

(defn- fail-reading! [code reading message]
  (throw (ex-info message
                  {:code code
                   :reading reading})))

(defn- katakana->hiragana [value]
  (->> (array-seq (js/Array.from value))
       (map (fn [character]
              (let [code-point (.codePointAt character 0)]
                (if (<= 0x30a1 code-point 0x30f6)
                  (js/String.fromCodePoint (- code-point 0x60))
                  character))))
       (apply str)))

(defn normalize-reading [reading]
  (when-not (string? reading)
    (fail-reading! :reading-not-string reading
                   "Reading must be a string"))
  (let [normalized (-> reading
                       (.normalize "NFKC")
                       katakana->hiragana
                       (.toLowerCase))]
    (when (string/blank? normalized)
      (fail-reading! :reading-empty reading
                     "Reading must not be empty"))
    (when (not= normalized (string/trim normalized))
      (fail-reading! :reading-surrounding-whitespace reading
                     "Reading must not contain surrounding whitespace"))
    (when-not (or (re-matches #"^[ぁ-ゖー]+$" normalized)
                  (re-matches #"^[a-z0-9][a-z0-9 .+@#/_-]*$" normalized))
      (fail-reading! :reading-unsupported-characters reading
                     "Reading contains unsupported characters"))
    normalized))

(defn- expand-prolonged-marks [reading]
  (loop [characters (seq (array-seq (js/Array.from reading)))
         result []]
    (if-let [character (first characters)]
      (if (= "ー" character)
        (if-let [vowel (get vowel-by-kana (peek result))]
          (recur (next characters) (conj result vowel))
          (fail-reading! :reading-invalid-prolonged-mark reading
                         "Prolonged mark must follow kana with a known vowel"))
        (recur (next characters) (conj result character)))
      (apply str result))))

(defn- remove-voicing [reading]
  (-> reading
      (.normalize "NFD")
      (string/replace #"[゙゚]" "")
      (.normalize "NFC")))

(defn- expand-small-kana [reading]
  (->> (array-seq (js/Array.from reading))
       (map #(get small-kana % %))
       (apply str)))

(defn reading-sort-key [reading]
  (let [normalized (normalize-reading reading)]
    (if (re-matches #"^[a-z0-9].*$" normalized)
      normalized
      (-> normalized
          expand-prolonged-marks
          remove-voicing
          expand-small-kana))))

(defn reading-group [reading]
  (let [sort-key (reading-sort-key reading)
        initial (.charAt sort-key 0)]
    (cond
      (re-matches #"[a-z0-9]" initial) :alphanumeric
      (string/includes? "あいうえお" initial) :a
      (string/includes? "かきくけこ" initial) :ka
      (string/includes? "さしすせそ" initial) :sa
      (string/includes? "たちつてと" initial) :ta
      (string/includes? "なにぬねの" initial) :na
      (string/includes? "はひふへほ" initial) :ha
      (string/includes? "まみむめも" initial) :ma
      (string/includes? "やゆよ" initial) :ya
      (string/includes? "らりるれろ" initial) :ra
      (string/includes? "わゐゑをん" initial) :wa
      :else
      (fail-reading! :reading-unsupported-initial reading
                     "Reading cannot be assigned to an index group"))))

(defn compare-code-points [left right]
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

(defn- compare-index-entries [left right]
  (let [comparisons [(compare (get group-ranks (:group left))
                              (get group-ranks (:group right)))
                     (compare-code-points (:sort-key left) (:sort-key right))
                     (compare-code-points (:normalized-reading left)
                                          (:normalized-reading right))
                     (compare-code-points (:normalized-term left)
                                          (:normalized-term right))
                     (compare-code-points (:term left) (:term right))
                     (compare (:first-position left) (:first-position right))]]
    (or (first (drop-while zero? comparisons)) 0)))

(defn- analyze-entry [position {:keys [term reading occurrence] :as entry}]
  (when-not (and (string? term)
                 (not (string/blank? term)))
    (throw (ex-info "Index term must be a non-empty string"
                    {:code :term-invalid
                     :entry entry})))
  (let [normalized-reading (normalize-reading reading)]
    {:term term
     :normalized-term (.normalize term "NFKC")
     :reading reading
     :normalized-reading normalized-reading
     :sort-key (reading-sort-key normalized-reading)
     :group (reading-group normalized-reading)
     :occurrence occurrence
     :first-position position}))

(defn- conflicting-readings [entries]
  (->> entries
       (group-by :term)
       (keep (fn [[term term-entries]]
               (let [readings (->> term-entries
                                   (map :normalized-reading)
                                   distinct
                                   (sort compare-code-points)
                                   vec)]
                 (when (< 1 (count readings))
                   {:term term
                    :readings readings}))))
       (sort #(compare-code-points (:term %1) (:term %2)))
       vec))

(defn- merge-term-entries [term-entries]
  (let [ordered (sort-by :first-position term-entries)
        first-entry (first ordered)]
    (assoc first-entry
           :occurrences (mapv :occurrence ordered))))

(defn build-index [entries]
  (let [analyzed (mapv analyze-entry (range) entries)
        conflicts (conflicting-readings analyzed)]
    (when (seq conflicts)
      (throw (ex-info "The same index term has conflicting readings"
                      {:code :term-reading-conflict
                       :conflicts conflicts})))
    (let [sorted-entries (->> analyzed
                              (group-by :term)
                              vals
                              (map merge-term-entries)
                              (sort compare-index-entries)
                              vec)
          entries-by-group (group-by :group sorted-entries)
          groups (->> group-definitions
                      (keep (fn [{:keys [id title]}]
                              (when-let [group-entries (seq (get entries-by-group id))]
                                {:id id
                                 :title title
                                 :entries (vec group-entries)})))
                      vec)]
      {:entries sorted-entries
       :groups groups})))
