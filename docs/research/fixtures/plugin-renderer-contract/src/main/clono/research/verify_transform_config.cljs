(ns clono.research.verify-transform-config
  (:require
   ["node:path" :as path]
   [clono.research.transform-config :as transform-config]))

(defn- assert! [condition message]
  (when-not condition
    (throw (js/Error. message))))

(defn main []
  (let [fixture-root (.resolve path js/__dirname "..")
        invocation-root (.join path fixture-root "scripts")
        project-root (.join path fixture-root "project with space#hash")
        project-argument (.relative path invocation-root project-root)
        input (js/Object.freeze
               #js {:title "設定 & renderer"
                    :body "本文には**強調**がある。"})
        context {:line 3 :column 1}]
    (-> (transform-config/transform-column
         ["chapter.md"
          "--output" "preview.md"
          "--project" project-argument]
         invocation-root
         input
         context)
        (.then
         (fn [configured-result]
           (assert! (:ok? configured-result)
                    "An explicitly selected project configuration could not be loaded")
           (assert! (.includes (:output configured-result)
                               "clono-column project-column")
                    "The project renderer was not applied to transform output")
           (assert! (.includes (:output configured-result)
                               "設定 &amp; renderer")
                    "The project renderer did not encode the title")
           (transform-config/transform-column
            ["chapter.md" "--output" "preview.md"]
            (.join path project-root "manuscripts")
            input
            context)))
        (.then
         (fn [default-result]
           (assert! (:ok? default-result)
                    "Transform without a project could not use the default renderer")
           (assert! (not (.includes (:output default-result) "project-column"))
                    "Transform searched ancestor directories for a project configuration")
           (assert! (.includes (:output default-result)
                               "<aside class=\"clono-column\">")
                    "Transform without a project did not use the default renderer"))))))
