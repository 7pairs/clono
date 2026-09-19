(ns clono.research.verify-loading
  (:require
   ["node:os" :as os]
   ["node:path" :as path]
   [clono.research.plugin-loading :as plugin-loading]
   [goog.object :as gobj]))

(defn- assert! [condition message]
  (when-not condition
    (throw (js/Error. message))))

(defn- plugin-default [entry]
  (gobj/get (:module entry) "default"))

(defn- plugin-markers [result]
  (mapv #(gobj/get (plugin-default %) "marker") (:plugins result)))

(defn main []
  (let [original-directory (.cwd js/process)
        fixture-root (.resolve path js/__dirname "..")
        config-path (.join path
                           fixture-root
                           "project with space#hash"
                           "clono.config.mjs")]
    (.chdir js/process (.tmpdir os))
    (-> (plugin-loading/load-candidate-plugins config-path)
        (.then
         (fn [first-result]
           (assert! (= config-path (:config-path first-result))
                    "The resolved configuration path changed")
           (assert! (= ["./plugins/basic plugin.mjs"
                        "./plugins/top-level-await#plugin.mjs"
                        "./plugins/cached-plugin.mjs"]
                       (mapv :specifier (:plugins first-result)))
                    "The candidate plugin order changed")
           (assert! (= ["basic" "top-level-await" "cached"]
                       (plugin-markers first-result))
                    "The candidate plugin modules were not loaded in configuration order")
           (assert! (= 1
                       (gobj/get (plugin-default (last (:plugins first-result)))
                                 "evaluationCount"))
                    "The cached plugin was evaluated more than once during the first load")
           (plugin-loading/load-candidate-plugins config-path)))
        (.then
         (fn [second-result]
           (assert! (= ["basic" "top-level-await" "cached"]
                       (plugin-markers second-result))
                    "The repeated load changed the candidate plugin order")
           (assert! (= 1
                       (gobj/get (plugin-default (last (:plugins second-result)))
                                 "evaluationCount"))
                    "Node.js did not reuse the imported plugin module")))
        (.catch
         (fn [error]
           (.error js/console (or (.-stack error) (.-message error) (str error)))
           (set! (.-exitCode js/process) 1)))
        (.finally
         (fn []
           (.chdir js/process original-directory))))))
