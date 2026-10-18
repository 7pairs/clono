;; Copyright 2026 HASEBA Junya (7pairs)
;; SPDX-License-Identifier: Apache-2.0

(ns clono.cli)

(def ^:private usage "Usage: clono --version\n")

(defn evaluate [args version]
  (cond
    (= ["--version"] args)
    {:stdout (str version "\n") :stderr "" :exit-code 0}

    (empty? args)
    {:stdout usage :stderr "" :exit-code 0}

    :else
    {:stdout ""
     :stderr (str "Error: unsupported arguments: "
                  (js/JSON.stringify (into-array args)) "\n" usage)
     :exit-code 1}))
