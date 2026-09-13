(ns clono.index.reading-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.index.reading :as reading]))

(defn- exception-data [operation]
  (try
    (operation)
    nil
    (catch :default error
      (ex-data error))))

(deftest reading-normalization-test
  (testing "When Japanese readings are normalized, then width and script differences are removed while semantic distinctions remain"
    (is (= "ばっくなんばー"
           (reading/normalize "バックナンバー")))
    (is (= "がぞう"
           (reading/normalize "ガゾウ")))
    (is (= "ぱす"
           (reading/normalize "ﾊﾟｽ")))
    (is (= "ぁっゃー"
           (reading/normalize "ァッャー"))))

  (testing "When extended katakana readings are normalized, then decomposed hiragana voicing is preserved"
    (is (= ["わ\u3099" "ゐ\u3099" "ゑ\u3099" "を\u3099"]
           (mapv reading/normalize ["ヷ" "ヸ" "ヹ" "ヺ"])))
    (is (= "わ\u3099"
           (reading/normalize "ﾜﾞ"))))

  (testing "When alphanumeric readings are normalized, then width and ASCII letter case differences are removed"
    (is (= "android 14"
           (reading/normalize "Ａｎｄｒｏｉｄ １４")))
    (is (= "c++"
           (reading/normalize "Ｃ＋＋")))
    (is (= "node.js@example_com#1"
           (reading/normalize "Node.js@Example_Com#1")))))

(deftest reading-validation-test
  (testing "When a reading is not a string, then normalization rejects its type"
    (let [data (exception-data #(reading/normalize nil))]
      (is (= :reading-not-string (:code data)))
      (is (nil? (:reading data)))))

  (testing "When a reading is empty, then normalization rejects the missing value"
    (is (= :reading-empty
           (:code (exception-data #(reading/normalize "　"))))))

  (testing "When a reading has surrounding whitespace, then normalization rejects the unintentional spacing"
    (is (= :reading-surrounding-whitespace
           (:code (exception-data #(reading/normalize " Android")))))
    (is (= :reading-surrounding-whitespace
           (:code (exception-data #(reading/normalize "あんどろいど　"))))))

  (testing "When a reading contains unsupported characters, then normalization rejects the ambiguous value"
    (doseq [value ["索引"
                   "androidあぷり"
                   ".gitignore"
                   "a:b"
                   "\u3099"
                   "わ\u3099\u3099"]]
      (let [data (exception-data #(reading/normalize value))]
        (is (= :reading-unsupported-characters (:code data)) value)
        (is (= value (:reading data)) value)))))
