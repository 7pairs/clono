(ns clono.cli-test
  (:require [cljs.test :refer [deftest is testing]]
            [clono.cli :as cli]))

(def ^:private expected-help
  (str "Usage: clono [options]\n"
       "\n"
       "Options:\n"
       "  --help     Show help\n"
       "  --version  Show version"))

(deftest help-test
  (testing "shows help when no arguments are given"
    (is (= {:stdout expected-help
            :stderr nil
            :exit-code 0}
           (cli/evaluate-arguments [] "1.2.3"))))

  (testing "shows help for --help"
    (is (= {:stdout expected-help
            :stderr nil
            :exit-code 0}
           (cli/evaluate-arguments ["--help"] "1.2.3")))))

(deftest version-test
  (testing "shows the supplied version for --version"
    (is (= {:stdout "clono 1.2.3"
            :stderr nil
            :exit-code 0}
           (cli/evaluate-arguments ["--version"] "1.2.3")))))

(deftest unknown-arguments-test
  (testing "rejects one unknown argument"
    (is (= {:stdout nil
            :stderr (str "Unknown argument(s): --unknown\n"
                         "Run 'clono --help' for usage.")
            :exit-code 1}
           (cli/evaluate-arguments ["--unknown"] "1.2.3"))))

  (testing "rejects multiple unknown arguments"
    (is (= {:stdout nil
            :stderr (str "Unknown argument(s): input.md output.md\n"
                         "Run 'clono --help' for usage.")
            :exit-code 1}
           (cli/evaluate-arguments ["input.md" "output.md"] "1.2.3")))))
