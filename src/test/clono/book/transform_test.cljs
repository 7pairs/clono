(ns clono.book.transform-test
  (:require
   ["node:fs" :as fs]
   ["node:os" :as os]
   ["node:path" :as path]
   [cljs.test :refer [deftest is testing]]
   [clono.book.plan :as plan]
   [clono.book.transform :as book-transform]
   [clono.pipeline :as pipeline]))

(defn- with-temporary-project [f]
  (let [project (.mkdtempSync fs (.join path (.tmpdir os) "clono-transform-test-"))]
    (try
      (f project)
      (finally
        (.rmSync fs project #js {:recursive true :force true})))))

(defn- write-file! [file-path content]
  (.mkdirSync fs (.dirname path file-path) #js {:recursive true})
  (.writeFileSync fs file-path content "utf8"))

(defn- book-config [project publication]
  {:project-root project
   :config-path (.join path project "clono.config.mjs")
   :source-root "manuscripts"
   :source-path (.join path project "manuscripts")
   :output-root "build/manuscripts"
   :output-path (.join path project "build" "manuscripts")
   :publication publication})

(defn- create-plan [project publication]
  (:plan (plan/create (book-config project publication))))

(deftest multiple-manuscript-transformation-test
  (testing "When a transformation plan contains multiple Markdown manuscripts, then every manuscript is transformed in deterministic plan order without writing output"
    (with-temporary-project
      (fn [project]
        (let [source (.join path project "manuscripts")
              output (.join path project "build" "manuscripts")
              publication [{:type :document
                            :path "chapter.md"
                            :kind "chapter"
                            :include-in-toc true}
                           {:type :document
                            :path "appendix.MD"
                            :kind "appendix"
                            :include-in-toc true}]]
          (write-file! (.join path source "chapter.md")
                       (str ":::align{position=\"right\"}\n本文\n:::\n\n"
                            ":::figure[構成図]{#diagram}\n"
                            "![構成図](./images/diagram.svg)\n"
                            ":::\n"))
          (write-file! (.join path source "appendix.MD")
                       "# 付録\n\n::page-break\n\n続きです。\n")
          (write-file! (.join path source "images" "diagram.svg")
                       "<svg></svg>\n")
          (let [result (book-transform/run (create-plan project publication))
                operations (:operations (:plan result))]
            (is (:ok? result))
            (is (empty? (:diagnostics result)))
            (is (= ["appendix.MD"
                    "chapter.md"
                    "images"
                    "images/diagram.svg"]
                   (mapv :path operations)))
            (is (.includes (:content (nth operations 0))
                           "<div class=\"clono-page-break\" aria-hidden=\"true\"></div>"))
            (is (.includes (:content (nth operations 1))
                           "<div class=\"clono-align-right\">"))
            (is (.includes (:content (nth operations 1))
                           "<figure class=\"clono-numbered-figure\" id=\"figure-diagram\">"))
            (is (not (contains? (nth operations 2) :content)))
            (is (not (contains? (nth operations 3) :content)))
            (is (false? (.existsSync fs output)))))))))

(deftest manuscript-diagnostic-collection-test
  (testing "When multiple manuscripts contain independent problems, then every diagnostic is collected in plan and source order without exposing a partial plan"
    (with-temporary-project
      (fn [project]
        (let [source (.join path project "manuscripts")]
          (write-file! (.join path source "a.md")
                       (str ":first[未知]\n\n"
                            ":::align{position=\"center\"}\n本文\n:::\n"))
          (write-file! (.join path source "b.md")
                       ":second[未知]\n")
          (let [result (book-transform/run
                        (create-plan
                         project
                         [{:type :document
                           :path "a.md"
                           :kind "chapter"
                           :include-in-toc true}
                          {:type :document
                           :path "b.md"
                           :kind "chapter"
                           :include-in-toc true}]))]
            (is (false? (:ok? result)))
            (is (nil? (:plan result)))
            (is (= [{:file "a.md"
                     :line 1
                     :column 1
                     :directive "first"
                     :message "`first`は登録されていないdirectiveです。"}
                    {:file "a.md"
                     :line 3
                     :column 1
                     :directive "align"
                     :message "`align`の`position`属性には`right`を指定する必要があります。"}
                    {:file "b.md"
                     :line 1
                     :column 1
                     :directive "second"
                     :message "`second`は登録されていないdirectiveです。"}]
                   (:diagnostics result)))))))))

(deftest book-specific-figure-validation-test
  (testing "When book manuscripts violate figure kind and image path constraints, then every diagnostic is collected without exposing a partial plan"
    (with-temporary-project
      (fn [project]
        (let [source (.join path project "manuscripts")
              figure-source (fn [id image-path]
                              (str ":::figure[検証用の図]{#" id "}\n"
                                   "![図](" image-path ")\n"
                                   ":::\n"))
              publication [{:type :document
                            :path "chapter.md"
                            :kind "chapter"
                            :include-in-toc true}
                           {:type :document
                            :path "frontmatter.md"
                            :kind "frontmatter"
                            :include-in-toc true}]]
          (write-file! (.join path source "chapter.md")
                       (figure-source "missing" "./images/missing.svg"))
          (write-file! (.join path source "frontmatter.md")
                       (figure-source "front" "./images/existing.svg"))
          (write-file! (.join path source "notes.md")
                       (figure-source "unlisted" "./images/existing.svg"))
          (write-file! (.join path source "images" "existing.svg")
                       "<svg></svg>\n")
          (let [result (book-transform/run (create-plan project publication))]
            (is (false? (:ok? result)))
            (is (nil? (:plan result)))
            (is (= [{:file "chapter.md"
                     :line 2
                     :column 1
                     :directive "figure"
                     :message "`figure`の画像ファイルが存在しません。"}
                    {:file "frontmatter.md"
                     :line 1
                     :column 1
                     :directive "figure"
                     :message "`figure`は本文または付録の掲載Markdownにだけ記述できます。"}]
                   (:diagnostics result)))))))))

(deftest manuscript-read-failure-test
  (testing "When a planned manuscript becomes unreadable, then its failure and later manuscript diagnostics are both returned"
    (with-temporary-project
      (fn [project]
        (let [source (.join path project "manuscripts")
              missing (.join path source "a.md")]
          (write-file! missing "# 削除予定\n")
          (write-file! (.join path source "b.md") ":unknown[未知]\n")
          (let [transformation-plan
                (create-plan
                 project
                 [{:type :document
                   :path "a.md"
                   :kind "chapter"
                   :include-in-toc true}
                  {:type :document
                   :path "b.md"
                   :kind "chapter"
                   :include-in-toc true}])]
            (.unlinkSync fs missing)
            (let [result (book-transform/run transformation-plan)
                  diagnostics (:diagnostics result)]
              (is (false? (:ok? result)))
              (is (nil? (:plan result)))
              (is (= ["a.md" "b.md"] (mapv :file diagnostics)))
              (is (.startsWith (:message (first diagnostics))
                               "Markdown原稿を読み込めません: "))
              (is (= "`unknown`は登録されていないdirectiveです。"
                     (:message (second diagnostics)))))))))))

(deftest unexpected-transformation-failure-test
  (testing "When one manuscript transformation throws unexpectedly, then the failure is diagnosed and later manuscripts are still processed"
    (with-temporary-project
      (fn [project]
        (let [source (.join path project "manuscripts")]
          (write-file! (.join path source "a.md") "# 例外\n")
          (write-file! (.join path source "b.md") "# 継続\n")
          (let [processed (atom [])
                transformation-plan
                (create-plan
                 project
                 [{:type :document
                   :path "a.md"
                   :kind "chapter"
                   :include-in-toc true}
                  {:type :document
                   :path "b.md"
                   :kind "chapter"
                   :include-in-toc true}])]
            (with-redefs [pipeline/run-analyzed
                          (fn [context tree]
                            (let [source-name (:source-name context)]
                              (swap! processed conj source-name)
                              (if (= "a.md" source-name)
                                (throw (js/Error. "unexpected failure"))
                                {:ok? true
                                 :output (str tree)
                                 :diagnostics []})))]
              (let [result (book-transform/run transformation-plan)]
                (is (false? (:ok? result)))
                (is (nil? (:plan result)))
                (is (= ["a.md" "b.md"] @processed))
                (is (= [{:file "a.md"
                         :message "Markdown原稿を変換できません: unexpected failure"}]
                       (:diagnostics result)))))))))))

(deftest execution-context-test
  (testing "When a book transformation runs, then each Markdown manuscript receives its build context and optional publication entry"
    (with-temporary-project
      (fn [project]
        (let [source (.join path project "manuscripts")
              publication [{:type :document
                            :path "chapter.md"
                            :kind "chapter"
                            :include-in-toc true}]]
          (write-file! (.join path source "chapter.md") "# 掲載原稿\n")
          (write-file! (.join path source "notes.md") "# 非掲載原稿\n")
          (let [analysis-contexts (atom [])
                transformation-contexts (atom [])
                unlisted-contexts (atom [])
                transformation-plan (create-plan project publication)]
            (with-redefs [pipeline/analyze
                          (fn [context manuscript]
                            (swap! analysis-contexts conj context)
                            {:ok? true
                             :tree manuscript
                             :diagnostics []})
                          pipeline/run-analyzed
                          (fn [context manuscript]
                            (swap! transformation-contexts conj context)
                            {:ok? true
                             :output manuscript
                             :diagnostics []})
                          pipeline/run
                          (fn [context manuscript]
                            (swap! unlisted-contexts conj context)
                            {:ok? true
                             :output manuscript
                             :diagnostics []})]
              (let [result (book-transform/run transformation-plan)
                    chapter-analysis-context (first @analysis-contexts)
                    chapter-transformation-context
                    (first @transformation-contexts)
                    notes-context (first @unlisted-contexts)]
                (is (:ok? result))
                (is (= ["chapter.md"]
                       (mapv :source-name @analysis-contexts)))
                (is (= ["chapter.md"]
                       (mapv :source-name @transformation-contexts)))
                (is (= ["notes.md"]
                       (mapv :source-name @unlisted-contexts)))
                (is (= chapter-analysis-context
                       chapter-transformation-context))
                (is (= :build (:mode chapter-analysis-context)))
                (is (= (.join path source "chapter.md")
                       (:input-path chapter-analysis-context)))
                (is (= source
                       (:source-root-path chapter-analysis-context)))
                (is (= (first publication)
                       (:publication-entry chapter-analysis-context)))
                (is (= :build (:mode notes-context)))
                (is (= (.join path source "notes.md")
                       (:input-path notes-context)))
                (is (nil? (:publication-entry notes-context)))))))))))

(deftest published-manuscript-analysis-order-test
  (testing "When a book transformation starts, then every published Markdown manuscript is analyzed before any manuscript is transformed"
    (with-temporary-project
      (fn [project]
        (let [source (.join path project "manuscripts")
              publication [{:type :document
                            :path "a.md"
                            :kind "chapter"
                            :include-in-toc true}
                           {:type :document
                            :path "b.md"
                            :kind "appendix"
                            :include-in-toc true}]
              events (atom [])
              analyzed-trees (atom {})]
          (write-file! (.join path source "a.md") "# A\n")
          (write-file! (.join path source "b.md") "# B\n")
          (write-file! (.join path source "notes.md") "# Notes\n")
          (with-redefs [pipeline/analyze
                        (fn [context manuscript]
                          (let [tree #js {:source manuscript}]
                            (swap! events conj [:analyze (:source-name context)])
                            (swap! analyzed-trees assoc (:source-name context) tree)
                            {:ok? true :tree tree :diagnostics []}))
                        pipeline/run-analyzed
                        (fn [context tree]
                          (swap! events conj [:transform (:source-name context)])
                          (is (identical? (get @analyzed-trees
                                               (:source-name context))
                                          tree))
                          {:ok? true :output "transformed\n" :diagnostics []})
                        pipeline/run
                        (fn [context _manuscript]
                          (swap! events conj [:run (:source-name context)])
                          {:ok? true :output "unlisted\n" :diagnostics []})]
            (let [result (book-transform/run
                          (create-plan project publication))]
              (is (:ok? result))
              (is (= [[:analyze "a.md"]
                      [:analyze "b.md"]
                      [:transform "a.md"]
                      [:transform "b.md"]
                      [:run "notes.md"]]
                     @events)))))))))
