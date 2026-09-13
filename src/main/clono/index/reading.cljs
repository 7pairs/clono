(ns clono.index.reading
  (:require
   [clojure.string :as str]))

(def ^:private extended-katakana
  {"ヷ" "わ\u3099"
   "ヸ" "ゐ\u3099"
   "ヹ" "ゑ\u3099"
   "ヺ" "を\u3099"})

(defn- fail! [code reading message]
  (throw (ex-info message
                  {:code code
                   :reading reading})))

(defn- katakana->hiragana [value]
  (->> (array-seq (js/Array.from value))
       (map (fn [character]
              (let [code-point (.codePointAt character 0)]
                (cond
                  (<= 0x30a1 code-point 0x30f6)
                  (js/String.fromCodePoint (- code-point 0x60))

                  (contains? extended-katakana character)
                  (get extended-katakana character)

                  :else
                  character))))
       (apply str)))

(defn normalize [reading]
  (when-not (string? reading)
    (fail! :reading-not-string reading
           "Reading must be a string"))
  (let [normalized (-> reading
                       (.normalize "NFKC")
                       katakana->hiragana
                       (.toLowerCase))]
    (when (str/blank? normalized)
      (fail! :reading-empty reading
             "Reading must not be empty"))
    (when (not= normalized (str/trim normalized))
      (fail! :reading-surrounding-whitespace reading
             "Reading must not contain surrounding whitespace"))
    (when-not (or (re-matches #"^(?:[ぁ-ゖ][゙゚]?|ー)+$" normalized)
                  (re-matches #"^[a-z0-9][a-z0-9 .+@#/_-]*$" normalized))
      (fail! :reading-unsupported-characters reading
             "Reading contains unsupported characters"))
    normalized))
