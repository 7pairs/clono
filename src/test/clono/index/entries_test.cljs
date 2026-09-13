(ns clono.index.entries-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.index.entries :as entries]))

(defn- index-entry [term normalized-reading sort-key group-id occurrence]
  {:term term
   :normalized-term (.normalize term "NFKC")
   :reading normalized-reading
   :normalized-reading normalized-reading
   :sort-key sort-key
   :group-id group-id
   :source-name occurrence})

(deftest merge-index-entries-test
  (testing "When one term has equivalent normalized readings, then its occurrences are merged in source order"
    (let [first-occurrence
          (assoc (index-entry "Android"
                              "android"
                              "android"
                              :alphanumeric
                              "chapter-one.md")
                 :reading "ＡＮＤＲＯＩＤ"
                 :marker-id "clono-index-marker-1")
          second-occurrence
          (assoc (index-entry "Android"
                              "android"
                              "android"
                              :alphanumeric
                              "chapter-two.md")
                 :marker-id "clono-index-marker-2")
          result (entries/merge-and-sort [first-occurrence second-occurrence])]
      (is (= 1 (count result)))
      (is (= "Android" (:term (first result))))
      (is (= "ＡＮＤＲＯＩＤ" (:reading (first result))))
      (is (= [first-occurrence second-occurrence]
             (:occurrences (first result))))))

  (testing "When one term has conflicting normalized readings, then entry integration rejects the conflict"
    (let [error
          (try
            (entries/merge-and-sort
             [(index-entry "橋" "はし" "はし" :ha "chapter-one.md")
              (index-entry "橋" "ばし" "はし" :ha "chapter-two.md")])
            nil
            (catch :default caught
              caught))]
      (is (= :index-entry-reading-conflict (:code (ex-data error))))
      (is (= "橋" (:term (ex-data error)))))))

(deftest deterministic-index-entry-order-test
  (testing "When index entries are merged and sorted, then fixed groups and Unicode tie breakers determine their order"
    (let [input
          [(index-entry "箸" "はし" "はし" :ha "chapter-eight.md")
           (index-entry "API" "api" "api" :alphanumeric "chapter-two.md")
           (index-entry "五木" "いつき" "いつき" :a "chapter-five.md")
           (index-entry "画像" "がぞう" "かそう" :ka "chapter-six.md")
           (index-entry "Android" "android" "android" :alphanumeric
                        "chapter-one.md")
           (index-entry "橋" "はし" "はし" :ha "chapter-seven.md")
           (index-entry "一気" "いっき" "いつき" :a "chapter-four.md")
           (index-entry "コラム" "こらむ" "こらむ" :ka "chapter-three.md")]
          result (entries/merge-and-sort input)]
      (is (= [:alphanumeric :alphanumeric :a :a :ka :ka :ha :ha]
             (mapv :group-id result)))
      (is (= ["Android" "API" "一気" "五木"
              "画像" "コラム" "橋" "箸"]
             (mapv :term result)))))

  (testing "When normalized display terms are equal, then original terms provide a deterministic order"
    (let [result
          (entries/merge-and-sort
           [(index-entry "Ａ" "えー" "ええ" :a "chapter-two.md")
            (index-entry "A" "えー" "ええ" :a "chapter-one.md")])]
      (is (= ["A" "Ａ"] (mapv :term result)))))

  (testing "When display terms contain supplementary characters, then Unicode code points determine their order"
    (let [result
          (entries/merge-and-sort
           [(index-entry "𐀀" "えー" "ええ" :a "chapter-two.md")
            (index-entry "\uE000" "えー" "ええ" :a "chapter-one.md")])]
      (is (= ["\uE000" "𐀀"] (mapv :term result))))))
