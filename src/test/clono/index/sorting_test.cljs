(ns clono.index.sorting-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.index.sorting :as sorting]))

(defn- exception-data [operation]
  (try
    (operation)
    nil
    (catch :default error
      (ex-data error))))

(deftest sort-key-test
  (testing "When Japanese readings produce sort keys, then prolonged marks, voicing, and small kana are folded in order"
    (is (= "はつくなんはあ"
           (sorting/sort-key "ばっくなんばー")))
    (is (= "こんひゆうたあ"
           (sorting/sort-key "コンピューター")))
    (is (= "かそう"
           (sorting/sort-key "ガゾウ")))
    (is (= "わあ"
           (sorting/sort-key "ヷー"))))

  (testing "When alphanumeric readings produce sort keys, then only reading normalization is applied"
    (is (= "android 14"
           (sorting/sort-key "Ａｎｄｒｏｉｄ １４")))
    (is (= "c++"
           (sorting/sort-key "Ｃ＋＋"))))

  (testing "When a prolonged mark has no preceding kana with a known vowel, then sort-key generation rejects the reading"
    (doseq [value ["ーど" "ンー"]]
      (let [data (exception-data #(sorting/sort-key value))]
        (is (= :reading-invalid-prolonged-mark (:code data)) value)
        (is (= value (:reading data)) value)))))

(deftest fixed-group-test
  (testing "When fixed index groups are requested, then their identifiers and titles have the specified order"
    (is (= [{:id :alphanumeric :title "英数字"}
            {:id :a :title "あ行"}
            {:id :ka :title "か行"}
            {:id :sa :title "さ行"}
            {:id :ta :title "た行"}
            {:id :na :title "な行"}
            {:id :ha :title "は行"}
            {:id :ma :title "ま行"}
            {:id :ya :title "や行"}
            {:id :ra :title "ら行"}
            {:id :wa :title "わ行"}]
           sorting/group-definitions)))

  (testing "When representative readings are classified, then folded initials select the fixed index groups"
    (is (= [:alphanumeric :a :ka :sa :ta :na :ha :ma :ya :ra :wa]
           (mapv sorting/group-id
                 ["API" "ぁさ" "がぞう" "ざっし" "だい" "なみ"
                  "ぱす" "みほん" "ゃく" "りすと" "んど"]))))

  (testing "When extended katakana readings are classified, then their folded initials select the wa group"
    (is (= [:wa :wa :wa :wa]
           (mapv sorting/group-id ["ヷ" "ヸ" "ヹ" "ヺ"])))))
