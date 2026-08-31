(ns clono.book.config
  (:require
   ["node:fs" :as fs]
   ["node:path" :as path]
   ["node:url" :refer [pathToFileURL]]
   [clojure.string :as string]
   [goog.object :as gobj]))

(def ^:private config-file-name "clono.config.mjs")
(def ^:private top-level-fields
  #{"sourceRoot" "outputRoot" "publication"})
(def ^:private publication-fields
  #{"type" "path" "kind" "includeInToc"})
(def ^:private document-fields publication-fields)
(def ^:private blank-page-fields #{"type"})
(def ^:private document-kinds
  #{"frontmatter" "chapter" "appendix" "backmatter"})
;; Closure cannot transpile a dynamic import expression in a node-script release.
(def ^:private dynamic-import
  (js/Function. "specifier" "return import(specifier);"))

(defn- error-message [error]
  (or (.-message error) (str error)))

(defn- diagnostic [config-path message]
  {:file config-path
   :message message})

(defn- javascript-object? [value]
  (and (some? value)
       (= "object" (goog/typeOf value))
       (not (js/Array.isArray value))))

(defn- own-field-names [value]
  (if (javascript-object? value)
    (set (array-seq (.keys js/Object value)))
    #{}))

(defn- unknown-field-diagnostics [config-path context allowed-fields value]
  (->> (own-field-names value)
       (remove allowed-fields)
       sort
       (mapv #(diagnostic
               config-path
               (str context "に未知の設定項目`" % "`があります。")))))

(defn- required-field-diagnostics [config-path context required-fields value]
  (->> required-fields
       sort
       (remove #(contains? (own-field-names value) %))
       (mapv #(diagnostic
               config-path
               (str context "に必須の設定項目`" % "`がありません。")))))

(defn- portable-absolute-path? [value]
  (or (.isAbsolute path value)
      (boolean (re-find #"^[A-Za-z]:/" value))
      (.startsWith value "//")))

(defn- relative-path-result [config-path field-name value]
  (cond
    (not (string? value))
    {:diagnostics
     [(diagnostic config-path (str "`" field-name "`には文字列を指定してください。"))]}

    (empty? value)
    {:diagnostics
     [(diagnostic config-path (str "`" field-name "`に空文字列は指定できません。"))]}

    (.includes value "\\")
    {:diagnostics
     [(diagnostic config-path
                  (str "`" field-name "`の区切り文字には`/`を使用してください。"))]}

    (portable-absolute-path? value)
    {:diagnostics
     [(diagnostic config-path (str "`" field-name "`に絶対パスは指定できません。"))]}

    :else
    (let [normalized-value (.normalize (.-posix path) value)
          normalized (if (and (not= normalized-value ".")
                              (.endsWith normalized-value "/"))
                       (.slice normalized-value 0 -1)
                       normalized-value)]
      (if (or (= normalized "..") (.startsWith normalized "../"))
        {:diagnostics
         [(diagnostic config-path
                      (str "`" field-name "`に基準ディレクトリの外側へ出るパスは指定できません。"))]}
        {:value normalized
         :diagnostics []}))))

(defn- resolve-portable-path [root relative-path]
  (.resolve path root (string/replace relative-path "/" (.-sep path))))

(defn- lstat-if-present [candidate]
  (try
    (.lstatSync fs candidate)
    (catch :default error
      (if (= "ENOENT" (.-code error))
        nil
        (throw error)))))

(defn- existing-symlink [root relative-path]
  (loop [current root
         segments (seq (remove #{"."} (string/split relative-path #"/")))]
    (when-let [segment (first segments)]
      (let [candidate (.join path current segment)
            candidate-stat (lstat-if-present candidate)]
        (when candidate-stat
          (if (.isSymbolicLink candidate-stat)
            candidate
            (recur candidate (next segments))))))))

(defn- path-descendant? [parent child]
  (let [relative (.relative path parent child)]
    (and (not (empty? relative))
         (not= relative "..")
         (not (.startsWith relative (str ".." (.-sep path))))
         (not (.isAbsolute path relative)))))

(defn- source-readability-diagnostics [config-path source-path]
  (try
    (.accessSync fs source-path (.-R_OK (.-constants fs)))
    []
    (catch :default error
      [(diagnostic config-path
                   (str "`sourceRoot`を読み取れません: "
                        (error-message error)))])))

(defn- validate-root-paths [config-path project-root source-result output-result]
  (if (or (nil? (:value source-result)) (nil? (:value output-result)))
    {:diagnostics []}
    (let [source-root (:value source-result)
          output-root (:value output-result)
          source-path (resolve-portable-path project-root source-root)
          output-path (resolve-portable-path project-root output-root)]
      (try
        (let [source-stat (when (.existsSync fs source-path)
                            (.statSync fs source-path))
              output-stat (when (.existsSync fs output-path)
                            (.statSync fs output-path))
              source-symlink (existing-symlink project-root source-root)
              output-symlink (existing-symlink project-root output-root)
              path-diagnostics
              (cond-> []
                (nil? source-stat)
                (conj (diagnostic config-path
                                  "`sourceRoot`には既存のディレクトリを指定してください。"))

                (and source-stat (not (.isDirectory source-stat)))
                (conj (diagnostic config-path
                                  "`sourceRoot`にはディレクトリを指定してください。"))

                source-symlink
                (conj (diagnostic config-path
                                  (str "`sourceRoot`はシンボリックリンクを経由できません: "
                                       source-symlink)))

                (and output-stat (not (.isDirectory output-stat)))
                (conj (diagnostic config-path
                                  "`outputRoot`にはディレクトリパスを指定してください。"))

                output-symlink
                (conj (diagnostic config-path
                                  (str "`outputRoot`はシンボリックリンクを経由できません: "
                                       output-symlink)))

                (or (= source-path output-path)
                    (path-descendant? source-path output-path)
                    (path-descendant? output-path source-path))
                (conj (diagnostic config-path
                                  "`sourceRoot`と`outputRoot`には同じパスまたは祖先・子孫関係にあるパスを指定できません。")))]
          (let [readability-diagnostics
                (if (and source-stat
                         (.isDirectory source-stat)
                         (nil? source-symlink))
                  (source-readability-diagnostics config-path source-path)
                  [])]
            {:source-path source-path
             :output-path output-path
             :diagnostics (into path-diagnostics readability-diagnostics)}))
        (catch :default error
          {:diagnostics
           [(diagnostic config-path
                        (str "入力原稿ルートと生成済み原稿ルートを確認できません: "
                             (error-message error)))]})))))

(defn- document-entry-structure [config-path index entry]
  (let [context (str "`publication[" index "]`")
        field-names (own-field-names entry)
        path-value (gobj/get entry "path")
        kind (gobj/get entry "kind")
        include-in-toc (gobj/get entry "includeInToc")
        path-result (if (contains? field-names "path")
                      (relative-path-result config-path
                                            (str "publication[" index "].path")
                                            path-value)
                      {:diagnostics []})
        diagnostics
        (into (into (unknown-field-diagnostics config-path
                                                context
                                                document-fields
                                                entry)
                    (required-field-diagnostics config-path
                                                context
                                                document-fields
                                                entry))
              (concat
               (:diagnostics path-result)
               (when (and (:value path-result)
                          (not (contains? #{".md" ".html"}
                                          (.toLowerCase
                                           (.extname path (:value path-result))))))
                 [(diagnostic config-path
                              (str "`publication[" index "].path`には`.md`または`.html`のファイルを指定してください。"))])
               (when (and (contains? field-names "kind")
                          (not (and (string? kind) (contains? document-kinds kind))))
                 [(diagnostic config-path
                              (str "`publication[" index "].kind`には`frontmatter`、`chapter`、`appendix`または`backmatter`を指定してください。"))])
               (when (and (contains? field-names "includeInToc")
                          (not (boolean? include-in-toc)))
                 [(diagnostic config-path
                              (str "`publication[" index "].includeInToc`には真偽値を指定してください。"))])))]
    {:entry (when (empty? diagnostics)
              {:type :document
               :path (:value path-result)
               :kind kind
               :include-in-toc include-in-toc})
     :diagnostics diagnostics}))

(defn- blank-page-entry-structure [config-path index entry]
  (let [context (str "`publication[" index "]`")
        diagnostics (unknown-field-diagnostics config-path
                                                context
                                                blank-page-fields
                                                entry)]
    {:entry (when (empty? diagnostics)
              {:type :blank-page})
     :diagnostics diagnostics}))

(defn- publication-entry-structure [config-path index entry]
  (let [context (str "`publication[" index "]`")]
    (if-not (javascript-object? entry)
      {:diagnostics
       [(diagnostic config-path (str context "にはオブジェクトを指定してください。"))]}
      (let [field-names (own-field-names entry)
            type-present? (contains? field-names "type")
            entry-type (gobj/get entry "type")]
        (cond
          (not type-present?)
          {:entry nil
           :diagnostics
           [(diagnostic config-path
                        (str context "に必須の設定項目`type`がありません。"))]}

          (= "document" entry-type)
          (document-entry-structure config-path index entry)

          (= "blank-page" entry-type)
          (blank-page-entry-structure config-path index entry)

          :else
          {:entry nil
           :diagnostics
           (into (unknown-field-diagnostics config-path
                                            context
                                            publication-fields
                                            entry)
                 [(diagnostic config-path
                              (str "`publication[" index "].type`には`document`または`blank-page`を指定してください。"))])})))))

(defn- validate-publication-files [config-path source-path entries]
  (loop [remaining entries
         seen #{}
         validated []
         diagnostics []]
    (if-let [entry (first remaining)]
      (if (= :blank-page (:type entry))
        (recur (next remaining)
               seen
               (conj validated entry)
               diagnostics)
        (let [entry-path (:path entry)
              file-path (resolve-portable-path source-path entry-path)
              duplicate? (contains? seen entry-path)
              path-diagnostics
              (try
              (let [symlink (existing-symlink source-path entry-path)
                    file-stat (when (.existsSync fs file-path)
                                (.statSync fs file-path))]
                (cond-> []
                  duplicate?
                  (conj (diagnostic config-path
                                    (str "`publication`に同じ原稿パスが重複しています: " entry-path)))

                  (nil? file-stat)
                  (conj (diagnostic config-path
                                    (str "`publication`の原稿が存在しません: " entry-path)))

                  (and file-stat (not (.isFile file-stat)))
                  (conj (diagnostic config-path
                                    (str "`publication`には通常ファイルを指定してください: " entry-path)))

                  symlink
                  (conj (diagnostic config-path
                                    (str "`publication`の原稿はシンボリックリンクを経由できません: "
                                         entry-path)))))
              (catch :default error
                [(diagnostic config-path
                             (str "`publication`の原稿を確認できません: " entry-path ": "
                                  (error-message error)))]))]
          (recur (next remaining)
                 (conj seen entry-path)
                 (conj validated (assoc entry :file-path file-path))
                 (into diagnostics path-diagnostics))))
      {:publication validated
       :diagnostics diagnostics})))

(defn- validate-config [project-root config-path value]
  (if-not (javascript-object? value)
    {:ok? false
     :config nil
     :diagnostics [(diagnostic config-path
                               "default exportには設定オブジェクトを指定してください。")]}
    (let [field-names (own-field-names value)
          source-result (if (contains? field-names "sourceRoot")
                          (relative-path-result config-path
                                                "sourceRoot"
                                                (gobj/get value "sourceRoot"))
                          {:diagnostics []})
          output-result (if (contains? field-names "outputRoot")
                          (relative-path-result config-path
                                                "outputRoot"
                                                (gobj/get value "outputRoot"))
                          {:diagnostics []})
          publication-value (gobj/get value "publication")
          publication-present? (contains? field-names "publication")
          publication-array? (and publication-present?
                                  (js/Array.isArray publication-value))
          publication-results (if publication-array?
                                (map-indexed #(publication-entry-structure
                                              config-path %1 %2)
                                             (array-seq publication-value))
                                [])
          root-result (validate-root-paths config-path
                                           project-root
                                           source-result
                                           output-result)
          base-structural-diagnostics
          (into (into (unknown-field-diagnostics config-path
                                                  "設定"
                                                  top-level-fields
                                                  value)
                      (required-field-diagnostics config-path
                                                  "設定"
                                                  top-level-fields
                                                  value))
                (concat
                 (:diagnostics source-result)
                 (:diagnostics output-result)
                 (when (and publication-present? (not publication-array?))
                   [(diagnostic config-path "`publication`には配列を指定してください。")])
                 (mapcat :diagnostics publication-results)
                 (:diagnostics root-result)))
          structured-entries (mapv :entry publication-results)
          document-required-diagnostics
          (if (and publication-array?
                   (every? some? structured-entries)
                   (not-any? #(= :document (:type %)) structured-entries))
            [(diagnostic config-path
                         "`publication`には一件以上の`document`を指定してください。")]
            [])
          structural-diagnostics (into base-structural-diagnostics
                                       document-required-diagnostics)
          files-result (if (and (empty? structural-diagnostics)
                                (every? some? structured-entries))
                         (validate-publication-files config-path
                                                     (:source-path root-result)
                                                     structured-entries)
                         {:publication []
                          :diagnostics []})
          diagnostics (into structural-diagnostics (:diagnostics files-result))]
      (if (seq diagnostics)
        {:ok? false
         :config nil
         :diagnostics diagnostics}
        {:ok? true
         :config {:project-root project-root
                  :config-path config-path
                  :source-root (:value source-result)
                  :source-path (:source-path root-result)
                  :output-root (:value output-result)
                  :output-path (:output-path root-result)
                  :publication (:publication files-result)}
         :diagnostics []}))))

(defn ^:no-doc import-config-module [specifier]
  (dynamic-import specifier))

(defn- import-error-result [config-path error]
  {:ok? false
   :config nil
   :diagnostics
   [(diagnostic config-path
                (str "`clono.config.mjs`を読み込めません: "
                     (error-message error)))]})

(defn- validation-error-result [config-path error]
  {:ok? false
   :config nil
   :diagnostics
   [(diagnostic config-path
                (str "`clono.config.mjs`を検証できません: "
                     (error-message error)))]})

(defn load-project-config [project]
  (let [project-root (.resolve path project)
        config-path (.join path project-root config-file-name)]
    (try
      (let [project-stat (when (.existsSync fs project-root)
                           (.statSync fs project-root))
            config-stat (when (.existsSync fs config-path)
                          (.lstatSync fs config-path))]
        (cond
          (nil? project-stat)
          (js/Promise.resolve
           {:ok? false
            :config nil
            :diagnostics [(diagnostic config-path
                                      "書籍プロジェクトのルートが存在しません。")]})

          (not (.isDirectory project-stat))
          (js/Promise.resolve
           {:ok? false
            :config nil
            :diagnostics [(diagnostic config-path
                                      "書籍プロジェクトのルートにはディレクトリを指定してください。")]})

          (nil? config-stat)
          (js/Promise.resolve
           {:ok? false
            :config nil
            :diagnostics [(diagnostic config-path
                                      "書籍プロジェクトのルート直下に`clono.config.mjs`がありません。")]})

          (or (.isSymbolicLink config-stat) (not (.isFile config-stat)))
          (js/Promise.resolve
           {:ok? false
            :config nil
            :diagnostics [(diagnostic config-path
                                      "`clono.config.mjs`には通常ファイルを指定してください。")]})

          :else
          (-> (import-config-module (.-href (pathToFileURL config-path)))
              (.then (fn [module]
                       {:module module})
                     (fn [error]
                       {:import-error error}))
              (.then (fn [{:keys [module import-error]}]
                       (if import-error
                         (import-error-result config-path import-error)
                         (try
                           (validate-config project-root
                                            config-path
                                            (gobj/get module "default"))
                           (catch :default error
                             (validation-error-result config-path error)))))))))
      (catch :default error
        (js/Promise.resolve
         {:ok? false
          :config nil
          :diagnostics
          [(diagnostic config-path
                       (str "書籍プロジェクトの設定を確認できません: "
                            (error-message error)))]})))))
