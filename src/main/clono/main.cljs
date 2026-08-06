(ns clono.main
  (:require [clono.cli :as cli]))

(def ^:private package-json
  (js/require "../package.json"))

(defn- print-result [{:keys [stdout stderr exit-code]}]
  (when stdout
    (println stdout))
  (when stderr
    (.error js/console stderr))
  (set! (.-exitCode js/process) exit-code))

(defn main []
  (let [arguments (vec (.slice js/process.argv 2))
        version (aget package-json "version")]
    (print-result (cli/evaluate-arguments arguments version))))
