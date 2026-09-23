(ns clono.plugin
  (:require
   [clono.plugin.loader :as loader]
   [clono.plugin.registry :as registry]))

(defn load-registry [config-path config]
  (let [load-plugins loader/load
        build-registry registry/build]
    (-> (load-plugins config)
        (.then
         (fn [load-result]
           (if (:ok? load-result)
             (build-registry config-path (:plugins load-result))
             {:ok? false
              :registry nil
              :diagnostics (:diagnostics load-result)}))))))
