(ns clono.cli-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.cli :as cli]))

(def successful-help-result
  {:exit-code 0
   :output cli/usage
   :stream :stdout})

(deftest command-result-test
  (testing "When no arguments are given, then usage is written to standard output and the exit code is 0"
    (is (= successful-help-result
           (cli/command-result []))))

  (testing "When -h or --help is given, then usage is written to standard output and the exit code is 0"
    (doseq [option ["-h" "--help"]]
      (is (= successful-help-result
             (cli/command-result [option]))
          (str "Unexpected result for " option))))

  (testing "When an unknown argument is given, then usage is written to standard error and the exit code is 1"
    (is (= {:exit-code 1
            :output cli/usage
            :stream :stderr}
           (cli/command-result ["unknown"])))))
