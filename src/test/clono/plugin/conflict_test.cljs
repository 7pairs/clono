(ns clono.plugin.conflict-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.plugin.conflict :as conflict]))

(def config-path "/project/clono.config.mjs")

(defn- plugin [name file-name renderer-names]
  {:specifier (str "./plugins/" file-name)
   :definition
   #js {:name name
        :renderers (reduce (fn [renderers renderer-name]
                             (aset renderers renderer-name (fn [_] file-name))
                             renderers)
                           #js {}
                           renderer-names)}})

(deftest no-conflict-test
  (testing "When plugin names and renderer names are unique, then no conflict is reported"
    (is (empty? (conflict/diagnostics
                 config-path
                 [(plugin "column-theme" "column-theme.mjs" ["column"])])))))

(deftest duplicate-plugin-name-test
  (testing "When plugin names are duplicated, then every conflicting plugin path is reported"
    (is (= [{:file config-path
             :message (str "プラグイン名\"same-name\"が重複しています: "
                           "\"./plugins/first.mjs\", \"./plugins/third.mjs\"")}
            {:file config-path
             :message (str "renderer名`column`が競合しています: "
                           "\"same-name\" (\"./plugins/first.mjs\"), "
                           "\"second-name\" (\"./plugins/second.mjs\"), "
                           "\"same-name\" (\"./plugins/third.mjs\")")}]
           (conflict/diagnostics
            config-path
            [(plugin "same-name" "first.mjs" ["column"])
             (plugin "second-name" "second.mjs" ["column"])
             (plugin "same-name" "third.mjs" ["column"])])))))

(deftest conflicting-renderer-test
  (testing "When renderer names conflict, then every plugin name and path is reported without selecting precedence"
    (is (= [{:file config-path
             :message (str "renderer名`column`が競合しています: "
                           "\"first-theme\" (\"./plugins/first.mjs\"), "
                           "\"second-theme\" (\"./plugins/second.mjs\")")}]
           (conflict/diagnostics
            config-path
            [(plugin "first-theme" "first.mjs" ["column"])
             (plugin "second-theme" "second.mjs" ["column"])])))))
