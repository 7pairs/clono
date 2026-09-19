(ns clono.research.plugin-loading
  (:require
   ["node:path" :as path]
   ["node:url" :refer [pathToFileURL]]
   [goog.object :as gobj]))

;; Closure cannot transpile a dynamic import expression in a node-script release.
(def ^:private dynamic-import
  (js/Function. "specifier" "return import(specifier);"))

(defn- import-file [file-path]
  (dynamic-import (.-href (pathToFileURL file-path))))

(defn candidate-plugin [entry]
  (gobj/get (:module entry) "default"))

(defn candidate-plugin-info [entry]
  (let [plugin (candidate-plugin entry)]
    {:name (gobj/get plugin "name")
     :version (gobj/get plugin "version")
     :api-version (gobj/get plugin "apiVersion")}))

(defn candidate-renderer [entry renderer-name]
  (gobj/get (gobj/get (candidate-plugin entry) "renderers") renderer-name))

(defn invoke-candidate-renderer [entry renderer-name input]
  ((candidate-renderer entry renderer-name) input))

(defn load-candidate-plugins [config-path]
  (let [resolved-config-path (.resolve path config-path)]
    (-> (import-file resolved-config-path)
        (.then
         (fn [config-module]
           (let [config (gobj/get config-module "default")
                 specifiers (array-seq (gobj/get config "plugins"))
                 config-directory (.dirname path resolved-config-path)
                 entries (mapv (fn [specifier]
                                 {:specifier specifier
                                  :file-path (.resolve path config-directory specifier)})
                               specifiers)
                 imports (to-array (map #(import-file (:file-path %)) entries))]
             (-> (js/Promise.all imports)
                 (.then
                  (fn [modules]
                    {:config-path resolved-config-path
                     :plugins
                     (mapv (fn [entry module]
                             (assoc entry :module module))
                           entries
                           (array-seq modules))})))))))))
