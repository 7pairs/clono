(ns clono.research.plugin-loading-test
  (:require
   [cljs.test :refer [deftest is testing]]))

(deftest fixture-runtime-test
  (testing "When the fixture test suite runs, then it executes on Node.js"
    (is (= "node" (.-name (.-release js/process))))))
