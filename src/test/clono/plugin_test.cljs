(ns clono.plugin-test
  (:require
   [cljs.test :refer [async deftest is testing]]
   [clono.plugin :as plugin]
   [clono.plugin.loader :as loader]
   [clono.plugin.registry :as registry]))

(def config-path "/project/clono.config.mjs")

(deftest successful-plugin-registration-test
  (async done
    (testing "When plugin loading and registration succeed, then the renderer registry is returned"
      (let [loaded-plugins [{:specifier "./plugins/column-theme.mjs"}]
            expected-registry {"column" {:renderer identity}}]
        (with-redefs [loader/load
                      (fn [_]
                        (js/Promise.resolve
                         {:ok? true
                          :plugins loaded-plugins
                          :diagnostics []}))
                      registry/build
                      (fn [actual-config-path actual-plugins]
                        (is (= config-path actual-config-path))
                        (is (= loaded-plugins actual-plugins))
                        {:ok? true
                         :registry expected-registry
                         :diagnostics []})]
          (-> (plugin/load-registry config-path {:plugins []})
              (.then (fn [result]
                       (is (:ok? result))
                       (is (= expected-registry (:registry result)))
                       (is (empty? (:diagnostics result)))))
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
      (let [loaded-plugins [{:specifier "./plugins/first.mjs"}
                            {:specifier "./plugins/second.mjs"}]
            diagnostic {:file config-path
                        :message "renderer名`column`が競合しています。"}]
        (with-redefs [loader/load
                      (fn [_]
                        (js/Promise.resolve
                         {:ok? true
                          :plugins loaded-plugins
                          :diagnostics []}))
                      registry/build
                      (fn [_ _]
                        {:ok? false
                         :registry nil
                         :diagnostics [diagnostic]})]
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
