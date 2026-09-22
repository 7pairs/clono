(ns clono.plugin.registry
  (:require
   [clojure.string :as string]
   [goog.object :as gobj]))

(defn- plugin-name [plugin]
  (gobj/get (:definition plugin) "name"))

(defn- column-renderer [plugin]
  (gobj/get (gobj/get (:definition plugin) "renderers") "column"))

(defn- conflict-paths [plugins]
  (->> plugins
       (map :specifier)
       (map pr-str)
       (string/join ", ")))

(defn- duplicate-name-diagnostics [config-path plugins]
  (->> plugins
       (group-by plugin-name)
       (keep (fn [[name matching-plugins]]
               (when (< 1 (count matching-plugins))
                 {:file config-path
                  :index (:index (first matching-plugins))
                  :message
                  (str "プラグイン名" (pr-str name)
                       "が重複しています: "
                       (conflict-paths matching-plugins))})))
       (sort-by :index)
       (mapv #(dissoc % :index))))

(defn- renderer-conflict-diagnostics [config-path plugins]
  (when (< 1 (count plugins))
    [{:file config-path
      :message
      (str "renderer名`column`が競合しています: "
           (->> plugins
                (map (fn [plugin]
                       (str (pr-str (plugin-name plugin))
                            " (" (pr-str (:specifier plugin)) ")")))
                (string/join ", ")))}]))

(defn build [config-path plugins]
  (let [indexed-plugins (mapv #(assoc %2 :index %1)
                              (range)
                              plugins)
        diagnostics (into (duplicate-name-diagnostics config-path
                                                      indexed-plugins)
                          (renderer-conflict-diagnostics config-path
                                                         indexed-plugins))]
    (if (seq diagnostics)
      {:ok? false
       :registry nil
       :diagnostics diagnostics}
      {:ok? true
       :registry
       (if-let [plugin (first plugins)]
         {"column" {:plugin plugin
                    :renderer (column-renderer plugin)}}
         {})
       :diagnostics []})))
