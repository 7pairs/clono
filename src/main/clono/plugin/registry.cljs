(ns clono.plugin.registry
  (:require
   [clono.plugin.conflict :as conflict]
   [goog.object :as gobj]))

(defn- renderer-registrations [plugin]
  (let [renderers (gobj/get (:definition plugin) "renderers")]
    (map (fn [renderer-name]
           [renderer-name
            {:plugin plugin
             :renderer (gobj/get renderers renderer-name)}])
         (array-seq (js/Object.keys renderers)))))

(defn build [config-path plugins]
  (let [diagnostics (conflict/diagnostics config-path plugins)]
    (if (seq diagnostics)
      {:ok? false
       :registry nil
       :diagnostics diagnostics}
      {:ok? true
       :registry (into {} (mapcat renderer-registrations plugins))
       :diagnostics []})))
