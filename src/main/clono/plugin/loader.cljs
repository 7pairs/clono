(ns clono.plugin.loader
  (:require
   ["node:url" :refer [pathToFileURL]]
   [clojure.string :as string]))

;; Closure cannot transpile a dynamic import expression in a node-script release.
(def ^:private dynamic-import
  (js/Function. "specifier" "return import(specifier);"))

(defn ^:no-doc import-plugin-module [specifier]
  (dynamic-import specifier))

(defn- error-message [error]
  (let [message (or (.-message error) (str error))]
    (string/replace message #"\s*[\r\n]+\s*" " ")))

(defn- load-error-result [index plugin error]
  {:ok? false
   :plugins []
   :diagnostics
   [{:file (:file-path plugin)
     :message (str "`plugins[" index "]`のプラグインを読み込みまたは評価できません: "
                   (:specifier plugin) ": "
                   (error-message error))}]})

(defn load [config]
  (let [importer import-plugin-module]
    (letfn [(load-next [index loaded remaining]
              (if-let [plugin (first remaining)]
                (try
                  (.then
                   (importer
                    (.-href (pathToFileURL (:file-path plugin))))
                   (fn [module]
                     (load-next (inc index)
                                (conj loaded (assoc plugin :module module))
                                (next remaining)))
                   (fn [error]
                     (load-error-result index plugin error)))
                  (catch :default error
                    (js/Promise.resolve
                     (load-error-result index plugin error))))
                (js/Promise.resolve
                 {:ok? true
                  :plugins loaded
                  :diagnostics []})))]
      (load-next 0 [] (:plugins config)))))
