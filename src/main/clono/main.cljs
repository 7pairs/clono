;; Copyright 2026 HASEBA Junya (7pairs)
;; SPDX-License-Identifier: Apache-2.0

(ns clono.main
  (:require [clono.cli :as cli]
            [clono.version :as version]))

(defn main []
  (let [args (vec (array-seq (.slice (.-argv js/process) 2)))
        {:keys [stdout stderr exit-code]} (cli/evaluate args version/value)]
    (.write (.-stdout js/process) stdout)
    (.write (.-stderr js/process) stderr)
    (set! (.-exitCode js/process) exit-code)))
