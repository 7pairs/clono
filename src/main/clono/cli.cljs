(ns clono.cli)

(def usage "Usage: clono")

(defn- print-usage []
  (println usage))

(defn main [& arguments]
  (if (or (empty? arguments)
          (#{"-h" "--help"} (first arguments)))
    (print-usage)
    (do
      (js/console.error usage)
      (set! (.-exitCode js/process) 1))))
