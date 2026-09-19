(ns clono.research.verify-loading
  (:require
   ["node:os" :as os]
   ["node:path" :as path]
   [clono.research.plugin-loading :as plugin-loading]
   [goog.object :as gobj]))

(defn- assert! [condition message]
  (when-not condition
    (throw (js/Error. message))))

(defn- plugin-markers [result]
  (mapv #(gobj/get (plugin-loading/candidate-plugin %) "marker")
        (:plugins result)))

(defn main []
  (let [original-directory (.cwd js/process)
        fixture-root (.resolve path js/__dirname "..")
        config-path (.join path
                           fixture-root
                           "project with space#hash"
                           "clono.config.mjs")
        behavior-config-path (.join path
                                    fixture-root
                                    "project with space#hash"
                                    "renderer-behaviors.config.mjs")
        invalid-export-config-path (.join path
                                          fixture-root
                                          "project with space#hash"
                                          "invalid-export.config.mjs")
        missing-plugin-config-path (.join path
                                          fixture-root
                                          "project with space#hash"
                                          "missing-plugin.config.mjs")
        first-renderer (atom nil)]
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
           (assert! (= [{:renderer-name "column"
                         :specifiers ["./plugins/basic plugin.mjs"
                                      "./plugins/top-level-await#plugin.mjs"
                                      "./plugins/cached-plugin.mjs"]}]
                       (plugin-loading/duplicate-candidate-renderers
                        (:plugins first-result)))
                    "Duplicate candidate renderers were not reported in configuration order")
           (assert! (= [{:name "research-basic-plugin"
                         :version "0.0.0"
                         :api-version 1}
                        {:name "research-top-level-await-plugin"
                         :version "0.0.0"
                         :api-version 1}
                        {:name "research-cached-plugin"
                         :version "0.0.0"
                         :api-version 1}]
                       (mapv plugin-loading/candidate-plugin-info
                             (:plugins first-result)))
                    "ClojureScript could not read the candidate plugin information")
           (assert! (= 1
                       (gobj/get
                        (plugin-loading/candidate-plugin
                         (last (:plugins first-result)))
                        "evaluationCount"))
                    "The cached plugin was evaluated more than once during the first load")
           (let [input (js/Object.freeze
                        #js {:title "ちょっと休憩"
                             :body "これは**雑談**です。"})
                 outputs (mapv #(plugin-loading/invoke-candidate-renderer
                                 %
                                 "column"
                                 input)
                               (:plugins first-result))]
             (assert! (= ["basic:ちょっと休憩:これは**雑談**です。"
                          "top-level-await:ちょっと休憩:これは**雑談**です。"
                          "cached:ちょっと休憩:これは**雑談**です。"]
                         outputs)
                      "ClojureScript could not invoke the candidate renderers")
             (assert! (= "ちょっと休憩" (.-title input))
                      "A candidate renderer changed the input title")
             (assert! (= "これは**雑談**です。" (.-body input))
                      "A candidate renderer changed the input body"))
           (reset! first-renderer
                   (plugin-loading/candidate-renderer
                    (first (:plugins first-result))
                    "column"))
           (assert! (= "basic:別の題名:別の本文"
                       (plugin-loading/invoke-candidate-renderer
                        (first (:plugins first-result))
                        "column"
                        #js {:title "別の題名" :body "別の本文"}))
                    "The candidate renderer could not be invoked repeatedly")
           (plugin-loading/load-candidate-plugins config-path)))
        (.then
         (fn [second-result]
           (assert! (= ["basic" "top-level-await" "cached"]
                       (plugin-markers second-result))
                    "The repeated load changed the candidate plugin order")
           (assert! (= 1
                       (gobj/get
                        (plugin-loading/candidate-plugin
                         (last (:plugins second-result)))
                        "evaluationCount"))
                    "Node.js did not reuse the imported plugin module")
           (assert! (identical? @first-renderer
                                (plugin-loading/candidate-renderer
                                 (first (:plugins second-result))
                                 "column"))
                    "The repeated import changed the renderer function identity")
           (let [sync-result
                 (plugin-loading/inspect-candidate-renderer-call
                  (first (:plugins second-result))
                  "column"
                  #js {:title "同期呼び出し"
                       :body "文字列を返します。"})]
             (assert! (= {:status :accepted
                          :output "basic:同期呼び出し:文字列を返します。"}
                         sync-result)
                      (str "A synchronous string renderer was not accepted: "
                           (pr-str sync-result))))
           (plugin-loading/load-candidate-plugins behavior-config-path)))
        (.then
         (fn [behavior-result]
           (let [[throwing-entry invalid-entry promise-entry]
                 (:plugins behavior-result)
                 throwing-result
                 (plugin-loading/inspect-candidate-renderer-call
                  throwing-entry "column" #js {})
                 invalid-result
                 (plugin-loading/inspect-candidate-renderer-call
                  invalid-entry "column" #js {})
                 promise-result
                 (plugin-loading/inspect-candidate-renderer-call
                  promise-entry "column" #js {})]
             (assert! (= {:status :rejected
                          :reason :renderer-threw}
                         (dissoc throwing-result :error))
                      "A renderer exception was not distinguished from other failures")
             (assert! (= "The renderer deliberately failed"
                         (.-message (:error throwing-result)))
                      "The renderer exception was not preserved")
             (assert! (= {:status :rejected
                          :reason :invalid-return-value
                          :actual-type "object"}
                         invalid-result)
                      "A non-string renderer result was not rejected")
             (assert! (= {:status :rejected
                          :reason :promise-returned}
                         promise-result)
                      "A Promise renderer result was not rejected"))
           (plugin-loading/load-candidate-plugins invalid-export-config-path)))
        (.then
         (fn [invalid-export-result]
           (assert! (= {:status :rejected
                        :reason :invalid-default-export
                        :actual-type "undefined"}
                       (plugin-loading/inspect-candidate-export
                        (first (:plugins invalid-export-result))))
                    "A module without a default export was not rejected")
           (-> (plugin-loading/load-candidate-plugins
                missing-plugin-config-path)
               (.then (fn [_]
                        {:loaded? true})
                      (fn [error]
                        {:loaded? false
                         :error error})))))
        (.then
         (fn [missing-plugin-result]
           (assert! (false? (:loaded? missing-plugin-result))
                    "A missing plugin file was loaded successfully")
           (assert! (= "ERR_MODULE_NOT_FOUND"
                       (.-code (:error missing-plugin-result)))
                    "A missing plugin file did not preserve the Node.js error code")
           (assert! (.includes (.-message (:error missing-plugin-result))
                               "missing-plugin.mjs")
                    "A missing plugin error did not identify the requested file")))
        (.catch
         (fn [error]
           (.error js/console (or (.-stack error) (.-message error) (str error)))
           (set! (.-exitCode js/process) 1)))
        (.finally
         (fn []
           (.chdir js/process original-directory))))))
