(ns clono.plugin.loader-test
  (:require
   ["node:url" :refer [pathToFileURL]]
   [cljs.test :refer [async deftest is testing]]
   [clono.plugin.loader :as loader]))

(defn- plugin [name]
  {:specifier (str "./plugins/" name ".mjs")
   :path (str "plugins/" name ".mjs")
   :file-path (str "/project/plugins/" name ".mjs")})

(defn- valid-module [name]
  #js {:default
       #js {:name name
            :version "1.0.0"
            :apiVersion 1
            :renderers #js {:column (fn [_] "column")}}})

(deftest empty-plugin-loading-test
  (async done
    (testing "When no plugins are configured, then loading succeeds without importing a module"
      (with-redefs [loader/import-plugin-module
                    (fn [_]
                      (throw (js/Error. "No module should be imported")))]
        (-> (loader/load {:plugins []})
            (.then (fn [result]
                     (is (:ok? result))
                     (is (empty? (:plugins result)))
                     (is (empty? (:diagnostics result)))))
            (.catch (fn [error]
                      (is false (str "Unexpected rejected promise: " (.-message error)))))
            (.finally done))))))

(deftest sequential-plugin-loading-test
  (async done
    (testing "When plugin modules are valid, then they are returned in configuration order"
      (let [plugins [(plugin "first") (plugin "second")]
            imported-specifiers (atom [])
            modules [(valid-module "first") (valid-module "second")]]
        (with-redefs [loader/import-plugin-module
                      (fn [specifier]
                        (let [index (count @imported-specifiers)]
                          (swap! imported-specifiers conj specifier)
                          (js/Promise.resolve (nth modules index))))]
          (-> (loader/load {:plugins plugins})
              (.then (fn [result]
                       (is (:ok? result))
                       (is (empty? (:diagnostics result)))
                       (is (= [(.-href (pathToFileURL "/project/plugins/first.mjs"))
                               (.-href (pathToFileURL "/project/plugins/second.mjs"))]
                              @imported-specifiers))
                       (is (= plugins
                              (mapv #(dissoc % :module :definition)
                                    (:plugins result))))
                       (is (= modules (mapv :module (:plugins result))))))
              (.catch (fn [error]
                        (is false (str "Unexpected rejected promise: " (.-message error)))))
              (.finally done)))))))

(deftest failed-plugin-loading-test
  (async done
    (testing "When plugin evaluation fails, then that plugin is diagnosed and later plugins are not evaluated"
      (let [plugins [(plugin "first") (plugin "broken") (plugin "later")]
            imported-specifiers (atom [])]
        (with-redefs [loader/import-plugin-module
                      (fn [specifier]
                        (swap! imported-specifiers conj specifier)
                        (if (.endsWith specifier "/broken.mjs")
                          (let [error (js/Error. "evaluation exploded")]
                            (set! (.-stack error)
                                  "Error: evaluation exploded\n    at private stack")
                            (js/Promise.reject error))
                          (js/Promise.resolve (valid-module "first"))))]
          (-> (loader/load {:plugins plugins})
              (.then (fn [result]
                       (is (false? (:ok? result)))
                       (is (empty? (:plugins result)))
                       (is (= [(.-href (pathToFileURL "/project/plugins/first.mjs"))
                               (.-href (pathToFileURL "/project/plugins/broken.mjs"))]
                              @imported-specifiers))
                       (is (= [{:file "/project/plugins/broken.mjs"
                                :message (str "`plugins[1]`のプラグインを読み込みまたは評価できません: "
                                              "./plugins/broken.mjs: "
                                              "evaluation exploded")}]
                              (:diagnostics result)))
                       (is (not (.includes (:message (first (:diagnostics result)))
                                           "private stack")))))
              (.catch (fn [error]
                        (is false (str "Unexpected rejected promise: " (.-message error)))))
              (.finally done)))))))

(deftest invalid-plugin-information-loading-test
  (async done
    (testing "When plugin information is invalid, then later plugin modules are not evaluated"
      (let [plugins [(plugin "invalid") (plugin "later")]
            imported-specifiers (atom [])]
        (with-redefs [loader/import-plugin-module
                      (fn [specifier]
                        (swap! imported-specifiers conj specifier)
                        (js/Promise.resolve #js {:default #js {}}))]
          (-> (loader/load {:plugins plugins})
              (.then (fn [result]
                       (is (false? (:ok? result)))
                       (is (empty? (:plugins result)))
                       (is (= [(.-href (pathToFileURL
                                       "/project/plugins/invalid.mjs"))]
                              @imported-specifiers))
                       (is (= 4 (count (:diagnostics result))))))
              (.catch (fn [error]
                        (is false (str "Unexpected rejected promise: " (.-message error)))))
              (.finally done)))))))

(deftest synchronous-import-failure-test
  (async done
    (testing "When module import setup throws, then the plugin failure is returned as a diagnostic"
      (let [configured-plugin (plugin "broken")]
        (with-redefs [loader/import-plugin-module
                      (fn [_]
                        (throw (js/Error. "specifier rejected")))]
          (-> (loader/load {:plugins [configured-plugin]})
              (.then (fn [result]
                       (is (false? (:ok? result)))
                       (is (empty? (:plugins result)))
                       (is (= "/project/plugins/broken.mjs"
                              (:file (first (:diagnostics result)))))
                       (is (.includes (:message (first (:diagnostics result)))
                                      "specifier rejected"))))
              (.catch (fn [error]
                        (is false (str "Unexpected rejected promise: " (.-message error)))))
              (.finally done)))))))
