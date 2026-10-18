;; Copyright 2026 HASEBA Junya (7pairs)
;; SPDX-License-Identifier: Apache-2.0

(ns clono.cli-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clono.cli :as cli]
            [clono.version :as version]))

;; Temporary test-runner connection check, replaced by CLI unit tests in step 3.
(deftest bootstrap-connection
  (testing "When the connection probe receives a value, then the same value is returned"
    (is (= "CLONO_BOOTSTRAP" (cli/probe "CLONO_BOOTSTRAP"))))
  (testing "When version information is loaded, then the version is a string"
    (is (string? version/value))))
