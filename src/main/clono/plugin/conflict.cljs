(ns clono.plugin.conflict
  (:require
   [clojure.string :as string]
   [goog.object :as gobj]))

(defn- plugin-name [plugin]
  (gobj/get (:definition plugin) "name"))

(defn- renderer-names [plugin]
  (-> (gobj/get (:definition plugin) "renderers")
      js/Object.keys
      array-seq
      sort))

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
                       (->> matching-plugins
                            (map :specifier)
                            (map pr-str)
                            (string/join ", ")))})))
       (sort-by :index)
       (mapv #(dissoc % :index))))

(defn- renderer-registrations [plugins]
  (mapcat (fn [plugin]
            (map (fn [renderer-name]
                   {:index (:index plugin)
                    :name renderer-name
                    :plugin plugin})
                 (renderer-names plugin)))
          plugins))

(defn- renderer-conflict-diagnostics [config-path plugins]
  (->> (renderer-registrations plugins)
       (group-by :name)
       (keep (fn [[renderer-name registrations]]
               (when (< 1 (count registrations))
                 {:file config-path
                  :index (:index (first registrations))
                  :message
                  (str "renderer名`" renderer-name "`が競合しています: "
                       (->> registrations
                            (map :plugin)
                            (map (fn [plugin]
                                   (str (pr-str (plugin-name plugin))
                                        " (" (pr-str (:specifier plugin)) ")")))
                            (string/join ", ")))})))
       (sort-by (juxt :index :message))
       (mapv #(dissoc % :index))))

(defn diagnostics [config-path plugins]
  (let [indexed-plugins (mapv #(assoc %2 :index %1)
                              (range)
                              plugins)]
    (into (duplicate-name-diagnostics config-path indexed-plugins)
          (renderer-conflict-diagnostics config-path indexed-plugins))))
