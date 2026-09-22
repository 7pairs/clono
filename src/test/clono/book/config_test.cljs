(ns clono.book.config-test
  (:require
   ["node:fs" :as fs]
   ["node:os" :as os]
   ["node:path" :as path]
   ["node:worker_threads" :refer [Worker]]
   [cljs.test :refer [async deftest is testing]]
   [clono.book.config :as config]))

(defn- write-file! [file-path content]
  (.mkdirSync fs (.dirname path file-path) #js {:recursive true})
  (.writeFileSync fs file-path content "utf8"))

(def ^:private import-worker-source
  (str "const { parentPort, workerData } = require('node:worker_threads');\n"
       "import(workerData.specifier)\n"
       "  .then((module) => parentPort.postMessage({ ok: true, value: module.default }))\n"
       "  .catch((error) => parentPort.postMessage({ ok: false, message: error.message }));\n"))

(defn- import-config-module [specifier]
  (js/Promise.
   (fn [resolve reject]
     (let [worker (Worker. import-worker-source
                           #js {:eval true
                                :workerData #js {:specifier specifier}})]
       (.once worker "message"
              (fn [message]
                (if (.-ok message)
                  (resolve #js {:default (.-value message)})
                  (reject (js/Error. (.-message message))))))
       (.once worker "error" reject)))))

(defn- with-project
  ([setup assertion done]
   (with-project setup import-config-module assertion done))
  ([setup importer assertion done]
   (let [project (.mkdtempSync fs (.join path (.tmpdir os) "clono-config-test-"))]
     (try
       (let [load-path (or (setup project) project)
             result-promise (with-redefs [config/import-config-module importer]
                              (config/load-project-config load-path))]
         (-> result-promise
             (.then assertion)
             (.catch (fn [error]
                       (is false (str "Unexpected rejected promise: " (.-message error)))))
             (.finally (fn []
                         (.rmSync fs project #js {:recursive true :force true})
                         (done)))))
       (catch :default error
         (.rmSync fs project #js {:recursive true :force true})
         (is false (str "Unexpected setup error: " (.-message error)))
         (done))))))

(defn- messages [result]
  (mapv :message (:diagnostics result)))

(deftest valid-configuration-test
  (async done
    (testing "When a valid ESM configuration uses top-level await, then a normalized book configuration is loaded"
      (with-project
        (fn [project]
          (write-file! (.join path project "manuscripts" "chapter.md") "# 本文\n")
          (write-file! (.join path project "manuscripts" "colophon.html") "<p>奥付</p>\n")
          (write-file!
           (.join path project "clono.config.mjs")
           (str "const sourceRoot = await Promise.resolve('manuscripts/./');\n"
                "export default {\n"
                "  sourceRoot,\n"
                "  outputRoot: 'build/../build/manuscripts',\n"
                "  publication: [\n"
                "    { type: 'document', path: './chapter.md', kind: 'chapter', includeInToc: true },\n"
                "    { type: 'blank-page' },\n"
                "    { type: 'index', path: './generated/index.MD', title: '索引', includeInToc: true },\n"
                "    { type: 'document', path: 'colophon.html', kind: 'backmatter', includeInToc: false },\n"
                "  ],\n"
                "  plugins: [],\n"
                "};\n")))
        (fn [result]
          (is (:ok? result))
          (is (empty? (:diagnostics result)))
          (let [{:keys [project-root source-root source-path output-root output-path plugins publication]}
                (:config result)]
            (is (.isAbsolute path project-root))
            (is (= "manuscripts" source-root))
            (is (= (.join path project-root "manuscripts") source-path))
            (is (= "build/manuscripts" output-root))
            (is (= (.join path project-root "build" "manuscripts") output-path))
            (is (empty? plugins))
            (is (= [{:type :document
                     :path "chapter.md"
                     :kind "chapter"
                     :include-in-toc true}
                    {:type :blank-page}
                    {:type :index
                     :path "generated/index.MD"
                     :title "索引"
                     :include-in-toc true}
                    {:type :document
                     :path "colophon.html"
                     :kind "backmatter"
                     :include-in-toc false}]
                   (mapv #(dissoc % :file-path) publication)))
            (is (every? #(.isAbsolute path (:file-path %))
                        (filter #(= :document (:type %)) publication)))))
        done))))

(deftest valid-plugin-configuration-test
  (async done
    (testing "When local plugin paths are valid, then normalized plugin entries are loaded in configuration order"
      (with-project
        (fn [project]
          (write-file! (.join path project "manuscripts" "chapter.md") "# 本文\n")
          (write-file! (.join path project "plugins" "first plugin.mjs")
                       "export default {};\n")
          (write-file! (.join path project "plugins" "second#plugin.mjs")
                       "export default {};\n")
          (write-file!
           (.join path project "clono.config.mjs")
           (str "export default {\n"
                "  sourceRoot: 'manuscripts',\n"
                "  outputRoot: 'build/manuscripts',\n"
                "  publication: [\n"
                "    { type: 'document', path: 'chapter.md', kind: 'chapter', includeInToc: true },\n"
                "  ],\n"
                "  plugins: [\n"
                "    './plugins/first plugin.mjs',\n"
                "    './plugins/nested/../second#plugin.mjs',\n"
                "  ],\n"
                "};\n")))
        (fn [result]
          (is (:ok? result))
          (is (empty? (:diagnostics result)))
          (let [project-root (get-in result [:config :project-root])]
            (is (= [{:specifier "./plugins/first plugin.mjs"
                     :path "plugins/first plugin.mjs"
                     :file-path (.join path project-root "plugins" "first plugin.mjs")}
                    {:specifier "./plugins/nested/../second#plugin.mjs"
                     :path "plugins/second#plugin.mjs"
                     :file-path (.join path project-root "plugins" "second#plugin.mjs")}]
                   (get-in result [:config :plugins])))))
        done))))

(deftest plugin-structure-validation-test
  (async done
    (testing "When plugin settings violate the schema, then every independent path diagnostic is returned"
      (with-project
        (fn [project]
          (write-file! (.join path project "manuscripts" "chapter.md") "# 本文\n")
          (write-file!
           (.join path project "clono.config.mjs")
           (str "export default {\n"
                "  sourceRoot: 'manuscripts',\n"
                "  outputRoot: 'build/manuscripts',\n"
                "  publication: [\n"
                "    { type: 'document', path: 'chapter.md', kind: 'chapter', includeInToc: true },\n"
                "  ],\n"
                "  plugins: [42, '', 'plugin.mjs', './plugins\\\\bad.mjs',\n"
                "            './../outside.mjs', './plugins/*.mjs', './plugins/plugin.js',\n"
                "            './C:/outside.mjs', './C:outside.mjs'],\n"
                "};\n")))
        (fn [result]
          (is (false? (:ok? result)))
          (is (nil? (:config result)))
          (is (= #{"`plugins[0]`には文字列を指定してください。"
                   "`plugins[1]`に空文字列は指定できません。"
                   "`plugins[2]`には`./`で始まるローカルパスを指定してください。"
                   "`plugins[3]`の区切り文字には`/`を使用してください。"
                   "`plugins[4]`にプロジェクトルートの外側へ出るパスは指定できません。"
                   "`plugins[5]`にglobパターンは指定できません。"
                   "`plugins[6]`には`.mjs`ファイルを指定してください。"
                   "`plugins[7]`にはWindowsドライブ接頭辞を含むパスを指定できません。"
                   "`plugins[8]`にはWindowsドライブ接頭辞を含むパスを指定できません。"}
                 (set (messages result)))))
        done))))

(deftest plugin-array-validation-test
  (async done
    (testing "When plugins is not an array, then the configuration is rejected"
      (with-project
        (fn [project]
          (write-file! (.join path project "manuscripts" "chapter.md") "# 本文\n")
          (write-file!
           (.join path project "clono.config.mjs")
           (str "export default {\n"
                "  sourceRoot: 'manuscripts',\n"
                "  outputRoot: 'build/manuscripts',\n"
                "  publication: [\n"
                "    { type: 'document', path: 'chapter.md', kind: 'chapter', includeInToc: true },\n"
                "  ],\n"
                "  plugins: './plugins/plugin.mjs',\n"
                "};\n")))
        (fn [result]
          (is (false? (:ok? result)))
          (is (= ["`plugins`には配列を指定してください。"]
                 (messages result))))
        done))))

(deftest plugin-file-validation-test
  (async done
    (testing "When plugin paths are duplicated, missing, non-files, or symbolic links, then each problem is diagnosed"
      (with-project
        (fn [project]
          (write-file! (.join path project "manuscripts" "chapter.md") "# 本文\n")
          (write-file! (.join path project "plugins" "valid.mjs")
                       "export default {};\n")
          (.mkdirSync fs (.join path project "plugins" "directory.mjs"))
          (let [link-target (.join path project "plugin-link-target")
                link-path (.join path project "plugin-link")]
            (.mkdirSync fs link-target)
            (write-file! (.join path link-target "linked.mjs") "export default {};\n")
            (.symlinkSync fs
                          link-target
                          link-path
                          (if (= "win32" (.-platform js/process)) "junction" "dir")))
          (write-file!
           (.join path project "clono.config.mjs")
           (str "export default {\n"
                "  sourceRoot: 'manuscripts',\n"
                "  outputRoot: 'build/manuscripts',\n"
                "  publication: [\n"
                "    { type: 'document', path: 'chapter.md', kind: 'chapter', includeInToc: true },\n"
                "  ],\n"
                "  plugins: [\n"
                "    './plugins/valid.mjs',\n"
                "    './plugins/./valid.mjs',\n"
                "    './plugins/missing.mjs',\n"
                "    './plugins/directory.mjs',\n"
                "    './plugin-link/linked.mjs',\n"
                "  ],\n"
                "};\n")))
        (fn [result]
          (is (false? (:ok? result)))
          (is (nil? (:config result)))
          (is (= #{"`plugins`に同じプラグインパスが重複しています: ./plugins/./valid.mjs"
                   "`plugins[2]`のプラグインが存在しません: ./plugins/missing.mjs"
                   "`plugins[3]`には通常ファイルを指定してください: ./plugins/directory.mjs"
                   "`plugins[4]`はシンボリックリンクを経由できません: ./plugin-link/linked.mjs"}
                 (set (messages result)))))
        done))))

(deftest configuration-location-test
  (async done
    (testing "When only an ancestor contains clono.config.mjs, then the project configuration is reported as missing"
      (with-project
        (fn [project]
          (write-file! (.join path project "clono.config.mjs") "export default {};\n")
          (let [child (.join path project "nested")]
            (.mkdirSync fs child)
            child))
        (fn [result]
          (is (false? (:ok? result)))
          (is (= ["書籍プロジェクトのルート直下に`clono.config.mjs`がありません。"]
                 (messages result))))
        done))))

(deftest configuration-structure-validation-test
  (async done
    (testing "When configuration fields violate the schema, then all structural diagnostics are returned without a config"
      (with-project
        (fn [project]
          (write-file!
           (.join path project "clono.config.mjs")
           (str "export default {\n"
                "  sourceRoot: 42,\n"
                "  publication: [\n"
                "    { type: 'document', path: 'chapter.txt', kind: 'unknown', includeInToc: 'yes', extra: true },\n"
                "    null,\n"
                "  ],\n"
                "  extra: true,\n"
                "};\n")))
        (fn [result]
          (is (false? (:ok? result)))
          (is (nil? (:config result)))
          (let [actual (set (messages result))]
            (is (contains? actual "設定に未知の設定項目`extra`があります。"))
            (is (contains? actual "設定に必須の設定項目`outputRoot`がありません。"))
            (is (contains? actual "`sourceRoot`には文字列を指定してください。"))
            (is (contains? actual "`publication[0]`に未知の設定項目`extra`があります。"))
            (is (contains? actual "`publication[0].path`には`.md`または`.html`のファイルを指定してください。"))
            (is (contains? actual "`publication[0].kind`には`frontmatter`、`chapter`、`appendix`または`backmatter`を指定してください。"))
            (is (contains? actual "`publication[0].includeInToc`には真偽値を指定してください。"))
            (is (contains? actual "`publication[1]`にはオブジェクトを指定してください。"))))
        done))))

(deftest publication-type-validation-test
  (async done
    (testing "When publication entry types are missing or invalid, then discriminated entry diagnostics are returned"
      (with-project
        (fn [project]
          (write-file! (.join path project "manuscripts" "chapter.md") "# 本文\n")
          (write-file!
           (.join path project "clono.config.mjs")
           (str "export default {\n"
                "  sourceRoot: 'manuscripts',\n"
                "  outputRoot: 'build/manuscripts',\n"
                "  publication: [\n"
                "    { path: 'chapter.md', kind: 'chapter', includeInToc: true },\n"
                "    { type: 'blank-page', path: 'chapter.md' },\n"
                "    { type: 'unknown', extra: true },\n"
                "  ],\n"
                "};\n")))
        (fn [result]
          (is (false? (:ok? result)))
          (is (= #{"`publication[0]`に必須の設定項目`type`がありません。"
                   "`publication[1]`に未知の設定項目`path`があります。"
                   "`publication[2]`に未知の設定項目`extra`があります。"
                   "`publication[2].type`には`document`、`blank-page`または`index`を指定してください。"}
                 (set (messages result)))))
        done))))

(deftest index-entry-validation-test
  (async done
    (testing "When generated index fields violate the schema, then all index diagnostics are returned"
      (with-project
        (fn [project]
          (write-file! (.join path project "manuscripts" "chapter.md") "# 本文\n")
          (write-file!
           (.join path project "clono.config.mjs")
           (str "export default {\n"
                "  sourceRoot: 'manuscripts',\n"
                "  outputRoot: 'build/manuscripts',\n"
                "  publication: [\n"
                "    { type: 'document', path: 'chapter.md', kind: 'chapter', includeInToc: true },\n"
                "    { type: 'index', path: '_clono/index.html/', title: '   ', includeInToc: 'yes', kind: 'backmatter' },\n"
                "    { type: 'index' },\n"
                "  ],\n"
                "};\n")))
        (fn [result]
          (is (false? (:ok? result)))
          (is (nil? (:config result)))
          (is (= #{"`publication[1]`に未知の設定項目`kind`があります。"
                   "`publication[1].path`には`.md`のファイルを指定してください。"
                   "`publication[1].path`にはディレクトリではなくファイルパスを指定してください。"
                   "`publication[1].path`にclonoの予約領域`_clono/`は指定できません。"
                   "`publication[1].title`には空でない文字列を指定してください。"
                   "`publication[1].includeInToc`には真偽値を指定してください。"
                   "`publication[2]`に必須の設定項目`includeInToc`がありません。"
                   "`publication[2]`に必須の設定項目`path`がありません。"
                   "`publication[2]`に必須の設定項目`title`がありません。"}
                 (set (messages result)))))
        done))))

(deftest index-count-validation-test
  (async done
    (testing "When publication contains multiple generated indexes, then the configuration is rejected"
      (with-project
        (fn [project]
          (write-file! (.join path project "manuscripts" "chapter.md") "# 本文\n")
          (write-file!
           (.join path project "clono.config.mjs")
           (str "export default {\n"
                "  sourceRoot: 'manuscripts',\n"
                "  outputRoot: 'build/manuscripts',\n"
                "  publication: [\n"
                "    { type: 'document', path: 'chapter.md', kind: 'chapter', includeInToc: true },\n"
                "    { type: 'index', path: 'generated/subject.md', title: '事項索引', includeInToc: true },\n"
                "    { type: 'index', path: 'generated/name.md', title: '人名索引', includeInToc: true },\n"
                "  ],\n"
                "};\n")))
        (fn [result]
          (is (false? (:ok? result)))
          (is (= ["`publication`に`index`は一件だけ指定できます。"]
                 (messages result))))
        done))))

(deftest index-order-validation-test
  (async done
    (testing "When a generated index is outside the numbered-document and backmatter boundary, then both order violations are diagnosed"
      (with-project
        (fn [project]
          (write-file! (.join path project "manuscripts" "colophon.html") "<p>奥付</p>\n")
          (write-file! (.join path project "manuscripts" "chapter.md") "# 本文\n")
          (write-file!
           (.join path project "clono.config.mjs")
           (str "export default {\n"
                "  sourceRoot: 'manuscripts',\n"
                "  outputRoot: 'build/manuscripts',\n"
                "  publication: [\n"
                "    { type: 'document', path: 'colophon.html', kind: 'backmatter', includeInToc: false },\n"
                "    { type: 'blank-page' },\n"
                "    { type: 'index', path: 'generated/index.md', title: '索引', includeInToc: true },\n"
                "    { type: 'blank-page' },\n"
                "    { type: 'document', path: 'chapter.md', kind: 'chapter', includeInToc: true },\n"
                "  ],\n"
                "};\n")))
        (fn [result]
          (is (false? (:ok? result)))
          (is (= ["`publication`の`index`はすべての`backmatter`より前に配置してください。"
                  "`publication`の`index`はすべての`chapter`および`appendix`より後に配置してください。"]
                 (messages result))))
        done))))

(deftest document-required-test
  (async done
    (testing "When publication contains only blank pages, then the configuration is rejected"
      (with-project
        (fn [project]
          (.mkdirSync fs (.join path project "manuscripts"))
          (write-file!
           (.join path project "clono.config.mjs")
           (str "export default {\n"
                "  sourceRoot: 'manuscripts',\n"
                "  outputRoot: 'build/manuscripts',\n"
                "  publication: [\n"
                "    { type: 'blank-page' },\n"
                "    { type: 'blank-page' },\n"
                "  ],\n"
                "};\n")))
        (fn [result]
          (is (false? (:ok? result)))
          (is (= ["`publication`には一件以上の`document`を指定してください。"]
                 (messages result))))
        done))))

(deftest root-path-validation-test
  (async done
    (testing "When sourceRoot and outputRoot overlap, then the configuration is rejected"
      (with-project
        (fn [project]
          (write-file! (.join path project "manuscripts" "chapter.md") "# 本文\n")
          (write-file!
           (.join path project "clono.config.mjs")
           (str "export default {\n"
                "  sourceRoot: 'manuscripts',\n"
                "  outputRoot: 'manuscripts/build',\n"
                "  publication: [\n"
                "    { type: 'document', path: 'chapter.md', kind: 'chapter', includeInToc: true },\n"
                "  ],\n"
                "};\n")))
        (fn [result]
          (is (false? (:ok? result)))
          (is (= ["`sourceRoot`と`outputRoot`には同じパスまたは祖先・子孫関係にあるパスを指定できません。"]
                 (messages result))))
        done))))

(deftest portable-path-validation-test
  (async done
    (testing "When configured paths are unsafe or publication is empty, then portable path diagnostics are returned"
      (with-project
        (fn [project]
          (write-file!
           (.join path project "clono.config.mjs")
           (str "export default {\n"
                "  sourceRoot: '../manuscripts',\n"
                "  outputRoot: 'C:/generated',\n"
                "  publication: [],\n"
                "};\n")))
        (fn [result]
          (is (false? (:ok? result)))
          (is (= #{"`sourceRoot`に基準ディレクトリの外側へ出るパスは指定できません。"
                   "`outputRoot`に絶対パスは指定できません。"
                   "`publication`には一件以上の`document`を指定してください。"}
                 (set (messages result)))))
        done))))

(deftest root-symlink-validation-test
  (async done
    (testing "When outputRoot traverses a symbolic link, then the configuration is rejected"
      (with-project
        (fn [project]
          (write-file! (.join path project "manuscripts" "chapter.md") "# 本文\n")
          (.mkdirSync fs (.join path project "generated-target"))
          (.symlinkSync fs
                        (.join path project "generated-target")
                        (.join path project "generated-link")
                        (if (= "win32" (.-platform js/process)) "junction" "dir"))
          (write-file!
           (.join path project "clono.config.mjs")
           (str "export default {\n"
                "  sourceRoot: 'manuscripts',\n"
                "  outputRoot: 'generated-link/manuscripts',\n"
                "  publication: [\n"
                "    { type: 'document', path: 'chapter.md', kind: 'chapter', includeInToc: true },\n"
                "  ],\n"
                "};\n")))
        (fn [result]
          (is (false? (:ok? result)))
          (is (= 1 (count (:diagnostics result))))
          (is (.startsWith (first (messages result))
                           "`outputRoot`はシンボリックリンクを経由できません:")))
        done))))

(deftest publication-file-validation-test
  (async done
    (testing "When publication paths are duplicated, missing, non-files, or symbolic links, then each problem is diagnosed"
      (with-project
        (fn [project]
          (let [source (.join path project "manuscripts")]
            (write-file! (.join path source "chapter.md") "# 本文\n")
            (.mkdirSync fs (.join path source "directory.md"))
            (.mkdirSync fs (.join path source "link-target"))
            (.symlinkSync fs
                          (.join path source "link-target")
                          (.join path source "linked.md")
                          (if (= "win32" (.-platform js/process)) "junction" "dir"))
            (write-file!
             (.join path project "clono.config.mjs")
             (str "export default {\n"
                  "  sourceRoot: 'manuscripts',\n"
                  "  outputRoot: 'build/manuscripts',\n"
                  "  publication: [\n"
                  "    { type: 'document', path: 'chapter.md', kind: 'chapter', includeInToc: true },\n"
                  "    { type: 'document', path: './chapter.md', kind: 'chapter', includeInToc: true },\n"
                  "    { type: 'document', path: 'missing.md', kind: 'chapter', includeInToc: true },\n"
                  "    { type: 'document', path: 'directory.md', kind: 'chapter', includeInToc: true },\n"
                  "    { type: 'document', path: 'linked.md', kind: 'chapter', includeInToc: true },\n"
                  "  ],\n"
                  "};\n"))))
        (fn [result]
          (is (false? (:ok? result)))
          (let [actual (set (messages result))]
            (is (contains? actual "`publication`に同じ原稿パスが重複しています: chapter.md"))
            (is (contains? actual "`publication`の原稿が存在しません: missing.md"))
            (is (contains? actual "`publication`には通常ファイルを指定してください: directory.md"))
            (is (contains? actual "`publication`の原稿はシンボリックリンクを経由できません: linked.md"))))
        done))))

(deftest module-evaluation-test
  (async done
    (testing "When clono.config.mjs cannot be evaluated, then a diagnostic is returned without a JavaScript stack trace"
      (with-project
        (fn [project]
          (write-file! (.join path project "clono.config.mjs")
                       "throw new Error('configuration exploded');\n"))
        (fn [result]
          (is (false? (:ok? result)))
          (is (nil? (:config result)))
          (is (= 1 (count (:diagnostics result))))
          (is (.includes (:message (first (:diagnostics result)))
                         "configuration exploded"))
          (is (not (.includes (:message (first (:diagnostics result))) "    at "))))
        done))))

(deftest unexpected-validation-error-test
  (async done
    (testing "When configuration validation throws unexpectedly, then it is distinguished from an import failure"
      (let [invalid-config
            (js/Proxy. #js {}
                       #js {:ownKeys
                            (fn []
                              (throw (js/Error. "validation exploded")))})]
        (with-project
          (fn [project]
            (write-file! (.join path project "clono.config.mjs")
                         "export default {};\n"))
          (fn [_]
            (js/Promise.resolve #js {:default invalid-config}))
          (fn [result]
            (is (false? (:ok? result)))
            (is (nil? (:config result)))
            (is (= 1 (count (:diagnostics result))))
            (is (.startsWith (:message (first (:diagnostics result)))
                             "`clono.config.mjs`を検証できません:"))
            (is (.includes (:message (first (:diagnostics result)))
                           "validation exploded")))
          done)))))
