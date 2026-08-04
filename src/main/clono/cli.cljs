(ns clono.cli
  (:require [clojure.string :as string]))

(def ^:private help-text
  (str "Usage: clono [options]\n"
       "\n"
       "Options:\n"
       "  --help     Show help\n"
       "  --version  Show version"))

(defn evaluate-arguments [arguments version]
  (cond
    (empty? arguments)
    {:stdout help-text
     :stderr nil
     :exit-code 0}

    (= ["--help"] arguments)
    {:stdout help-text
     :stderr nil
     :exit-code 0}

    (= ["--version"] arguments)
    {:stdout (str "clono " version)
     :stderr nil
     :exit-code 0}

    :else
    {:stdout nil
     :stderr (str "Unknown argument(s): " (string/join " " arguments) "\n"
                  "Run 'clono --help' for usage.")
     :exit-code 1}))
