(ns clono.research.generated-index-test
  (:require
   ["@vivliostyle/vfm" :refer [stringify]]
   ["node:fs" :as fs]
   ["node:path" :as path]
   ["node-html-parser" :refer [parse]]
   [cljs.test :refer [deftest is testing]]
   [clojure.string :as string]
   [clono.research.generated-index :as generated-index]))

(def fixture-directory (.cwd js/process))

(def source-paths
  #{"preface.md"
    "chapter-one.md"
    "nested/chapter-two.md"
    "appendix #notes.md"
    "afterword.md"
    "unlisted.md"})

(defn exception-data [operation]
  (try
    (operation)
    nil
    (catch :default error
      (ex-data error))))

(defn query-all [root selector]
  (array-seq (.querySelectorAll root selector)))

(defn rendered-document [markdown]
  (parse (stringify markdown #js {:partial true})))

(defn decode-url-path [value]
  (->> (string/split value #"/")
       (map js/decodeURIComponent)
       (string/join "/")))

(def build-result
  (delay (generated-index/build-project! fixture-directory)))

(deftest generated-index-config-test
  (let [config (generated-index/load-config fixture-directory)
        index (generated-index/index-entry config)]
    (testing "When the fixture configuration is loaded, then one generated index appears between numbered documents and backmatter"
      (is (= "manuscripts" (:sourceRoot config)))
      (is (= "build/manuscripts" (:outputRoot config)))
      (is (= {:type "index"
              :path "generated/index.md"
              :title "索引"
              :includeInToc true}
             index))
      (is (= ["preface.md"
              "chapter-one.md"
              "nested/chapter-two.md"
              "appendix #notes.md"
              "generated/index.md"
              "afterword.md"]
             (mapv :path (:publication config))))
      (is (= config (generated-index/validate-config config source-paths))))

    (testing "When generated index entries are duplicated or collide with input, then configuration validation rejects them"
      (is (= :multiple-index-entries
             (:code
              (exception-data
               #(generated-index/validate-config
                 (update config :publication conj index)
                 source-paths)))))
      (is (= :index-path-collision
             (:code
              (exception-data
               #(generated-index/validate-config
                 config
                 (conj source-paths "generated/index.md")))))))

    (testing "When a numbered document follows the generated index, then configuration validation rejects the order"
      (let [invalid-config
            (assoc config
                   :publication
                   [index
                    {:type "document"
                     :path "chapter-one.md"
                     :kind "chapter"
                     :includeInToc true}])]
        (is (= :numbered-document-after-index
               (:code
                (exception-data
                 #(generated-index/validate-config invalid-config source-paths)))))))))

(deftest generated-index-failure-contract-test
  (let [without-index
        {:publication
         [{:type "document"
           :path "chapter-one.md"
           :kind "chapter"
           :includeInToc true}]}]
    (testing "When index markers exist without a generated index entry, then project transformation rejects the incomplete publication"
      (is (= :index-entry-required
             (:code
              (exception-data
               #(generated-index/transform-project
                 without-index
                 source-paths
                 {"chapter-one.md" ":index[索引]{reading=\"さくいん\"}"}))))))

    (testing "When the same term has conflicting normalized readings, then project transformation rejects the ambiguous index"
      (let [minimal-config
            {:publication
             [{:type "document"
               :path "chapter-one.md"
               :kind "chapter"
               :includeInToc true}
              {:type "index"
               :path "index.md"
               :title "索引"
               :includeInToc true}]}
            data (exception-data
                  #(generated-index/transform-project
                    minimal-config
                    #{"chapter-one.md"}
                    {"chapter-one.md"
                     (str ":index[橋]{reading=\"はし\"}と"
                          ":index[橋]{reading=\"ばし\"}")}))]
        (is (= :term-reading-conflict (:code data)))))

    (testing "When no markers exist, then an explicitly configured generated index remains a valid empty document"
      (let [minimal-config
            {:publication
             [{:type "document"
               :path "chapter-one.md"
               :kind "chapter"
               :includeInToc true}
              {:type "index"
               :path "index.md"
               :title "索引"
               :includeInToc true}]}
            result (generated-index/transform-project
                    minimal-config
                    #{"chapter-one.md"}
                    {"chapter-one.md" "# 本文\n"})]
        (is (= "# 索引\n\n" (:index-markdown result)))
        (is (empty? (get-in result [:index-data :groups])))))))

(deftest generated-index-output-test
  (let [{:keys [config documents index-data index-markdown
                source-directory output-directory]} @build-result
        document-html
        (into {} (map (fn [[document-path markdown]]
                        [(string/replace document-path #"(?i)\.md$" ".html")
                         (rendered-document markdown)])
                      documents))
        index-html (rendered-document index-markdown)
        expected-markers
        [{:document "chapter-one.html" :id "clono-index-marker-1" :term "Android"}
         {:document "chapter-one.html" :id "clono-index-marker-2" :term "API"}
         {:document "chapter-one.html" :id "clono-index-marker-3" :term "一気"}
         {:document "chapter-one.html" :id "clono-index-marker-4" :term "Android"}
         {:document "nested/chapter-two.html" :id "clono-index-marker-5" :term "五木"}
         {:document "nested/chapter-two.html" :id "clono-index-marker-6" :term "画像"}
         {:document "nested/chapter-two.html" :id "clono-index-marker-7" :term "コラム"}
         {:document "nested/chapter-two.html" :id "clono-index-marker-8" :term "バックナンバー"}
         {:document "nested/chapter-two.html" :id "clono-index-marker-9" :term "Android"}
         {:document "appendix #notes.html" :id "clono-index-marker-10" :term "API"}]
        expected-entries
        [{:term "Android"
          :hrefs ["../chapter-one.html#clono-index-marker-1"
                  "../chapter-one.html#clono-index-marker-4"
                  "../nested/chapter-two.html#clono-index-marker-9"]}
         {:term "API"
          :hrefs ["../chapter-one.html#clono-index-marker-2"
                  "../appendix%20%23notes.html#clono-index-marker-10"]}
         {:term "一気"
          :hrefs ["../chapter-one.html#clono-index-marker-3"]}
         {:term "五木"
          :hrefs ["../nested/chapter-two.html#clono-index-marker-5"]}
         {:term "画像"
          :hrefs ["../nested/chapter-two.html#clono-index-marker-6"]}
         {:term "コラム"
          :hrefs ["../nested/chapter-two.html#clono-index-marker-7"]}
         {:term "バックナンバー"
          :hrefs ["../nested/chapter-two.html#clono-index-marker-8"]}]]
    (testing "When publication Markdown is transformed, then every index marker displays only its term and receives a source-order identifier"
      (is (= 10 (count expected-markers)))
      (doseq [{:keys [document id term]} expected-markers]
        (let [marker (.querySelector (get document-html document) (str "#" id))]
          (is (some? marker))
          (is (= "clono-index-marker" (.getAttribute marker "class")))
          (is (= term (.-textContent marker)))))
      (is (every? #(not (string/includes? % ":index["))
                  (vals documents))))

    (testing "When index entries are generated, then fixed groups and collision tie breakers determine their order"
      (is (= [:alphanumeric :a :ka :ha]
             (mapv :id (:groups index-data))))
      (is (= ["Android" "API" "一気" "五木" "画像" "コラム" "バックナンバー"]
             (mapv #(.-textContent %)
                   (query-all index-html ".clono-index-entry > dt")))))

    (testing "When generated occurrence links are inspected, then every complete relative URL identifies its expected document and marker"
      (let [entries (vec (query-all index-html ".clono-index-entry"))]
        (is (= (count expected-entries) (count entries)))
        (doseq [[entry expected] (map vector entries expected-entries)]
          (let [term (.-textContent (.querySelector entry "dt"))
                hrefs (mapv #(.getAttribute % "href")
                            (query-all entry "a.clono-index-page"))]
            (is (= (:term expected) term))
            (is (= (:hrefs expected) hrefs))
            (doseq [href hrefs]
              (let [[relative-html target-id] (string/split href #"#")
                    target-path (->> relative-html
                                     decode-url-path
                                     (.join (.-posix path) "generated")
                                     (.normalize (.-posix path)))
                    target-document (get document-html target-path)]
                (is (some? target-document))
                (is (some? (.querySelector target-document
                                            (str "#" target-id))))))))))

    (testing "When the generated manuscript tree is inspected, then the index is generated without replacing the source tree or unpublished files"
      (let [index-entry (generated-index/index-entry config)
            output-index-path (.join path output-directory (:path index-entry))
            source-index-path (.join path source-directory (:path index-entry))]
        (is (.existsSync fs output-index-path))
        (is (not (.existsSync fs source-index-path)))
        (is (= index-markdown (.readFileSync fs output-index-path "utf8")))
        (is (= (.readFileSync fs (.join path source-directory "unlisted.md") "utf8")
               (.readFileSync fs (.join path output-directory "unlisted.md") "utf8")))))))

(deftest generated-index-escaping-test
  (let [config {:publication
                [{:type "document"
                  :path "chapter.md"
                  :kind "chapter"
                  :includeInToc true}
                 {:type "index"
                  :path "index.md"
                  :title "索引 *draft*"
                  :includeInToc true}]}
        result (generated-index/transform-project
                config
                #{"chapter.md"}
                {"chapter.md" ":index[A & B]{reading=\"a b\"}"})]
    (testing "When an index term contains HTML-sensitive text, then generated markers and index entries preserve the displayed term without creating markup"
      (is (string/includes? (get-in result [:documents "chapter.md"])
                            ">A &amp; B</span>"))
      (is (string/includes? (:index-markdown result)
                            "<dt>A &amp; B</dt>"))
      (is (= "索引 *draft*"
             (.-textContent
              (.querySelector
               (rendered-document (:index-markdown result))
               "h1"))))
      (is (= "A & B"
             (.-textContent
              (.querySelector
               (rendered-document (:index-markdown result))
               ".clono-index-entry > dt")))))))
