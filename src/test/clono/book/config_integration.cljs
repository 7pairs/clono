(ns clono.book.config-integration
  (:require
   ["node:fs" :as fs]
   ["node:os" :as os]
   ["node:path" :as path]
   [clono.book.config :as config]))

(defn- write-file! [file-path content]
  (.mkdirSync fs (.dirname path file-path) #js {:recursive true})
  (.writeFileSync fs file-path content "utf8"))

(defn- fail! [error]
  (.write (.-stderr js/process)
          (str "Release config integration failed: "
               (or (.-message error) (str error))
               "\n"))
  (set! (.-exitCode js/process) 1))

(defn- expected-config? [result project]
  (let [loaded-config (:config result)]
    (and (:ok? result)
         (empty? (:diagnostics result))
         (= "manuscripts" (:source-root loaded-config))
         (= "build/manuscripts" (:output-root loaded-config))
         (= (.join path project "manuscripts") (:source-path loaded-config))
         (= [{:path "chapter.md"
              :kind "chapter"
              :include-in-toc true}]
            (mapv #(dissoc % :file-path) (:publication loaded-config))))))

(defn main []
  (let [project (.mkdtempSync fs (.join path (.tmpdir os)
                                        "clono-config-integration-"))]
    (try
      (write-file! (.join path project "manuscripts" "chapter.md")
                   "# Release integration\n")
      (write-file!
       (.join path project "clono.config.mjs")
       (str "const sourceRoot = await Promise.resolve('manuscripts');\n"
            "export default {\n"
            "  sourceRoot,\n"
            "  outputRoot: 'build/manuscripts',\n"
            "  publication: [\n"
            "    { path: 'chapter.md', kind: 'chapter', includeInToc: true },\n"
            "  ],\n"
            "};\n"))
      (-> (config/load-project-config project)
          (.then (fn [result]
                   (when-not (expected-config? result project)
                     (throw (js/Error.
                             (str "Unexpected load result: " (pr-str result)))))))
          (.catch fail!)
          (.finally (fn []
                      (.rmSync fs project #js {:recursive true
                                               :force true}))))
      (catch :default error
        (.rmSync fs project #js {:recursive true :force true})
        (fail! error)))))
