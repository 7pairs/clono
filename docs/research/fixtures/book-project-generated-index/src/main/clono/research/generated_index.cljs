(ns clono.research.generated-index
  (:require
   ["mdast-util-directive" :refer [directiveFromMarkdown directiveToMarkdown]]
   ["mdast-util-from-markdown" :refer [fromMarkdown]]
   ["mdast-util-to-markdown" :refer [toMarkdown]]
   ["micromark-extension-directive" :refer [directive]]
   ["node:child_process" :as child-process]
   ["node:fs" :as fs]
   ["node:path" :as path]
   [clojure.string :as string]
   [clono.research.index-normalization :as index-normalization]
   [goog.object :as gobj]
   [goog.string :as gstring]))

(def index-entry-keys
  #{:type :path :title :includeInToc})

(defn parse [source]
  (fromMarkdown
   source
   #js {:extensions #js [(directive)]
        :mdastExtensions #js [(directiveFromMarkdown)]}))

(defn serialize [tree]
  (toMarkdown tree #js {:extensions #js [(directiveToMarkdown)]}))

(defn- children [node]
  (when (js/Array.isArray (.-children node))
    (array-seq (.-children node))))

(defn- nodes [tree]
  (tree-seq #(some? (children %)) children tree))

(defn- fail! [code message & [data]]
  (throw (ex-info message (assoc (or data {}) :code code))))

(defn- markdown-path? [value]
  (= ".md" (string/lower-case (.extname (.-posix path) value))))

(defn- safe-relative-path? [value]
  (and (string? value)
       (not (string/blank? value))
       (not (.isAbsolute (.-posix path) value))
       (not= ".." (.normalize (.-posix path) value))
       (not (string/starts-with? (.normalize (.-posix path) value) "../"))))

(defn- same-or-ancestor-path? [ancestor descendant]
  (let [posix-path (.-posix path)
        relative (.relative posix-path
                            (.normalize posix-path ancestor)
                            (.normalize posix-path descendant))]
    (or (string/blank? relative)
        (and (not (.isAbsolute posix-path relative))
             (not= ".." relative)
             (not (string/starts-with? relative "../"))))))

(defn- related-paths? [left right]
  (or (same-or-ancestor-path? left right)
      (same-or-ancestor-path? right left)))

(defn index-entry [config]
  (first (filter #(= "index" (:type %)) (:publication config))))

(defn validate-config [config source-paths]
  (let [publication (:publication config)
        indexes (filterv #(= "index" (:type %)) publication)]
    (when (< 1 (count indexes))
      (fail! :multiple-index-entries
             "Publication must not contain more than one generated index"))
    (when-let [entry (first indexes)]
      (when-not (= index-entry-keys (set (keys entry)))
        (fail! :invalid-index-entry
               "Generated index entry must contain only the required fields"))
      (when-not (and (safe-relative-path? (:path entry))
                     (markdown-path? (:path entry)))
        (fail! :invalid-index-path
               "Generated index path must be a safe relative Markdown path"))
      (when-not (and (string? (:title entry))
                     (not (string/blank? (:title entry))))
        (fail! :invalid-index-title
               "Generated index title must be a non-empty string"))
      (when-not (boolean? (:includeInToc entry))
        (fail! :invalid-index-toc-setting
               "Generated index includeInToc must be a boolean"))
      (when (some #(related-paths? (:path entry) %) source-paths)
        (fail! :index-path-collision
               "Generated index path must not equal, contain, or be contained by an input file path"
               {:path (:path entry)}))
      (let [following (rest (drop-while #(not= entry %) publication))
            numbered-after-index
            (first (filter #(and (= "document" (:type %))
                                 (contains? #{"chapter" "appendix"} (:kind %)))
                           following))]
        (when numbered-after-index
          (fail! :numbered-document-after-index
                 "Chapter and appendix documents must precede the generated index"
                 {:path (:path numbered-after-index)}))))
    config))

(defn- directive-attributes [node]
  (or (.-attributes node) #js {}))

(defn- index-marker? [node]
  (and (= "textDirective" (.-type node))
       (= "index" (.-name node))))

(defn- marker-term [node source-path]
  (let [marker-children (vec (children node))
        term (apply str (map #(.-value %) marker-children))]
    (when (or (empty? marker-children)
              (not-every? #(= "text" (.-type %)) marker-children)
              (string/blank? term))
      (fail! :invalid-index-term
             "Index marker must contain one non-empty plain-text term"
             {:source-path source-path}))
    term))

(defn- marker-reading [node source-path]
  (let [attributes (directive-attributes node)
        names (set (array-seq (js/Object.keys attributes)))
        reading (gobj/get attributes "reading")]
    (when-not (= #{"reading"} names)
      (fail! :invalid-index-attributes
             "Index marker must contain only the reading attribute"
             {:source-path source-path}))
    (when-not (string? reading)
      (fail! :missing-index-reading
             "Index marker must contain a reading"
             {:source-path source-path}))
    reading))

(defn- collect-document [source-path source]
  (let [tree (parse source)
        markers (->> (nodes tree)
                     (filter index-marker?)
                     (mapv (fn [node]
                             {:node node
                              :term (marker-term node source-path)
                              :reading (marker-reading node source-path)
                              :source-path source-path
                              :position (gobj/getValueByKeys
                                         node "position" "start" "offset")})))]
    {:path source-path
     :tree tree
     :markers markers}))

(defn- html-path [markdown-path]
  (let [extension (.extname (.-posix path) markdown-path)]
    (str (.slice markdown-path 0 (- (count markdown-path) (count extension)))
         ".html")))

(defn- url-path [filesystem-path]
  (->> (string/split (string/replace filesystem-path #"\\" "/") #"/")
       (map js/encodeURIComponent)
       (string/join "/")))

(defn- occurrence-href [index-path {:keys [source-path id]}]
  (let [from-directory (.dirname (.-posix path) (html-path index-path))
        target-path (html-path source-path)
        relative-path (.relative (.-posix path) from-directory target-path)]
    (str (url-path relative-path) "#" id)))

(defn- marker-html [term id]
  (str "<span class=\"clono-index-marker\" id=\""
       (gstring/htmlEscape id)
       "\">"
       (gstring/htmlEscape term)
       "</span>"))

(defn- replace-marker! [{:keys [node term id]}]
  (gobj/set node "type" "html")
  (gobj/set node "value" (marker-html term id))
  (js-delete node "name")
  (js-delete node "attributes")
  (js-delete node "children"))

(defn- index-links [index-path term occurrences]
  (->> occurrences
       (map-indexed
        (fn [index occurrence]
          (str "<a class=\"clono-index-page\" href=\""
               (gstring/htmlEscape (occurrence-href index-path occurrence))
               "\" aria-label=\""
               (gstring/htmlEscape
                (str term "の出現" (inc index)))
               "\"></a>")))
       (interpose "<span class=\"clono-index-separator\">,&nbsp;</span>")
       (apply str)))

(defn- index-entry-html [index-path entry]
  (str "<div class=\"clono-index-entry\">\n"
       "<dt>" (gstring/htmlEscape (:term entry)) "</dt>\n"
       "<dd>" (index-links index-path (:term entry) (:occurrences entry)) "</dd>\n"
       "</div>"))

(defn- group-html [index-path {:keys [id title entries]}]
  (str "<section class=\"clono-index-group clono-index-group-"
       (name id)
       "\">\n"
       "<h2>" (gstring/htmlEscape title) "</h2>\n"
       "<dl class=\"clono-index-list\">\n"
       (string/join "\n" (map #(index-entry-html index-path %) entries))
       "\n</dl>\n"
       "</section>"))

(defn- heading-markdown [title]
  (serialize
   #js {:type "root"
        :children
        #js [#js {:type "heading"
                  :depth 1
                  :children #js [#js {:type "text" :value title}]}]}))

(defn- index-markdown [entry groups]
  (str (heading-markdown (:title entry)) "\n"
       (string/join "\n\n" (map #(group-html (:path entry) %) groups))
       (when (seq groups) "\n")))

(defn transform-project [config source-paths markdown-by-path]
  (validate-config config source-paths)
  (let [documents (->> (:publication config)
                       (filter #(and (= "document" (:type %))
                                     (markdown-path? (:path %))))
                       (mapv (fn [{:keys [path]}]
                               (when-not (contains? markdown-by-path path)
                                 (fail! :missing-publication-markdown
                                        "Publication Markdown source is missing"
                                        {:path path}))
                               (collect-document path (get markdown-by-path path)))))
        markers (vec (mapcat :markers documents))
        generated-index (index-entry config)]
    (when (and (seq markers) (nil? generated-index))
      (fail! :index-entry-required
             "Publication must contain a generated index when index markers exist"))
    (let [numbered-markers
          (mapv (fn [index marker]
                  (assoc marker :id (str "clono-index-marker-" (inc index))))
                (range)
                markers)
          marker-by-node (into {} (map (juxt :node identity) numbered-markers))
          index-data (index-normalization/build-index
                      (mapv (fn [marker]
                              {:term (:term marker)
                               :reading (:reading marker)
                               :occurrence (dissoc marker :node)})
                            numbered-markers))]
      (doseq [{:keys [tree]} documents
              node (nodes tree)
              :when (contains? marker-by-node node)]
        (replace-marker! (get marker-by-node node)))
      {:documents (into {} (map (juxt :path #(serialize (:tree %))) documents))
       :index-data index-data
       :index-markdown (when generated-index
                         (index-markdown generated-index (:groups index-data)))})))

(defn- relative-files [directory]
  (letfn [(walk [current relative]
            (mapcat
             (fn [entry]
               (let [entry-relative (if (string/blank? relative)
                                      (.-name entry)
                                      (.join (.-posix path) relative (.-name entry)))
                     entry-path (.join path current (.-name entry))]
                 (cond
                   (.isDirectory entry) (walk entry-path entry-relative)
                   (.isFile entry) [entry-relative]
                   :else (fail! :unsupported-source-entry
                                "Source tree contains an unsupported entry"
                                {:path entry-relative}))))
             (array-seq (.readdirSync fs current #js {:withFileTypes true}))))]
    (set (walk directory ""))))

(defn load-config [fixture-directory]
  (let [result (.spawnSync child-process
                           (.-execPath js/process)
                           #js ["scripts/read-config.mjs"]
                           #js {:cwd fixture-directory
                                :encoding "utf8"
                                :timeout 30000})]
    (when-let [error (.-error result)]
      (throw error))
    (when-not (zero? (.-status result))
      (fail! :config-load-failed (.-stderr result)))
    (js->clj (js/JSON.parse (.-stdout result)) :keywordize-keys true)))

(defn build-project! [fixture-directory]
  (let [project-directory (.join path fixture-directory "project")
        config (load-config fixture-directory)
        source-directory (.resolve path project-directory (:sourceRoot config))
        output-directory (.resolve path project-directory (:outputRoot config))
        source-paths (relative-files source-directory)
        markdown-by-path
        (->> (:publication config)
             (filter #(and (= "document" (:type %))
                           (markdown-path? (:path %))))
             (map (fn [{entry-path :path}]
                    [entry-path
                     (.readFileSync fs
                                    (.join path source-directory entry-path)
                                    "utf8")]))
             (into {}))
        result (transform-project config source-paths markdown-by-path)]
    (.rmSync fs output-directory #js {:recursive true :force true})
    (.mkdirSync fs (.dirname path output-directory) #js {:recursive true})
    (.cpSync fs source-directory output-directory #js {:recursive true})
    (doseq [[document-path output] (:documents result)]
      (.writeFileSync fs (.join path output-directory document-path) output "utf8"))
    (when-let [entry (index-entry config)]
      (let [output-path (.join path output-directory (:path entry))]
        (.mkdirSync fs (.dirname path output-path) #js {:recursive true})
        (.writeFileSync fs output-path (:index-markdown result) "utf8")))
    (assoc result
           :config config
           :source-directory source-directory
           :output-directory output-directory)))
