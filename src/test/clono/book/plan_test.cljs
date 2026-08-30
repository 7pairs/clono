(ns clono.book.plan-test
  (:require
   ["node:child_process" :refer [execFileSync]]
   ["node:fs" :as fs]
   ["node:os" :as os]
   ["node:path" :as path]
   [cljs.test :refer [deftest is testing]]
   [clono.book.plan :as plan]))

(defn- with-temporary-project [f]
  (let [project (.mkdtempSync fs (.join path (.tmpdir os) "clono-plan-test-"))]
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

(defn- operation-contract [operation]
  (select-keys operation [:action :path]))

(deftest transformation-plan-test
  (testing "When a manuscript tree is planned, then every entry has a deterministic operation without writing output"
    (with-temporary-project
      (fn [project]
        (let [source (.join path project "manuscripts")
              output (.join path project "build" "manuscripts")
              publication [{:path "chapter.md"
                            :kind "chapter"
                            :include-in-toc true}
                           {:path "appendix.MD"
                            :kind "appendix"
                            :include-in-toc false}]]
          (write-file! (.join path source "chapter.md") "# 本文\n")
          (write-file! (.join path source "appendix.MD") "# 付録\n")
          (write-file! (.join path source "colophon.html") "<p>奥付</p>\n")
          (write-file! (.join path source "images" "diagram.svg") "<svg></svg>\n")
          (write-file! (.join path source "nested" "chapter-two.md") "# 第2章\n")
          (write-file! (.join path source "nested" ".clono-output.json") "{}\n")
          (.mkdirSync fs (.join path source "empty") #js {:recursive true})
          (let [result (plan/create (book-config project publication))]
            (is (:ok? result))
            (is (empty? (:diagnostics result)))
            (is (= [{:action :transform-markdown :path "appendix.MD"}
                    {:action :transform-markdown :path "chapter.md"}
                    {:action :copy-file :path "colophon.html"}
                    {:action :create-directory :path "empty"}
                    {:action :create-directory :path "images"}
                    {:action :copy-file :path "images/diagram.svg"}
                    {:action :create-directory :path "nested"}
                    {:action :copy-file :path "nested/.clono-output.json"}
                    {:action :transform-markdown :path "nested/chapter-two.md"}]
                   (mapv operation-contract
                         (:operations (:plan result)))))
            (is (= publication (:publication (:plan result))))
            (is (every? #(.isAbsolute path (:source-path %))
                        (:operations (:plan result))))
            (is (false? (.existsSync fs output)))))))))

(deftest reserved-path-test
  (testing "When reserved paths occur at the manuscript root, then both collisions are diagnosed without exposing a partial plan"
    (with-temporary-project
      (fn [project]
        (let [source (.join path project "manuscripts")]
          (write-file! (.join path source ".clono-output.json") "{}\n")
          (write-file! (.join path source "_clono" "styles" "custom.css") "body {}\n")
          (write-file! (.join path source "chapter.md") "# 本文\n")
          (let [result (plan/create
                        (book-config project
                                     [{:path "chapter.md"
                                       :kind "chapter"
                                       :include-in-toc true}]))
                messages (set (map :message (:diagnostics result)))]
            (is (false? (:ok? result)))
            (is (nil? (:plan result)))
            (is (= #{"入力原稿ルート直下の`.clono-output.json`はclonoの予約パスです。"
                     "入力原稿ルート直下の`_clono`はclonoの予約パスです。"}
                   messages))))))))

(deftest symbolic-link-test
  (testing "When a symbolic link occurs in the manuscript tree, then it is diagnosed without following its target"
    (with-temporary-project
      (fn [project]
        (let [source (.join path project "manuscripts")
              target (.join path project "outside")]
          (write-file! (.join path source "chapter.md") "# 本文\n")
          (write-file! (.join path target "secret.md") "# 対象外\n")
          (.symlinkSync fs
                        target
                        (.join path source "linked")
                        (if (= "win32" (.-platform js/process)) "junction" "dir"))
          (let [result (plan/create
                        (book-config project
                                     [{:path "chapter.md"
                                       :kind "chapter"
                                       :include-in-toc true}]))]
            (is (false? (:ok? result)))
            (is (nil? (:plan result)))
            (is (= ["入力原稿ツリーにシンボリックリンクは使用できません: linked"]
                   (mapv :message (:diagnostics result))))))))))

(deftest publication-plan-consistency-test
  (testing "When a publication manuscript is absent from the traversal result, then the inconsistent plan is rejected"
    (with-temporary-project
      (fn [project]
        (let [source (.join path project "manuscripts")]
          (write-file! (.join path source "existing.md") "# 本文\n")
          (let [result (plan/create
                        (book-config project
                                     [{:path "missing.md"
                                       :kind "chapter"
                                       :include-in-toc true}]))]
            (is (false? (:ok? result)))
            (is (nil? (:plan result)))
            (is (= ["`publication`の原稿を変換計画に含められません: missing.md"]
                   (mapv :message (:diagnostics result))))))))))

(deftest unsupported-file-type-test
  (testing "When an unsupported file type occurs in the manuscript tree, then it is diagnosed on POSIX"
    (when-not (= "win32" (.-platform js/process))
      (with-temporary-project
        (fn [project]
          (let [source (.join path project "manuscripts")
                fifo (.join path source "events.pipe")]
            (write-file! (.join path source "chapter.md") "# 本文\n")
            (execFileSync "mkfifo" #js [fifo])
            (let [result (plan/create
                          (book-config project
                                       [{:path "chapter.md"
                                         :kind "chapter"
                                         :include-in-toc true}]))]
              (is (false? (:ok? result)))
              (is (nil? (:plan result)))
              (is (= ["入力原稿ツリーに対応していないファイル種別があります: events.pipe"]
                     (mapv :message (:diagnostics result)))))))))))
