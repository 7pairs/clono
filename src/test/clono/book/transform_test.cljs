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
              publication [{:path "chapter.md"
                            :kind "chapter"
                            :include-in-toc true}
                           {:path "appendix.MD"
                            :kind "appendix"
                            :include-in-toc true}]]
          (write-file! (.join path source "chapter.md")
                       ":::align{position=\"right\"}\n本文\n:::\n")
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
                         [{:path "a.md"
                           :kind "chapter"
                           :include-in-toc true}
                          {:path "b.md"
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
                 [{:path "a.md"
                   :kind "chapter"
                   :include-in-toc true}
                  {:path "b.md"
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
                 [{:path "a.md"
                   :kind "chapter"
                   :include-in-toc true}
                  {:path "b.md"
                   :kind "chapter"
                   :include-in-toc true}])]
            (with-redefs [pipeline/run
                          (fn [source-name source]
                            (swap! processed conj source-name)
                            (if (= "a.md" source-name)
                              (throw (js/Error. "unexpected failure"))
                              {:ok? true
                               :output source
                               :diagnostics []}))]
              (let [result (book-transform/run transformation-plan)]
                (is (false? (:ok? result)))
                (is (nil? (:plan result)))
                (is (= ["a.md" "b.md"] @processed))
                (is (= [{:file "a.md"
                         :message "Markdown原稿を変換できません: unexpected failure"}]
                       (:diagnostics result)))))))))))
