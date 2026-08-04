(ns clono.main
  (:require [clojure.string :as string]))

(def ^:private package-json
  (js/require "../package.json"))

(def ^:private help-text
  (str "Usage: clono [options]\n"
       "\n"
       "Options:\n"
       "  --help     Show help\n"
       "  --version  Show version"))

(defn- print-help []
  (println help-text))

(defn- print-version []
  (println (str "clono " (aget package-json "version"))))

(defn- print-unknown-arguments [arguments]
  (.error js/console (str "Unknown argument(s): " (string/join " " arguments)))
  (.error js/console "Run 'clono --help' for usage.")
  (set! (.-exitCode js/process) 1))

(defn main []
  (let [arguments (vec (.slice js/process.argv 2))]
    (cond
      (empty? arguments) (print-help)
      (= ["--help"] arguments) (print-help)
      (= ["--version"] arguments) (print-version)
      :else (print-unknown-arguments arguments))))
