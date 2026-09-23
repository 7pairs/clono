(ns clono.plugin.validator
  (:require
   [clojure.string :as string]
   [goog.object :as gobj]))

(def ^:private plugin-fields
  #{"name" "version" "apiVersion" "renderers"})
(def ^:private renderer-fields #{"column"})

(defn- javascript-object? [value]
  (and (some? value)
       (= "object" (goog/typeOf value))
       (not (js/Array.isArray value))))

(defn- own-field-names [value]
  (if (javascript-object? value)
    (set (array-seq (.keys js/Object value)))
    #{}))

(defn- context [index plugin]
  (str "`plugins[" index "]` (" (:specifier plugin) ")"))

(defn- diagnostic [plugin message]
  {:file (:file-path plugin)
   :message message})

(defn- error-message [error]
  (let [message (or (.-message error) (str error))]
    (string/replace message #"\s*[\r\n]+\s*" " ")))

(defn- unknown-field-diagnostics [plugin context-value allowed-fields value]
  (->> (own-field-names value)
       (remove allowed-fields)
       sort
       (mapv #(diagnostic
               plugin
               (str context-value "に未知の項目`" % "`があります。")))))

(defn- required-field-diagnostics [plugin context-value required-fields value]
  (->> required-fields
       sort
       (remove #(contains? (own-field-names value) %))
       (mapv #(diagnostic
               plugin
               (str context-value "に必須の項目`" % "`がありません。")))))

(defn- validate-renderers [index plugin renderers]
  (let [context-value (str (context index plugin) "の`renderers`")]
    (if-not (javascript-object? renderers)
      [(diagnostic plugin
                   (str context-value "にはオブジェクトを指定してください。"))]
      (let [field-names (own-field-names renderers)]
        (into (into (unknown-field-diagnostics plugin
                                               context-value
                                               renderer-fields
                                               renderers)
                    (required-field-diagnostics plugin
                                                context-value
                                                renderer-fields
                                                renderers))
              (when (and (contains? field-names "column")
                         (not (fn? (gobj/get renderers "column"))))
                [(diagnostic plugin
                             (str context-value
                                  "の`column`には関数を指定してください。"))]))))))

(defn- validate-definition [index plugin module]
  (let [definition (gobj/get module "default")
        context-value (str (context index plugin) "のdefault export")]
    (if-not (javascript-object? definition)
      {:plugin nil
       :diagnostics
       [(diagnostic plugin
                    (str context-value "にはオブジェクトを指定してください。"))]}
      (let [field-names (own-field-names definition)
            name-value (gobj/get definition "name")
            version (gobj/get definition "version")
            api-version (gobj/get definition "apiVersion")
            renderers (gobj/get definition "renderers")
            diagnostics
            (into
             (into (unknown-field-diagnostics plugin
                                              context-value
                                              plugin-fields
                                              definition)
                   (required-field-diagnostics plugin
                                               context-value
                                               plugin-fields
                                               definition))
             (concat
              (when (and (contains? field-names "name")
                         (not (and (string? name-value)
                                   (not (string/blank? name-value)))))
                [(diagnostic plugin
                             (str context-value
                                  "の`name`には空でない文字列を指定してください。"))])
              (when (and (contains? field-names "version")
                         (not (and (string? version)
                                   (not (string/blank? version)))))
                [(diagnostic plugin
                             (str context-value
                                  "の`version`には空でない文字列を指定してください。"))])
              (when (and (contains? field-names "apiVersion")
                         (not= 1 api-version))
                [(diagnostic plugin
                             (str context-value
                                  "の`apiVersion`には数値の`1`を指定してください。"))])
              (when (contains? field-names "renderers")
                (validate-renderers index plugin renderers))))]
        {:plugin (when (empty? diagnostics)
                   (assoc plugin
                          :module module
                          :definition definition))
         :diagnostics diagnostics}))))

(defn validate [index plugin module]
  (try
    (let [{:keys [plugin diagnostics]}
          (validate-definition index plugin module)]
      {:ok? (empty? diagnostics)
       :plugin plugin
       :diagnostics diagnostics})
    (catch :default error
      {:ok? false
       :plugin nil
       :diagnostics
       [(diagnostic plugin
                    (str (context index plugin)
                         "のdefault exportを検証できません: "
                         (error-message error)))]})))
