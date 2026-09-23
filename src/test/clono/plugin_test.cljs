(ns clono.plugin-test
  (:require
   [cljs.test :refer [async deftest is testing]]
   [clono.plugin :as plugin]
   [clono.plugin.loader :as loader]
   [clono.plugin.registry :as registry]))

(def config-path "/project/clono.config.mjs")

(defn- loaded-plugin [name file-name]
  (let [renderer (fn [_] (str "rendered by " name))]
    {:specifier (str "./plugins/" file-name)
     :path (str "plugins/" file-name)
     :file-path (str "/project/plugins/" file-name)
     :module #js {}
     :definition
     #js {:name name
          :version "1.0.0"
          :apiVersion 1
          :renderers #js {:column renderer}}}))

(deftest successful-plugin-registration-test
  (async done
    (testing "When plugin loading and registration succeed, then the renderer registry is returned"
      (let [loaded-plugin (loaded-plugin "column-theme" "column-theme.mjs")]
        (with-redefs [loader/load
                      (fn [_]
                        (js/Promise.resolve
                         {:ok? true
                          :plugins [loaded-plugin]
                          :diagnostics []}))]
          (-> (plugin/load-registry config-path {:plugins []})
              (.then (fn [result]
                       (let [registration (get (:registry result) "column")]
                         (is (:ok? result))
                         (is (empty? (:diagnostics result)))
                         (is (= loaded-plugin (:plugin registration)))
                         (is (= "rendered by column-theme"
                                ((:renderer registration) #js {}))))))
              (.catch (fn [error]
                        (is false
                            (str "Unexpected rejected promise: "
                                 (.-message error)))))
              (.finally done)))))))

(deftest plugin-loading-diagnostic-test
  (async done
    (testing "When plugin loading fails, then its diagnostic is returned without attempting registration"
      (let [diagnostic {:file "/project/plugins/broken.mjs"
                        :message "プラグインを読み込めません。"}]
        (with-redefs [loader/load
                      (fn [_]
                        (js/Promise.resolve
                         {:ok? false
                          :plugins []
                          :diagnostics [diagnostic]}))
                      registry/build
                      (fn [& _]
                        (throw (js/Error. "Registration must not start")))]
          (-> (plugin/load-registry config-path {:plugins []})
              (.then (fn [result]
                       (is (false? (:ok? result)))
                       (is (nil? (:registry result)))
                       (is (= [diagnostic] (:diagnostics result)))))
              (.catch (fn [error]
                        (is false
                            (str "Unexpected rejected promise: "
                                 (.-message error)))))
              (.finally done)))))))

(deftest plugin-registration-diagnostic-test
  (async done
    (testing "When renderer registration conflicts, then its configuration diagnostic is returned without a registry"
      (let [loaded-plugins [(loaded-plugin "first" "first.mjs")
                            (loaded-plugin "second" "second.mjs")]]
        (with-redefs [loader/load
                      (fn [_]
                        (js/Promise.resolve
                         {:ok? true
                          :plugins loaded-plugins
                          :diagnostics []}))]
          (-> (plugin/load-registry config-path {:plugins []})
              (.then (fn [result]
                       (is (false? (:ok? result)))
                       (is (nil? (:registry result)))
                       (is (= [{:file config-path
                                :message
                                (str "renderer名`column`が競合しています: "
                                     "\"first\" (\"./plugins/first.mjs\"), "
                                     "\"second\" (\"./plugins/second.mjs\")")}]
                              (:diagnostics result)))))
              (.catch (fn [error]
                        (is false
                            (str "Unexpected rejected promise: "
                                 (.-message error)))))
              (.finally done)))))))
