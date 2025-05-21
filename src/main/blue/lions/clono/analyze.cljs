; Copyright 2025 HASEBA Junya
; 
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
; 
;     http://www.apache.org/licenses/LICENSE-2.0
; 
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns blue.lions.clono.analyze
  (:require [clojure.string :as str]
            [blue.lions.clono.ast :as ast]
            [blue.lions.clono.identifier :as id]
            [blue.lions.clono.log :as logger]
            [blue.lions.clono.spec :as spec]))

(defn create-toc-item
  [file-name node]
  {:pre [(spec/validate ::spec/file-name file-name
                        "Invalid file name is given.")
         (spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/toc-item % "Invalid item is returned.")]}
  (let [depth (:depth node)
        caption (->> node
                     ast/extract-texts
                     (map :value)
                     str/join)
        base-name (try
                    (id/extract-base-name file-name)
                    (catch js/Error e
                      (throw (ex-info "Failed to extract base name."
                                      {:file-name file-name :cause e}))))
        slug (or (:slug node)
                 (throw (ex-info "Node does not have slug."
                                 {:file-name file-name :node node})))
        url (id/build-url base-name slug)]
    {:depth depth :caption caption :url url}))

(defn create-toc-items
  [documents]
  {:pre [(spec/validate ::spec/documents documents
                        "Invalid documents are given.")]
   :post [(spec/validate ::spec/toc-items % "Invalid items are returned.")]}
  (vec
   (mapcat (fn [{:keys [name ast]}]
             (map #(create-toc-item name %)
                  (ast/extract-headings ast)))
           documents)))

(defn has-valid-id-or-root-depth?
  [node]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/pred-result % "Invalid result is returned.")]}
  (or (-> (ast/extract-labels node)
          first
          :attributes
          :id
          some?)
      (= (:depth node) 1)))

(defn create-heading-info
  [file-name node]
  {:pre [(spec/validate ::spec/file-name file-name
                        "Invalid file name is given.")
         (spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/heading-or-nil %
                         "Invalid heading information is returned.")]}
  (when (has-valid-id-or-root-depth? node)
    (let [{:keys [depth caption url]} (create-toc-item file-name node)
          base-name (id/extract-base-name file-name)
          label-id (-> node
                       ast/extract-labels
                       first
                       :attributes
                       :id)
          id (if (= depth 1)
               base-name
               (id/build-dic-key base-name label-id))]
      {:id id :depth depth :caption caption :url url})))

(defn create-heading-dic
  [documents]
  {:pre [(spec/validate ::spec/documents documents
                        "Invalid documents are given.")]
   :post [(spec/validate ::spec/heading-dic %
                         "Invalid heading dictionary is returned.")]}
  (->> (for [{:keys [name ast]} documents
             nodes (ast/extract-headings ast)
             :let [{:keys [id] :as heading-info}
                   (create-heading-info name nodes)]
             :when id]
         [id heading-info])
       (into {})))

(defn create-footnote-dic
  [documents]
  {:pre [(spec/validate ::spec/documents documents
                        "Invalid documents are given.")]
   :post [(spec/validate ::spec/footnote-dic %
                         "Invalid footnote dictionary is returned.")]}
  (->> (for [{:keys [name ast]} documents
             footnote (ast/extract-footnote-definition ast)
             :let [base-name (id/extract-base-name name)
                   id (:identifier footnote)
                   child (first (:children footnote))]
             :when child]
         [(id/build-dic-key base-name id) child])
       (into {})))

(defn safe-re-pattern
  [pattern-str]
  {:pre [(spec/validate ::spec/pattern-string pattern-str
                        "Invalid pattern string is given.")]
   :post [(spec/validate ::spec/pattern % "Invalid pattern is returned.")]}
  (try
    (re-pattern pattern-str)
    (catch js/Error e
      (throw (ex-info "Invalid regular expression pattern."
                      {:pattern pattern-str :cause e})))))

(defn validate-captions
  [groups]
  {:pre [(spec/validate ::spec/custom-groups groups
                        "Invalid custom groups are given.")]}
  (let [captions (map :caption groups)
        duplicates (filterv #(> (count (filter #{%} captions)) 1)
                            (distinct captions))]
    (when (seq duplicates)
      (throw (ex-info "Duplicate captions are found in index groups."
                      {:duplicates duplicates})))))

