(ns clono.cli)

(def usage "Usage: clono")

(defn command-result [arguments]
  (let [show-help? (or (empty? arguments)
                       (#{"-h" "--help"} (first arguments)))]
    {:exit-code (if show-help? 0 1)
     :output usage
     :stream (if show-help? :stdout :stderr)}))

(defn- write-result! [{:keys [exit-code output stream]}]
  (case stream
    :stdout (println output)
    :stderr (js/console.error output))
  (set! (.-exitCode js/process) exit-code))

(defn main [& arguments]
  (write-result! (command-result arguments)))
