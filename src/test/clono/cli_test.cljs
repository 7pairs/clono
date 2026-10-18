;; Copyright 2026 HASEBA Junya (7pairs)
;; SPDX-License-Identifier: Apache-2.0

(ns clono.cli-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clono.cli :as cli]))

(deftest version-result-test
  (testing "When only --version is supplied, then the supplied version is returned successfully"
    (is (= {:stdout "9.8.7\n" :stderr "" :exit-code 0}
           (cli/evaluate ["--version"] "9.8.7")))))

(deftest usage-result-test
  (testing "When no arguments are supplied, then usage is returned successfully"
    (is (= {:stdout "Usage: clono --version\n" :stderr "" :exit-code 0}
           (cli/evaluate [] "9.8.7")))))

;; C03-C05, C12
(deftest rejected-arguments-test
  (doseq [[condition args json]
          [["an unknown option is supplied" ["--invalid"] "[\"--invalid\"]"]
           ["--help is supplied" ["--help"] "[\"--help\"]"]
           ["-v is supplied" ["-v"] "[\"-v\"]"]
           ["-- is supplied" ["--"] "[\"--\"]"]
           ["a positional argument is supplied" ["chapter.md"] "[\"chapter.md\"]"]
           ["an empty argument is supplied" [""] "[\"\"]"]
           ["an unknown option follows --version" ["--version" "--invalid"] "[\"--version\",\"--invalid\"]"]
           ["--version follows an unknown option" ["--invalid" "--version"] "[\"--invalid\",\"--version\"]"]
           ["--version is repeated" ["--version" "--version"] "[\"--version\",\"--version\"]"]
           ["an empty argument follows --version" ["--version" ""] "[\"--version\",\"\"]"]
           ["an argument contains a space" ["two words"] "[\"two words\"]"]
           ["an argument contains a quotation mark" ["a\"b"] "[\"a\\\"b\"]"]
           ["an argument contains a newline" ["a\nb"] "[\"a\\nb\"]"]
           ["an argument contains a backslash" ["a\\b"] "[\"a\\\\b\"]"]]]
    (testing (str "When " condition ", then the ordered arguments are returned as a JSON error")
      (is (= {:stdout ""
              :stderr (str "Error: unsupported arguments: " json "\nUsage: clono --version\n")
              :exit-code 1}
             (cli/evaluate args "9.8.7"))))))
