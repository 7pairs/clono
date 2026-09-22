(ns clono.plugin.validator-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.plugin.validator :as validator]))

(def configured-plugin
  {:specifier "./plugins/column-theme.mjs"
   :path "plugins/column-theme.mjs"
   :file-path "/project/plugins/column-theme.mjs"})

(defn- module-with-default [definition]
  (js-obj "default" definition))

(defn- valid-definition []
  #js {:name "column-theme"
       :version "1.0.0"
       :apiVersion 1
       :renderers #js {:column (fn [_] "column")}})

(defn- messages [result]
  (mapv :message (:diagnostics result)))

(deftest valid-plugin-information-test
  (testing "When plugin information satisfies the API contract, then the plugin is accepted without changing its JavaScript values"
    (let [definition (valid-definition)
          module (module-with-default definition)
          result (validator/validate 0 configured-plugin module)]
      (is (:ok? result))
      (is (empty? (:diagnostics result)))
      (is (= configured-plugin
             (dissoc (:plugin result) :module :definition)))
      (is (identical? module (:module (:plugin result))))
      (is (identical? definition (:definition (:plugin result)))))))

(deftest invalid-default-export-test
  (testing "When the default export is missing or is not an object, then the plugin is rejected"
    (doseq [module [(js-obj)
                    (module-with-default nil)
                    (module-with-default #js [])
                    (module-with-default (fn [] nil))]]
      (let [result (validator/validate 0 configured-plugin module)]
        (is (false? (:ok? result)))
        (is (nil? (:plugin result)))
        (is (= [(str "`plugins[0]` (./plugins/column-theme.mjs)のdefault export"
                     "にはオブジェクトを指定してください。")]
               (messages result)))))))

(deftest invalid-plugin-fields-test
  (testing "When plugin fields violate the API contract, then every independent field diagnostic is returned"
    (let [definition #js {:name "   "
                          :version 1
                          :apiVersion 2
                          :renderers #js {:column "not a function"
                                          :unknown (fn [] nil)}
                          :extra true}
          result (validator/validate 2 configured-plugin
                                     (module-with-default definition))]
      (is (false? (:ok? result)))
      (is (nil? (:plugin result)))
      (is (= #{(str "`plugins[2]` (./plugins/column-theme.mjs)のdefault export"
                       "に未知の項目`extra`があります。")
               (str "`plugins[2]` (./plugins/column-theme.mjs)のdefault export"
                    "の`name`には空でない文字列を指定してください。")
               (str "`plugins[2]` (./plugins/column-theme.mjs)のdefault export"
                    "の`version`には空でない文字列を指定してください。")
               (str "`plugins[2]` (./plugins/column-theme.mjs)のdefault export"
                    "の`apiVersion`には数値の`1`を指定してください。")
               (str "`plugins[2]` (./plugins/column-theme.mjs)の`renderers`"
                    "に未知の項目`unknown`があります。")
               (str "`plugins[2]` (./plugins/column-theme.mjs)の`renderers`"
                    "の`column`には関数を指定してください。")}
             (set (messages result)))))))

(deftest missing-plugin-fields-test
  (testing "When required plugin fields are absent, then each missing field is reported"
    (let [result (validator/validate 0 configured-plugin
                                     (module-with-default #js {}))]
      (is (false? (:ok? result)))
      (is (= #{"apiVersion" "name" "renderers" "version"}
             (->> (messages result)
                  (keep #(second (re-find #"必須の項目`([^`]+)`" %)))
                  set))))))

(deftest invalid-renderers-test
  (testing "When renderers is not an object, then the plugin is rejected with a renderer diagnostic"
    (let [definition (valid-definition)]
      (aset definition "renderers" #js [])
      (let [result (validator/validate 0 configured-plugin
                                       (module-with-default definition))]
        (is (false? (:ok? result)))
        (is (= [(str "`plugins[0]` (./plugins/column-theme.mjs)の`renderers`"
                     "にはオブジェクトを指定してください。")]
               (messages result)))))))

(deftest missing-column-renderer-test
  (testing "When the column renderer is absent, then the required renderer is reported"
    (let [definition (valid-definition)]
      (aset definition "renderers" #js {})
      (let [result (validator/validate 0 configured-plugin
                                       (module-with-default definition))]
        (is (false? (:ok? result)))
        (is (= [(str "`plugins[0]` (./plugins/column-theme.mjs)の`renderers`"
                     "に必須の項目`column`がありません。")]
               (messages result)))))))

(deftest exceptional-plugin-information-test
  (testing "When plugin information cannot be inspected, then the failure is returned without a JavaScript stack trace"
    (let [definition (js/Proxy. #js {}
                                #js {:ownKeys
                                     (fn []
                                       (throw (js/Error. "inspection exploded")))})
          result (validator/validate 0 configured-plugin
                                     (module-with-default definition))]
      (is (false? (:ok? result)))
      (is (= [(str "`plugins[0]` (./plugins/column-theme.mjs)"
                   "のdefault exportを検証できません: inspection exploded")]
             (messages result)))
      (is (not (.includes (:message (first (:diagnostics result))) "    at "))))))
