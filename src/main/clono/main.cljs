;; Copyright 2026 HASEBA Junya (7pairs)
;; SPDX-License-Identifier: Apache-2.0

(ns clono.main
  (:require [clono.cli :as cli]
            [clono.version :as version]))

;; Temporary entry point for build/startup checks, not the product CLI.
(defn main []
  (cli/probe version/value))
