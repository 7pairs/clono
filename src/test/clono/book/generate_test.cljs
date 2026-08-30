(ns clono.book.generate-test
  (:require
   ["node:fs" :as fs]
   ["node:os" :as os]
   ["node:path" :as path]
   [cljs.test :refer [deftest is testing]]
   [clono.book.generate :as generate]
   [clono.book.plan :as plan]
   [clono.book.transform :as book-transform]))

(defn- with-temporary-project [f]
  (let [project (.mkdtempSync fs (.join path (.tmpdir os) "clono-generate-test-"))]
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

(defn- transformed-plan [project publication]
  (-> (plan/create (book-config project publication))
      :plan
      book-transform/run
      :plan))

(defn- read-json [file-path]
  (js->clj (js/JSON.parse (.readFileSync fs file-path "utf8"))))

(deftest generated-tree-test
  (testing "When a transformed plan is generated, then Markdown, regular files, empty directories, the bundled stylesheet, and the ownership marker are written"
    (with-temporary-project
      (fn [project]
        (let [source (.join path project "manuscripts")
              generation-path (.join path project "staging")
              publication [{:type :document
                            :path "chapter.md"
                            :kind "chapter"
                            :include-in-toc true}
                           {:type :document
                            :path "appendix.html"
                            :kind "appendix"
                            :include-in-toc false}]
              binary-content (js/Uint8Array. #js [0 1 2 127 128 255])]
          (write-file! (.join path source "chapter.md")
                       ":::align{position=\"right\"}\n本文\n:::\n")
          (write-file! (.join path source "appendix.html")
                       "<p>付録</p>\n")
          (.mkdirSync fs (.join path source "empty") #js {:recursive true})
          (.mkdirSync fs (.join path source "images") #js {:recursive true})
          (.writeFileSync fs (.join path source "images" "binary.dat") binary-content)
          (let [result (generate/run
                        (transformed-plan project publication)
                        generation-path)]
            (is (:ok? result))
            (is (= (.resolve path generation-path) (:generated-path result)))
            (is (empty? (:diagnostics result)))
            (is (.includes (.readFileSync fs
                                         (.join path generation-path "chapter.md")
                                         "utf8")
                           "<div class=\"clono-align-right\">"))
            (is (= "<p>付録</p>\n"
                   (.readFileSync fs
                                  (.join path generation-path "appendix.html")
                                  "utf8")))
            (is (false? (.existsSync fs
                                      (.join path generation-path
                                             "_clono"
                                             "pages"
                                             "blank-page.html"))))
            (is (.isDirectory (.statSync fs (.join path generation-path "empty"))))
            (is (= (vec (array-seq binary-content))
                   (vec (array-seq
                         (.readFileSync fs
                                        (.join path generation-path
                                               "images"
                                               "binary.dat"))))))
            (is (.equals (.readFileSync fs (generate/stylesheet-path))
                         (.readFileSync fs
                                        (.join path generation-path
                                               "_clono"
                                               "styles"
                                               "clono.css"))))
            (is (= {"format" 1
                    "producer" "clono"
                    "sourceRoot" "manuscripts"
                    "outputRoot" "build/manuscripts"}
                   (read-json (.join path generation-path
                                     ".clono-output.json"))))))))))

(deftest blank-page-generation-test
  (testing "When publication contains repeated blank pages, then one shared HTML resource and the required stylesheet rule are generated"
    (with-temporary-project
      (fn [project]
        (let [source (.join path project "manuscripts")
              generation-path (.join path project "staging")
              publication [{:type :blank-page}
                           {:type :document
                            :path "chapter.md"
                            :kind "chapter"
                            :include-in-toc true}
                           {:type :blank-page}
                           {:type :blank-page}]
              blank-page-path (.join path generation-path
                                     "_clono"
                                     "pages"
                                     "blank-page.html")]
          (write-file! (.join path source "chapter.md") "# 本文\n")
          (let [result (generate/run
                        (transformed-plan project publication)
                        generation-path)]
            (is (:ok? result))
            (is (= (str "<!doctype html>\n"
                        "<html>\n"
                        "  <head>\n"
                        "    <meta charset=\"utf-8\">\n"
                        "    <title>Blank page</title>\n"
                        "  </head>\n"
                        "  <body>\n"
                        "    <div class=\"clono-blank-page\" aria-hidden=\"true\"></div>\n"
                        "  </body>\n"
                        "</html>\n")
                   (.readFileSync fs blank-page-path "utf8")))
            (is (.includes (.readFileSync fs
                                         (.join path generation-path
                                                "_clono"
                                                "styles"
                                                "clono.css")
                                         "utf8")
                           (str ".clono-blank-page {\n"
                                "  page: clono-blank;\n"
                                "  break-before: page;\n"
                                "  break-after: page;\n"
                                "  min-block-size: 1px;\n"
                                "}")))))))))

(deftest empty-existing-generation-root-test
  (testing "When the generation root is an existing empty directory, then the generated tree is created there"
    (with-temporary-project
      (fn [project]
        (let [source (.join path project "manuscripts")
              generation-path (.join path project "staging")
              publication [{:type :document
                            :path "chapter.md"
                            :kind "chapter"
                            :include-in-toc true}]]
          (write-file! (.join path source "chapter.md") "# 本文\n")
          (.mkdirSync fs generation-path)
          (is (:ok? (generate/run
                     (transformed-plan project publication)
                     generation-path)))
          (is (.isFile (.statSync fs (.join path generation-path
                                            ".clono-output.json")))))))))

(deftest unsafe-generation-root-test
  (testing "When the generation root is not an empty directory, then it is rejected without changing existing contents"
    (with-temporary-project
      (fn [project]
        (let [generation-path (.join path project "staging")
              sentinel (.join path generation-path "sentinel.txt")]
          (write-file! sentinel "preserve me\n")
          (let [result (generate/run {:operations [] :publication []}
                                     generation-path)]
            (is (false? (:ok? result)))
            (is (nil? (:generated-path result)))
            (is (= "preserve me\n" (.readFileSync fs sentinel "utf8")))
            (is (= ["生成先には存在しないパスまたは空のディレクトリを指定してください。"]
                   (mapv :message (:diagnostics result)))))))))

  (testing "When the generation root is a symbolic link, then it is rejected without changing its target"
    (with-temporary-project
      (fn [project]
        (let [target (.join path project "target")
              generation-path (.join path project "staging")]
          (.mkdirSync fs target)
          (.symlinkSync fs
                        target
                        generation-path
                        (if (= "win32" (.-platform js/process)) "junction" "dir"))
          (let [result (generate/run {:operations [] :publication []}
                                     generation-path)]
            (is (false? (:ok? result)))
            (is (empty? (array-seq (.readdirSync fs target))))))))))

(deftest regular-file-copy-failure-test
  (testing "When a regular file cannot be copied, then generation fails without writing the stylesheet or ownership marker"
    (with-temporary-project
      (fn [project]
        (let [source (.join path project "manuscripts")
              generation-path (.join path project "staging")
              publication [{:type :document
                            :path "chapter.md"
                            :kind "chapter"
                            :include-in-toc true}]
              asset (.join path source "asset.txt")]
          (write-file! (.join path source "chapter.md") "# 本文\n")
          (write-file! asset "削除予定\n")
          (let [plan (transformed-plan project publication)
                _ (.unlinkSync fs asset)
                result (generate/run plan generation-path)]
            (is (false? (:ok? result)))
            (is (nil? (:generated-path result)))
            (is (= ["asset.txt"] (mapv :file (:diagnostics result))))
            (is (false? (.existsSync fs
                                      (.join path generation-path
                                             "_clono"
                                             "styles"
                                             "clono.css"))))
            (is (false? (.existsSync fs
                                      (.join path generation-path
                                             ".clono-output.json"))))))))))

(deftest missing-publication-generation-test
  (testing "When a publication manuscript is absent after generation, then every missing entry is diagnosed before the ownership marker is written"
    (with-temporary-project
      (fn [project]
        (let [generation-path (.join path project "staging")
              plan {:source-root "manuscripts"
                    :output-root "build/manuscripts"
                    :publication [{:type :document :path "missing-one.md"}
                                  {:type :document :path "missing-two.html"}]
                    :operations []}
              result (generate/run plan generation-path)]
          (is (false? (:ok? result)))
          (is (nil? (:generated-path result)))
          (is (= ["missing-one.md" "missing-two.html"]
                 (mapv :file (:diagnostics result))))
          (is (.isFile (.statSync fs
                                  (.join path generation-path
                                         "_clono"
                                         "styles"
                                         "clono.css"))))
          (is (false? (.existsSync fs
                                    (.join path generation-path
                                           ".clono-output.json")))))))))

(deftest invalid-operation-test
  (testing "When an operation path escapes the generation root, then generation fails without writing outside it"
    (with-temporary-project
      (fn [project]
        (let [generation-path (.join path project "staging")
              outside (.join path project "outside.md")
              result (generate/run
                      {:source-root "manuscripts"
                       :output-root "build/manuscripts"
                       :publication []
                       :operations [{:action :transform-markdown
                                     :path "../outside.md"
                                     :content "# 外側\n"}]}
                      generation-path)]
          (is (false? (:ok? result)))
          (is (false? (.existsSync fs outside)))))))

  (testing "When a transformation operation has no generated content, then generation fails before writing a marker"
    (with-temporary-project
      (fn [project]
        (let [generation-path (.join path project "staging")
              result (generate/run
                      {:source-root "manuscripts"
                       :output-root "build/manuscripts"
                       :publication []
                       :operations [{:action :transform-markdown
                                     :path "chapter.md"
                                     :source-path (.join path project "chapter.md")}]}
                      generation-path)]
          (is (false? (:ok? result)))
          (is (false? (.existsSync fs
                                    (.join path generation-path
                                           ".clono-output.json")))))))))
