(ns clono.plugin.registry-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.plugin.registry :as registry]))

(def config-path "/project/clono.config.mjs")

(defn- plugin [name file-name]
  (let [renderer (fn [_] file-name)]
    {:specifier (str "./plugins/" file-name)
     :path (str "plugins/" file-name)
     :file-path (str "/project/plugins/" file-name)
     :module #js {}
     :definition
     #js {:name name
          :version "1.0.0"
          :apiVersion 1
          :renderers #js {:column renderer}}}))

(deftest empty-renderer-registry-test
  (testing "When no external plugins are loaded, then an empty renderer registry is returned"
    (let [result (registry/build config-path [])]
      (is (:ok? result))
      (is (= {} (:registry result)))
      (is (empty? (:diagnostics result))))))

(deftest single-renderer-registry-test
  (testing "When one column renderer is loaded, then its function and source plugin are registered"
    (let [loaded-plugin (plugin "column-theme" "column-theme.mjs")
          result (registry/build config-path [loaded-plugin])
          registration (get (:registry result) "column")]
      (is (:ok? result))
      (is (empty? (:diagnostics result)))
      (is (= loaded-plugin (:plugin registration)))
      (is (identical? (aget (:definition loaded-plugin) "renderers" "column")
                      (:renderer registration))))))

(deftest duplicate-plugin-name-test
  (testing "When plugin names and renderer names conflict, then both conflicts identify every source plugin"
    (let [first-plugin (plugin "same-name" "first.mjs")
          second-plugin (plugin "same-name" "second.mjs")
          result (registry/build config-path [first-plugin second-plugin])]
      (is (false? (:ok? result)))
      (is (nil? (:registry result)))
      (is (= [{:file config-path
               :message (str "プラグイン名\"same-name\"が重複しています: "
                             "\"./plugins/first.mjs\", \"./plugins/second.mjs\"")}
              {:file config-path
               :message (str "renderer名`column`が競合しています: "
                             "\"same-name\" (\"./plugins/first.mjs\"), "
                             "\"same-name\" (\"./plugins/second.mjs\")")}]
             (:diagnostics result))))))

(deftest conflicting-renderer-test
  (testing "When different plugins register the column renderer, then no implicit precedence is selected"
    (let [result (registry/build config-path
                                 [(plugin "first-theme" "first.mjs")
                                  (plugin "second-theme" "second.mjs")])]
      (is (false? (:ok? result)))
      (is (nil? (:registry result)))
      (is (= [{:file config-path
               :message (str "renderer名`column`が競合しています: "
                             "\"first-theme\" (\"./plugins/first.mjs\"), "
                             "\"second-theme\" (\"./plugins/second.mjs\")")}]
             (:diagnostics result))))))
