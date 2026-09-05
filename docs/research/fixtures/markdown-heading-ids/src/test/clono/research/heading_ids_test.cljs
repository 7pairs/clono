(ns clono.research.heading-ids-test
  (:require
   ["node:fs" :as fs]
   [cljs.test :refer [deftest is testing]]
   [clono.research.heading-ids :as heading-ids]
   [goog.object :as gobj]))

(defn read-input []
  (.readFileSync fs "input/headings.md" "utf8"))

(defn property [object & names]
  (reduce gobj/get object names))

(defn child-types [node]
  (mapv #(.-type %) (heading-ids/children node)))

(defn candidates [tree]
  (mapv heading-ids/explicit-id-candidate (heading-ids/headings tree)))

(deftest heading-id-mdast-test
  (let [tree (heading-ids/parse (read-input))
        headings (vec (heading-ids/headings tree))
        chapter (nth headings 0)
        section (nth headings 1)
        subsection (nth headings 2)
        deep-heading (nth headings 3)]
    (testing "When VFM heading IDs are parsed, then heading depth and trailing ID text remain inspectable"
      (is (= [1 2 3 4 2 2 2 2]
             (mapv #(.-depth %) headings)))
      (is (= ["text"] (child-types chapter)))
      (is (= "はじめに {#chapter-introduction}"
             (.-value (first (heading-ids/children chapter)))))
      (is (= 1 (property chapter "position" "start" "line")))
      (is (= "deep-heading"
             (:value (heading-ids/explicit-id-candidate deep-heading)))))

    (testing "When inline Markdown precedes a VFM heading ID, then inline nodes and the trailing ID text remain separate"
      (is (= ["inlineCode" "text" "strong" "text"]
             (child-types section)))
      (is (= " {#section-structure}"
             (.-value (last (heading-ids/children section)))))
      (is (= 3 (property section "position" "start" "line"))))

    (testing "When a heading has no VFM ID, then no explicit ID candidate is exposed"
      (is (nil? (heading-ids/explicit-id-candidate subsection))))))

(deftest heading-id-candidate-test
  (let [tree (heading-ids/parse (read-input))
        actual (candidates tree)]
    (testing "When valid and invalid VFM ID suffixes are inspected, then candidates remain distinguishable before serialization"
      (is (= ["chapter-introduction"
              "section-structure"
              nil
              "deep-heading"
              "Invalid_ID"
              "duplicate"
              "duplicate"
              nil]
             (mapv :value actual)))
      (is (= [true true nil true false true true nil]
             (mapv :valid? actual))))

    (testing "When duplicate explicit IDs are inspected, then both source headings remain available for validation"
      (is (= 2 (count (filter #(= "duplicate" (:value %)) actual)))))

    (testing "When ordinary braced text ends a heading, then it is not treated as a VFM ID candidate"
      (is (nil? (last actual))))))

(deftest heading-id-round-trip-test
  (let [tree (heading-ids/parse (read-input))
        serialized (heading-ids/serialize tree)
        reparsed (heading-ids/parse serialized)
        reparsed-headings (vec (heading-ids/headings reparsed))]
    (testing "When valid VFM heading IDs are serialized and reparsed, then their IDs and inline Markdown remain inspectable"
      (is (= "chapter-introduction"
             (:value (heading-ids/explicit-id-candidate
                      (nth reparsed-headings 0)))))
      (is (= "section-structure"
             (:value (heading-ids/explicit-id-candidate
                      (nth reparsed-headings 1)))))
      (is (= ["inlineCode" "text" "strong" "text"]
             (child-types (nth reparsed-headings 1)))))

    (testing "When an invalid VFM ID candidate is serialized and reparsed, then escaping changes the Markdown while the invalid candidate remains inspectable"
      (is (.includes serialized "{#Invalid\\_ID}"))
      (is (= {:value "Invalid_ID"
              :valid? false
              :suffix " {#Invalid_ID}"}
             (heading-ids/explicit-id-candidate
              (nth reparsed-headings 4)))))))