(defn validate-defaults
  [groups]
  {:pre [(spec/validate ::spec/custom-groups groups
                        "Invalid custom groups are given.")]}
  (let [defaults (filter :default groups)]
    (when (> (count defaults) 1)
      (throw (ex-info "Multiple default groups are found."
                      {:defaults defaults})))))

(defn load-index-groups
  [config]
  {:pre [(spec/validate ::spec/config config "Invalid config is given.")]
   :post [(spec/validate ::spec/index-groups %
                         "Invalid index groups are returned.")]}
  ; Loads index group settings from configuration.
  ; Index groups define how index entries are categorized into sections in the
  ; final output.
  ; 
  ; If custom index groups are specified in the config, converts string
  ; patterns to RegExp objects.
  ; Otherwise, returns default groups that categorize by:
  ; - English characters and numbers ("英数字")
  ; - Japanese kana by row ("あ行", "か行", etc.)
  ; - A catch-all "その他" (Others) group
  ;
  ; Each group contains:
  ; - caption: Section heading text
  ; - pattern: RegExp for matching normalized ruby strings (or nil if default)
  ; - language: :english or :japanese to specify character set rules
  ; - default: true for the catch-all group
  (let [custom-groups (:index-groups config)]
    (if (seq custom-groups)
      (do
        (validate-captions custom-groups)
        (validate-defaults custom-groups)
        (mapv (fn [group]
                (if (:pattern group)
                  (update group :pattern safe-re-pattern)
                  group))
              custom-groups))
      [{:caption "英数字" :pattern #"^[ -~].*" :language :english}
       {:caption "あ行" :pattern #"^[あいうえお].*" :language :japanese}
       {:caption "か行" :pattern #"^[かきくけこ].*" :language :japanese}
       {:caption "さ行" :pattern #"^[さしすせそ].*" :language :japanese}
       {:caption "た行" :pattern #"^[たちつてと].*" :language :japanese}
       {:caption "な行" :pattern #"^[なにぬねの].*" :language :japanese}
       {:caption "は行" :pattern #"^[はひふへほ].*" :language :japanese}
       {:caption "ま行" :pattern #"^[まみむめも].*" :language :japanese}
       {:caption "や行" :pattern #"^[やゆよ].*" :language :japanese}
       {:caption "ら行" :pattern #"^[らりるれろ].*" :language :japanese}
       {:caption "わ行" :pattern #"^[わをん].*" :language :japanese}
       {:caption "その他" :default true}])))

(defn build-index-entry
  [base-name node]
  {:pre [(spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")
         (spec/validate ::spec/node node "Invalid node is given.")]
   :post [(spec/validate ::spec/index-entry % "Invalid entry is returned.")]}
  (let [{:keys [order id attributes]} node]
    (when (or (nil? order) (nil? id))
      (throw (ex-info "Node is invalid."
                      {:base-name base-name
                       :node node
                       :missing (cond
                                  (nil? order) :order
                                  (nil? id) :id)})))
    {:order order
     :text (->> node
                ast/extract-texts
                (map :value)
                str/join)
     :ruby (:ruby attributes)
     :url (id/build-url base-name id)}))

(defn english-ruby?
  [ruby]
  {:pre [(spec/validate ::spec/ruby ruby "Invalid ruby is given.")]
   :post [(spec/validate ::spec/pred-result % "Invalid result is returned.")]}
  (boolean (re-matches #"^[ -~].*" ruby)))

(defn lowercase->uppercase
  [ruby]
  {:pre [(spec/validate ::spec/ruby ruby "Invalid ruby is given.")]
   :post [(spec/validate ::spec/ruby % "Invalid ruby is returned.")]}
  ; Convert lowercase Latin characters to uppercase while preserving uppercase
  ; letters, symbols, and non-Latin characters such as Japanese.
  (str/replace ruby #"[a-z]" #(.toUpperCase %)))

(defn katakana->hiragana
  [ruby]
  {:pre [(spec/validate ::spec/ruby ruby "Invalid ruby is given.")]
   :post [(spec/validate ::spec/ruby % "Invalid ruby is returned.")]}
  ; Converts Katakana characters to their Hiragana equivalents while preserving
  ; other characters (hiragana, Latin characters, symbols, etc.)
  ; Uses Unicode code point mapping (U+30A1-U+30F6 -> U+3041-U+3096)
  (let [katakana-range #js [0x30A1 0x30F6]
        hiragana-range #js [0x3041 0x3096]
        offset (- (aget hiragana-range 0) (aget katakana-range 0))]
    (str/replace ruby
                 #"[\u30A1-\u30F6]"
                 (fn [ch]
                   (let [code (.charCodeAt ch 0)
                         new-code (+ code offset)]
                     (.fromCharCode js/String new-code))))))

(def ^:private seion-map
  {"ゔ" "う"
   "ぁ" "あ" "ぃ" "い" "ぅ" "う" "ぇ" "え" "ぉ" "お"
   "が" "か" "ぎ" "き" "ぐ" "く" "げ" "け" "ご" "こ"
   "ざ" "さ" "じ" "し" "ず" "す" "ぜ" "せ" "ぞ" "そ"
   "だ" "た" "ぢ" "ち" "づ" "つ" "で" "て" "ど" "と"
   "っ" "つ"
   "ば" "は" "び" "ひ" "ぶ" "ふ" "べ" "へ" "ぼ" "ほ"
   "ぱ" "は" "ぴ" "ひ" "ぷ" "ふ" "ぺ" "へ" "ぽ" "ほ"
   "ゃ" "や" "ゅ" "ゆ" "ょ" "よ"
   "ゎ" "わ"})

(def non-seion-pattern
  (re-pattern (str "[" (str/join (keys seion-map)) "]")))

(defn non-seion->seion
  [ruby]
  {:pre [(spec/validate ::spec/ruby ruby "Invalid ruby is given.")]
   :post [(spec/validate ::spec/ruby % "Invalid ruby is returned.")]}
  (str/replace ruby
               non-seion-pattern
               (fn [match] (get seion-map match))))

(def ^:private vowel-map
  {"あ" "あ" "か" "あ" "さ" "あ" "た" "あ" "な" "あ" "は" "あ" "ま" "あ"
   "や" "あ" "ら" "あ" "わ" "あ"
   "い" "い" "き" "い" "し" "い" "ち" "い" "に" "い" "ひ" "い" "み" "い"
   "り" "い"
   "う" "う" "く" "う" "す" "う" "つ" "う" "ぬ" "う" "ふ" "う" "む" "う"
   "ゆ" "う" "る" "う"
   "え" "え" "け" "え" "せ" "え" "て" "え" "ね" "え" "へ" "え" "め" "え"
   "れ" "え"
   "お" "お" "こ" "お" "そ" "お" "と" "お" "の" "お" "ほ" "お" "も" "お"
   "よ" "お" "ろ" "お" "を" "お"})

(defn onbiki->vowel
  [ruby]
  {:pre [(spec/validate ::spec/ruby ruby "Invalid ruby is given.")]
   :post [(spec/validate ::spec/ruby % "Invalid ruby is returned.")]}
  (let [chars (seq ruby)]
    (apply str
           (loop [result []
                  rest-chars chars]
             (if (empty? rest-chars)
               result
               (let [current (str (first rest-chars))
                     next-chars (rest rest-chars)]
                 (if (= current "ー")
                   (let [has-previous-char (seq result)
                         previous-vowel (when has-previous-char
                                          (some vowel-map
                                                (take-last 1 result)))]
                     (if previous-vowel
                       (recur (conj result previous-vowel) next-chars)
                       (recur (conj result current) next-chars)))
                   (let [normalized (get seion-map current current)]
                     (recur (conj result normalized) next-chars)))))))))

(defn normalize-japanese-ruby
  [ruby]
  {:pre [(spec/validate ::spec/ruby ruby "Invalid ruby is given.")]
   :post [(spec/validate ::spec/ruby % "Invalid ruby is returned.")]}
  ; Normalizes Japanese ruby by:
  ; 1. Normalizing Unicode characters (NFKC)
  ; 2. Converting katakana to hiragana
  ; 3. Converting non-seion characters (dakuon, handakuon, etc.) to seion
  ; 4. Converting prolonged sound marks (onbiki) to appropriate vowels
  (try
    (let [normalized (.normalize ruby "NFKC")]
      (-> normalized
          katakana->hiragana
          non-seion->seion
          onbiki->vowel))
    (catch js/Error e
      (logger/error "Failed to normalize ruby."
                    {:ruby ruby :cause (ex-message e)})
      ruby)))

(defn normalize-ruby
  [ruby]
  {:pre [(spec/validate ::spec/ruby ruby "Invalid ruby is given.")]
   :post [(spec/validate ::spec/ruby % "Invalid ruby is returned.")]}
  ; Normalizes ruby string by:
  ; 1. Converting lowercase letters to uppercase
  ; 2. Applying Japanese normalization (katakana to hiragana, etc.)
  ; Note: The order of these operations is arbitrary and could be reversed
  ; without affecting the end result, as they operate on different character
  ; sets.
  ; Example: "rubyルビ" -> "RUBYるび" -> "RUBYるひ"
  (-> ruby
      lowercase->uppercase
      normalize-japanese-ruby))

(defn ruby->caption
  [ruby index-groups]
  {:pre [(spec/validate ::spec/ruby ruby "Invalid ruby is given.")
         (spec/validate ::spec/index-groups index-groups
                        "Invalid index groups are given.")]
   :post [(spec/validate ::spec/caption % "Invalid caption is returned.")]}
   ; Determines the index caption (section heading) for a given ruby string
   ; based on specified index groups.
   ; 
   ; The function first normalizes the ruby, then identifies if it's English
   ; or Japanese.
   ; It then checks each index group and returns the matching caption when:
   ; - The group is marked as default, or
   ; - For English text: language is :english and pattern matches the
   ;   normalized ruby, or
   ; - For Japanese text: language is :japanese and pattern matches the
   ;   normalized ruby
   ; 
   ; If no match is found, returns "その他" (Others) as fallback.
  (let [normalized-ruby (normalize-ruby ruby)
        english? (english-ruby? ruby)]
    (or
     (some (fn [{:keys [caption pattern language default]}]
             (when (or default
                       (and (= language :english) english?
                            (re-matches pattern normalized-ruby))
                       (and (= language :japanese) (not english?)
                            (re-matches pattern normalized-ruby)))
               caption))
           index-groups)
     "その他")))

(defn insert-row-captions
  [indices index-groups]
  {:pre [(spec/validate ::spec/indices indices "Invalid indices are given.")
         (spec/validate ::spec/index-groups index-groups
                        "Invalid index groups are given.")]
   :post [(spec/validate ::spec/indices % "Invalid indices are returned.")]}
   ; Inserts row captions (section headers) into the index list based on ruby
   ; readings.
   ; For each index item, determines the appropriate caption using
   ; ruby->caption, and inserts a caption entry whenever the category changes.
  (loop [result []
         current-caption nil
         rest-items indices]
    (if (empty? rest-items)
      result
      (let [{:keys [ruby] :as item} (first rest-items)
            next-caption (ruby->caption ruby index-groups)
            add-header? (not= current-caption next-caption)]
        (recur (cond-> result
                 add-header? (conj {:type :caption :text next-caption})
                 true        (conj item))
               next-caption
               (rest rest-items))))))

(defn create-indices
  [documents]
  {:pre [(spec/validate ::spec/documents documents
                        "Invalid documents are given.")]
   :post [(spec/validate ::spec/indices % "Invalid indices are returned.")]}
  (let [entries (for [{:keys [name ast]} documents
                           index (ast/extract-indices ast)]
                       (let [base-name (id/extract-base-name name)]
                         (build-index-entry base-name index)))
        ruby-cache (reduce (fn [cache {:keys [ruby]}]
                             (assoc cache ruby
                                    {:normalized (normalize-ruby ruby)
                                     :is-english (english-ruby? ruby)}))
                           {}
                           entries)]
    (->> entries
         (group-by :text)
         (map (fn [[text entries]]
                (let [{:keys [ruby]} (first entries)]
                  {:type :item
                   :text text
                   :ruby ruby
                   :urls (->> entries
                              (sort-by :order)
                              (mapv :url))})))
         (sort-by (fn [{:keys [ruby]}]
                    (let [cache-entry (get ruby-cache ruby)]
                      (if (:is-english cache-entry)
                        ["" ruby]
                        [(:normalized cache-entry) ruby]))))
         vec
         insert-row-captions)))
