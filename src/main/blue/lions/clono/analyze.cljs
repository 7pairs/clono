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
  (:require [cljs.spec.alpha :as s]
            [clojure.string :as str]
            [blue.lions.clono.ast :as ast]
            [blue.lions.clono.identifier :as id]
            [blue.lions.clono.spec :as spec]))

(defn create-toc-item
  [file-name node]
  {:pre [(s/valid? ::spec/file-name file-name)
         (s/valid? ::spec/node node)]
   :post [(s/valid? ::spec/toc-item %)]}
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
  {:pre [(s/valid? ::spec/documents documents)]
   :post [(s/valid? ::spec/toc-items %)]}
  (vec
   (mapcat (fn [{:keys [name ast]}]
             (map #(create-toc-item name %)
                  (ast/extract-headings ast)))
           documents)))

(defn has-valid-id-or-root-depth?
  [node]
  {:pre [(s/valid? ::spec/node node)]
   :post [(s/valid? ::spec/pred-result %)]}
  (or (-> (ast/extract-labels node)
          first
          :attributes
          :id
          some?)
      (= (:depth node) 1)))

(defn create-heading-info
  [file-name node]
  {:pre [(s/valid? ::spec/file-name file-name)
         (s/valid? ::spec/node node)]
   :post [(s/valid? ::spec/heading-or-nil %)]}
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
  {:pre [(s/valid? ::spec/documents documents)]
   :post [(s/valid? ::spec/heading-dic %)]}
  (->> (for [{:keys [name ast]} documents
             nodes (ast/extract-headings ast)
             :let [{:keys [id] :as heading-info}
                   (create-heading-info name nodes)]
             :when id]
         [id heading-info])
       (into {})))

(defn create-footnote-dic
  [documents]
  {:pre [(s/valid? ::spec/documents documents)]
   :post [(s/valid? ::spec/footnote-dic %)]}
  (->> (for [{:keys [name ast]} documents
             footnote (ast/extract-footnote-definition ast)
             :let [base-name (id/extract-base-name name)
                   id (:identifier footnote)
                   child (first (:children footnote))]
             :when child]
         [(id/build-dic-key base-name id) child])
       (into {})))

(defn build-index-entry
  [base-name node]
  {:pre [(s/valid? ::spec/file-name base-name)
         (s/valid? ::spec/node node)]
   :post [(s/valid? ::spec/index-entry %)]}
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
  {:pre [(s/valid? ::spec/ruby ruby)]
   :post [(s/valid? ::spec/pred-result %)]}
  (boolean (re-matches #"^[ -~].*" ruby)))

(def vowel-map
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

(def seion-map
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

(defn normalize-hiragana
  [ruby]
  {:pre [(s/valid? ::spec/ruby ruby)]
   :post [(s/valid? ::spec/ruby %)]}
  (let [chars (seq ruby)]
    (apply str
           (loop [result []
                  rest-chars chars]
             (if (empty? rest-chars)
               result
               (let [current (str (first rest-chars))
                     next-chars (rest rest-chars)]
                 (if (= current "ー")
                   (if-let [vowel (some vowel-map (take-last 1 result))]
                     (recur (conj result vowel) next-chars)
                     (recur (conj result current) next-chars))
                   (let [normalized (get seion-map current current)]
                     (recur (conj result normalized) next-chars)))))))))

(defn ruby->caption
  [ruby]
  {:pre [(s/valid? ::spec/ruby ruby)]
   :post [(s/valid? ::spec/caption %)]}
  (cond
    (english-ruby? ruby) "英数字"
    (#{"あ" "い" "う" "え" "お"} (subs (normalize-hiragana ruby) 0 1)) "あ行"
    (#{"か" "き" "く" "け" "こ"} (subs (normalize-hiragana ruby) 0 1)) "か行"
    (#{"さ" "し" "す" "せ" "そ"} (subs (normalize-hiragana ruby) 0 1)) "さ行"
    (#{"た" "ち" "つ" "て" "と"} (subs (normalize-hiragana ruby) 0 1)) "た行"
    (#{"な" "に" "ぬ" "ね" "の"} (subs (normalize-hiragana ruby) 0 1)) "な行"
    (#{"は" "ひ" "ふ" "へ" "ほ"} (subs (normalize-hiragana ruby) 0 1)) "は行"
    (#{"ま" "み" "む" "め" "も"} (subs (normalize-hiragana ruby) 0 1)) "ま行"
    (#{"や" "ゆ" "よ"} (subs (normalize-hiragana ruby) 0 1)) "や行"
    (#{"ら" "り" "る" "れ" "ろ"} (subs (normalize-hiragana ruby) 0 1)) "ら行"
    (#{"わ" "を" "ん"} (subs (normalize-hiragana ruby) 0 1)) "わ行"
    :else "その他"))

(defn insert-row-captions
  [indices]
  {:pre [(s/valid? ::spec/indices indices)]
   :post [(s/valid? ::spec/indices %)]}
  (loop [result []
         current-caption nil
         rest-items indices]
    (if (empty? rest-items)
      result
      (let [{:keys [ruby] :as item} (first rest-items)
            next-caption (ruby->caption ruby)
            add-header? (not= current-caption next-caption)]
        (recur (cond-> result
                 add-header? (conj {:type :caption :text next-caption})
                 true        (conj item))
               next-caption
               (rest rest-items))))))

(defn create-indices
  [documents]
  {:pre [(s/valid? ::spec/documents documents)]
   :post [(s/valid? ::spec/indices %)]}
  (->> (for [{:keys [name ast]} documents
             index (ast/extract-indices ast)]
         (let [base-name (id/extract-base-name name)]
           (build-index-entry base-name index)))
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
                  (if (english-ruby? ruby)
                    ["" ruby]
                    [(normalize-hiragana ruby) ruby])))
       vec
       insert-row-captions))
