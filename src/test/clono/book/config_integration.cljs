(ns clono.book.config-integration
  (:require
   ["node:fs" :as fs]
   ["node:os" :as os]
   ["node:path" :as path]
   [clono.book.config :as config]
   [clono.plugin.loader :as plugin-loader]
   [clono.plugin.registry :as plugin-registry]))

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
         (= [{:specifier "./plugins/first plugin#renderer.mjs"
              :path "plugins/first plugin#renderer.mjs"
              :file-path (.join path project "plugins" "first plugin#renderer.mjs")}
             {:specifier "./plugins/second.mjs"
              :path "plugins/second.mjs"
              :file-path (.join path project "plugins" "second.mjs")}]
            (:plugins loaded-config))
         (= [{:type :document
              :path "chapter.md"
              :kind "chapter"
              :include-in-toc true}]
            (mapv #(dissoc % :file-path) (:publication loaded-config))))))

(defn- expected-plugins? [result]
  (and (:ok? result)
       (empty? (:diagnostics result))
       (= 2 (count (:plugins result)))
       (= ["first" "second"]
          (vec (array-seq (aget js/globalThis "__clonoPluginLoadTrace"))))))

(defn- expected-registry? [result]
  (and (false? (:ok? result))
       (nil? (:registry result))
       (= 1 (count (:diagnostics result)))
       (.includes (:message (first (:diagnostics result)))
                  "renderer名`column`が競合しています")))

(defn main []
  (let [project (.mkdtempSync fs (.join path (.tmpdir os)
                                        "clono-config-integration-"))]
    (try
      (write-file! (.join path project "manuscripts" "chapter.md")
                   "# Release integration\n")
      (aset js/globalThis "__clonoPluginLoadTrace" #js [])
      (write-file!
       (.join path project "plugins" "first plugin#renderer.mjs")
       (str "await new Promise((resolve) => setTimeout(resolve, 20));\n"
            "globalThis.__clonoPluginLoadTrace.push('first');\n"
            "export default {\n"
            "  name: 'first', version: '1.0.0', apiVersion: 1,\n"
            "  renderers: { column() { return 'first'; } },\n"
            "};\n"))
      (write-file!
       (.join path project "plugins" "second.mjs")
       (str "globalThis.__clonoPluginLoadTrace.push('second');\n"
            "export default {\n"
            "  name: 'second', version: '1.0.0', apiVersion: 1,\n"
            "  renderers: { column() { return 'second'; } },\n"
            "};\n"))
      (write-file!
       (.join path project "clono.config.mjs")
       (str "const sourceRoot = await Promise.resolve('manuscripts');\n"
            "export default {\n"
            "  sourceRoot,\n"
            "  outputRoot: 'build/manuscripts',\n"
            "  publication: [\n"
            "    { type: 'document', path: 'chapter.md', kind: 'chapter', includeInToc: true },\n"
            "  ],\n"
            "  plugins: [\n"
            "    './plugins/first plugin#renderer.mjs',\n"
            "    './plugins/second.mjs',\n"
            "  ],\n"
            "};\n"))
      (-> (config/load-project-config project)
          (.then (fn [result]
                   (when-not (expected-config? result project)
                     (throw (js/Error.
                             (str "Unexpected config result: " (pr-str result)))))
                   (plugin-loader/load (:config result))))
          (.then (fn [result]
                   (when-not (expected-plugins? result)
                     (throw (js/Error.
                             (str "Unexpected plugin result: " (pr-str result)))))
                   (plugin-registry/build
                    (.join path project "clono.config.mjs")
                    (:plugins result))))
          (.then (fn [result]
                   (when-not (expected-registry? result)
                     (throw (js/Error.
                             (str "Unexpected registry result: "
                                  (pr-str result)))))))
          (.catch fail!)
          (.finally (fn []
                      (js-delete js/globalThis "__clonoPluginLoadTrace")
                      (.rmSync fs project #js {:recursive true
                                               :force true}))))
      (catch :default error
        (js-delete js/globalThis "__clonoPluginLoadTrace")
        (.rmSync fs project #js {:recursive true :force true})
        (fail! error)))))
