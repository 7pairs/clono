(ns clono.book.config-integration
  (:require
   ["node:fs" :as fs]
   ["node:os" :as os]
   ["node:path" :as path]
   [clono.book.config :as config]
   [clono.plugin :as plugin]))

(defn- write-file! [file-path content]
  (.mkdirSync fs (.dirname path file-path) #js {:recursive true})
  (.writeFileSync fs file-path content "utf8"))

(defn- fail! [error]
  (.write (.-stderr js/process)
          (str "Release config integration failed: "
               (or (.-message error) (str error))
               "\n"))
  (set! (.-exitCode js/process) 1))

(defn- expected-config? [result project plugin-specifiers]
  (let [loaded-config (:config result)]
    (and (:ok? result)
         (empty? (:diagnostics result))
         (= "manuscripts" (:source-root loaded-config))
         (= "build/manuscripts" (:output-root loaded-config))
         (= (.join path project "manuscripts") (:source-path loaded-config))
         (= (mapv (fn [specifier]
                    (let [plugin-path (subs specifier 2)]
                      {:specifier specifier
                       :path plugin-path
                       :file-path (.join path project plugin-path)}))
                  plugin-specifiers)
            (:plugins loaded-config))
         (= [{:type :document
              :path "chapter.md"
              :kind "chapter"
              :include-in-toc true}]
            (mapv #(dissoc % :file-path) (:publication loaded-config))))))

(defn- expected-conflict? [result]
  (and (false? (:ok? result))
       (nil? (:registry result))
       (= 1 (count (:diagnostics result)))
       (.includes (:message (first (:diagnostics result)))
                  "renderer名`column`が競合しています")))

(defn- expected-registration? [result]
  (let [registration (get (:registry result) "column")]
    (and (:ok? result)
         (empty? (:diagnostics result))
         (= #{"column"} (set (keys (:registry result))))
         (= "success" (aget (:definition (:plugin registration)) "name"))
         (= "rendered by success"
            ((:renderer registration) #js {:title "Title"
                                            :body "Body"})))))

(defn- config-source [plugin-specifiers]
  (str "const sourceRoot = await Promise.resolve('manuscripts');\n"
       "export default {\n"
       "  sourceRoot,\n"
       "  outputRoot: 'build/manuscripts',\n"
       "  publication: [\n"
       "    { type: 'document', path: 'chapter.md', kind: 'chapter', includeInToc: true },\n"
       "  ],\n"
       "  plugins: [\n"
       (apply str (map #(str "    '" % "',\n") plugin-specifiers))
       "  ],\n"
       "};\n"))

(defn- prepare-project! [project plugin-specifiers]
  (write-file! (.join path project "manuscripts" "chapter.md")
               "# Release integration\n")
  (write-file! (.join path project "clono.config.mjs")
               (config-source plugin-specifiers)))

(defn- load-registry! [project plugin-specifiers]
  (-> (config/load-project-config project)
      (.then (fn [result]
               (when-not (expected-config? result project plugin-specifiers)
                 (throw (js/Error.
                         (str "Unexpected config result: " (pr-str result)))))
               (plugin/load-registry
                (.join path project "clono.config.mjs")
                (:config result))))))

(defn main []
  (let [conflict-project (.mkdtempSync fs (.join path (.tmpdir os)
                                                 "clono-config-conflict-"))
        success-project (.mkdtempSync fs (.join path (.tmpdir os)
                                                "clono-config-success-"))
        conflict-plugins ["./plugins/first plugin#renderer.mjs"
                          "./plugins/second.mjs"]
        success-plugins ["./plugins/success.mjs"]]
    (try
      (prepare-project! conflict-project conflict-plugins)
      (prepare-project! success-project success-plugins)
      (aset js/globalThis "__clonoPluginLoadTrace" #js [])
      (write-file!
       (.join path conflict-project "plugins" "first plugin#renderer.mjs")
       (str "await new Promise((resolve) => setTimeout(resolve, 20));\n"
            "globalThis.__clonoPluginLoadTrace.push('first');\n"
            "export default {\n"
            "  name: 'first', version: '1.0.0', apiVersion: 1,\n"
            "  renderers: { column() { return 'first'; } },\n"
            "};\n"))
      (write-file!
       (.join path conflict-project "plugins" "second.mjs")
       (str "globalThis.__clonoPluginLoadTrace.push('second');\n"
            "export default {\n"
            "  name: 'second', version: '1.0.0', apiVersion: 1,\n"
            "  renderers: { column() { return 'second'; } },\n"
            "};\n"))
      (write-file!
       (.join path success-project "plugins" "success.mjs")
       (str "export default {\n"
            "  name: 'success', version: '1.0.0', apiVersion: 1,\n"
            "  renderers: { column() { return 'rendered by success'; } },\n"
            "};\n"))
      (-> (load-registry! conflict-project conflict-plugins)
          (.then (fn [result]
                   (when-not (and (expected-conflict? result)
                                  (= ["first" "second"]
                                     (vec (array-seq
                                           (aget js/globalThis
                                                 "__clonoPluginLoadTrace")))))
                     (throw (js/Error.
                             (str "Unexpected conflict result: "
                                  (pr-str result)))))
                   (load-registry! success-project success-plugins)))
          (.then (fn [result]
                   (when-not (expected-registration? result)
                     (throw (js/Error.
                             (str "Unexpected registration result: "
                                  (pr-str result)))))))
          (.catch fail!)
          (.finally (fn []
                      (js-delete js/globalThis "__clonoPluginLoadTrace")
                      (.rmSync fs conflict-project #js {:recursive true
                                                        :force true})
                      (.rmSync fs success-project #js {:recursive true
                                                       :force true}))))
      (catch :default error
        (js-delete js/globalThis "__clonoPluginLoadTrace")
        (.rmSync fs conflict-project #js {:recursive true :force true})
        (.rmSync fs success-project #js {:recursive true :force true})
        (fail! error)))))
