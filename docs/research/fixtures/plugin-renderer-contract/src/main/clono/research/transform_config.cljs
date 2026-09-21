(ns clono.research.transform-config
  (:require
   ["node:path" :as path]
   ["node:url" :refer [pathToFileURL]]
   [clono.research.plugin-renderer-contract :as renderer-contract]
   [goog.object :as gobj]))

(def ^:private dynamic-import
  (js/Function. "specifier" "return import(specifier);"))

(defn parse-transform-arguments [arguments]
  (loop [remaining (seq arguments)
         input nil
         output nil
         project nil]
    (if-let [argument (first remaining)]
      (cond
        (#{"-o" "--output"} argument)
        (if-let [value (second remaining)]
          (if (or output (.startsWith value "-"))
            {:ok? false}
            (recur (nnext remaining) input value project))
          {:ok? false})

        (= "--project" argument)
        (if-let [value (second remaining)]
          (if (or project (.startsWith value "-"))
            {:ok? false}
            (recur (nnext remaining) input output value))
          {:ok? false})

        (.startsWith argument "-")
        {:ok? false}

        input
        {:ok? false}

        :else
        (recur (next remaining) argument output project))
      (if (and input output)
        {:ok? true
         :input input
         :output output
         :project project}
        {:ok? false}))))

(defn- import-file [file-path]
  (dynamic-import (.-href (pathToFileURL file-path))))

(defn- renderer-from-plugin [plugin renderer-name]
  (some-> (gobj/get plugin "renderers")
          (gobj/get renderer-name)))

(defn- load-project-renderer [invocation-root project renderer-name]
  (let [project-root (.resolve path invocation-root project)
        config-path (.join path project-root "clono.config.mjs")
        config-directory (.dirname path config-path)]
    (-> (import-file config-path)
        (.then
         (fn [config-namespace]
           (let [config (gobj/get config-namespace "default")
                 plugin-specifiers (array-seq (gobj/get config "plugins"))]
             (js/Promise.all
              (clj->js
               (mapv #(import-file (.resolve path config-directory %))
                     plugin-specifiers))))))
        (.then
         (fn [plugin-namespaces]
           (or (some #(renderer-from-plugin
                       (gobj/get % "default")
                       renderer-name)
                     (array-seq plugin-namespaces))
               (throw (js/Error.
                       (str "rendererが見つかりません: " renderer-name)))))))))

(defn select-column-renderer [invocation-root project]
  (if project
    (load-project-renderer invocation-root project "column")
    (js/Promise.resolve renderer-contract/default-column-renderer)))

(defn transform-column [arguments invocation-root input context]
  (let [request (parse-transform-arguments arguments)]
    (if-not (:ok? request)
      (js/Promise.resolve
       {:ok? false
        :output nil
        :diagnostics [{:message "transformの引数が不正です。"}]})
      (.then
       (select-column-renderer invocation-root (:project request))
       (fn [renderer]
         (renderer-contract/render-columns
          renderer
          [{:input input
            :context (assoc context :source-name (:input request))}]))))))
