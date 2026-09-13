(ns clono.index.sorting
  (:require
   [clojure.string :as str]
   [clono.index.reading :as reading]))

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

(def ^:private small-kana
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

(def ^:private vowel-by-kana
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

(defn- fail! [code reading message]
  (throw (ex-info message
                  {:code code
                   :reading reading})))

(defn- expand-prolonged-marks [normalized-reading source-reading]
  (loop [characters (seq (array-seq (js/Array.from normalized-reading)))
         result []]
    (if-let [character (first characters)]
      (if (= "ー" character)
        (if-let [vowel (->> result
                            rseq
                            (remove #{"\u3099" "\u309a"})
                            first
                            (get vowel-by-kana))]
          (recur (next characters) (conj result vowel))
          (fail! :reading-invalid-prolonged-mark
                 source-reading
                 "Prolonged mark must follow kana with a known vowel"))
        (recur (next characters) (conj result character)))
      (apply str result))))

(defn- remove-voicing [normalized-reading]
  (-> normalized-reading
      (.normalize "NFD")
      (str/replace #"[゙゚]" "")
      (.normalize "NFC")))

(defn- expand-small-kana [normalized-reading]
  (->> (array-seq (js/Array.from normalized-reading))
       (map #(get small-kana % %))
       (apply str)))

(defn sort-key [value]
  (let [normalized-reading (reading/normalize value)]
    (if (re-matches #"^[a-z0-9].*$" normalized-reading)
      normalized-reading
      (-> (expand-prolonged-marks normalized-reading value)
          remove-voicing
          expand-small-kana))))

(defn group-id [value]
  (let [key (sort-key value)
        initial (.charAt key 0)]
    (cond
      (re-matches #"[a-z0-9]" initial) :alphanumeric
      (str/includes? "あいうえお" initial) :a
      (str/includes? "かきくけこ" initial) :ka
      (str/includes? "さしすせそ" initial) :sa
      (str/includes? "たちつてと" initial) :ta
      (str/includes? "なにぬねの" initial) :na
      (str/includes? "はひふへほ" initial) :ha
      (str/includes? "まみむめも" initial) :ma
      (str/includes? "やゆよ" initial) :ya
      (str/includes? "らりるれろ" initial) :ra
      (str/includes? "わゐゑをん" initial) :wa
      :else
      (fail! :reading-unsupported-initial
             value
             "Reading cannot be assigned to an index group"))))
