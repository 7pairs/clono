(ns clono.research.index-normalization-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.research.index-normalization :as index-normalization]))

(defn exception-data [operation]
  (try
    (operation)
    nil
    (catch :default error
      (ex-data error))))

(defn entry-terms [group]
  (mapv :term (:entries group)))

(deftest reading-normalization-test
  (testing "When Japanese readings are normalized, then width, script, voicing, small kana, and prolonged marks produce deterministic keys"
    (is (= "ばっくなんばー"
           (index-normalization/normalize-reading "バックナンバー")))
    (is (= "はつくなんはあ"
           (index-normalization/reading-sort-key "ばっくなんばー")))
    (is (= "こんひゆうたあ"
           (index-normalization/reading-sort-key "コンピューター")))
    (is (= "かそう"
           (index-normalization/reading-sort-key "ガゾウ"))))

  (testing "When extended katakana readings are normalized, then their decomposed hiragana voicing remains available before sort-key folding"
    (is (= ["わ\u3099" "ゐ\u3099" "ゑ\u3099" "を\u3099"]
           (mapv index-normalization/normalize-reading
                 ["ヷ" "ヸ" "ヹ" "ヺ"])))
    (is (= ["わ" "ゐ" "ゑ" "を"]
           (mapv index-normalization/reading-sort-key
                 ["ヷ" "ヸ" "ヹ" "ヺ"])))
    (is (= "わ\u3099"
           (index-normalization/normalize-reading "ﾜﾞ")))
    (is (= "わあ"
           (index-normalization/reading-sort-key "ヷー")))
    (is (= [:wa :wa :wa :wa]
           (mapv index-normalization/reading-group
                 ["ヷ" "ヸ" "ヹ" "ヺ"]))))

  (testing "When alphanumeric readings are normalized, then full-width characters and ASCII case produce one key"
    (is (= "android 14"
           (index-normalization/normalize-reading "Ａｎｄｒｏｉｄ １４")))
    (is (= "c++"
           (index-normalization/reading-sort-key "Ｃ＋＋")))
    (is (= "node.js"
           (index-normalization/reading-sort-key "Node.js")))))

(deftest reading-validation-test
  (testing "When a reading is empty or uses unsupported characters, then normalization rejects it with a specific diagnostic"
    (is (= :reading-empty
           (:code (exception-data #(index-normalization/normalize-reading "　")))))
    (is (= :reading-unsupported-characters
           (:code (exception-data #(index-normalization/normalize-reading "索引")))))
    (is (= :reading-unsupported-characters
           (:code (exception-data #(index-normalization/normalize-reading "androidあぷり")))))
    (is (= :reading-unsupported-characters
           (:code (exception-data #(index-normalization/normalize-reading ".gitignore")))))
    (is (= :reading-unsupported-characters
           (:code (exception-data #(index-normalization/normalize-reading "\u3099")))))
    (is (= :reading-unsupported-characters
           (:code (exception-data #(index-normalization/normalize-reading
                                    "わ\u3099\u3099"))))))

  (testing "When a prolonged mark has no preceding vowel, then sort-key generation rejects the reading"
    (is (= :reading-invalid-prolonged-mark
           (:code (exception-data #(index-normalization/reading-sort-key "ーど")))))
    (is (= :reading-invalid-prolonged-mark
           (:code (exception-data #(index-normalization/reading-sort-key "んー")))))))

(deftest grouping-test
  (testing "When representative readings are classified, then folded initials select the fixed initial groups"
    (is (= [:alphanumeric :a :ka :sa :ta :na :ha :ma :ya :ra :wa]
           (mapv index-normalization/reading-group
                 ["API" "ぁさ" "がぞう" "ざっし" "だい" "なみ"
                  "ぱす" "みほん" "ゃく" "りすと" "んど"])))))

(deftest deterministic-index-order-test
  (let [result (index-normalization/build-index
                [{:term "箸" :reading "はし" :occurrence "p8"}
                 {:term "API" :reading "ＡＰＩ" :occurrence "p2"}
                 {:term "五木" :reading "いつき" :occurrence "p5"}
                 {:term "画像" :reading "がぞう" :occurrence "p6"}
                 {:term "Android" :reading "Android" :occurrence "p1"}
                 {:term "橋" :reading "はし" :occurrence "p7"}
                 {:term "一気" :reading "いっき" :occurrence "p4"}
                 {:term "コラム" :reading "こらむ" :occurrence "p3"}])]
    (testing "When entries are grouped and sorted, then empty groups are omitted and the fixed group order is preserved"
      (is (= [:alphanumeric :a :ka :ha]
             (mapv :id (:groups result))))
      (is (= ["英数字" "あ行" "か行" "は行"]
             (mapv :title (:groups result)))))

    (testing "When normalized sort keys collide, then normalized reading and display term provide deterministic tie breakers"
      (is (= ["Android" "API"]
             (entry-terms (nth (:groups result) 0))))
      (is (= ["一気" "五木"]
             (entry-terms (nth (:groups result) 1))))
      (is (= ["画像" "コラム"]
             (entry-terms (nth (:groups result) 2))))
      (is (= ["橋" "箸"]
             (entry-terms (nth (:groups result) 3)))))))

(deftest duplicate-term-test
  (testing "When the same term has equivalent readings, then its occurrences are merged in source order"
    (let [result (index-normalization/build-index
                  [{:term "Android" :reading "ＡＮＤＲＯＩＤ" :occurrence "chapter-one#1"}
                   {:term "Android" :reading "android" :occurrence "chapter-two#3"}])
          entry (first (:entries result))]
      (is (= "android" (:normalized-reading entry)))
      (is (= ["chapter-one#1" "chapter-two#3"]
             (:occurrences entry)))))

  (testing "When the same term has different normalized readings, then index construction rejects the conflict"
    (let [data (exception-data
                #(index-normalization/build-index
                  [{:term "橋" :reading "はし" :occurrence "p1"}
                   {:term "橋" :reading "ばし" :occurrence "p2"}]))]
      (is (= :term-reading-conflict (:code data)))
      (is (= [{:term "橋" :readings ["はし" "ばし"]}]
             (:conflicts data)))))

  (testing "When different terms share one reading, then both terms remain in deterministic display-term order"
    (let [result (index-normalization/build-index
                  [{:term "箸" :reading "はし" :occurrence "p2"}
                   {:term "橋" :reading "はし" :occurrence "p1"}])]
      (is (= ["橋" "箸"]
             (mapv :term (:entries result)))))))
