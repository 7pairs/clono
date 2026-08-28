(ns clono.book.publish-test
  (:require
   ["node:fs" :as fs]
   ["node:os" :as os]
   ["node:path" :as path]
   [cljs.test :refer [deftest is testing]]
   [clono.book.generate :as generate]
   [clono.book.plan :as plan]
   [clono.book.publish :as publish]
   [clono.book.transform :as book-transform]))

(defn- with-temporary-project [f]
  (let [project (.mkdtempSync fs (.join path (.tmpdir os) "clono-publish-test-"))]
    (try
      (f project)
      (finally
        (.rmSync fs project #js {:recursive true :force true})))))

(defn- write-file! [file-path content]
  (.mkdirSync fs (.dirname path file-path) #js {:recursive true})
  (.writeFileSync fs file-path content "utf8"))

(defn- book-config [project]
  {:project-root project
   :config-path (.join path project "clono.config.mjs")
   :source-root "manuscripts"
   :source-path (.join path project "manuscripts")
   :output-root "build/manuscripts"
   :output-path (.join path project "build" "manuscripts")
   :publication [{:path "chapter.md"
                  :kind "chapter"
                  :include-in-toc true}]})

(defn- transformed-plan [project]
  (-> (plan/create (book-config project))
      :plan
      book-transform/run
      :plan))

(defn- prepare-plan [project]
  (write-file! (.join path project "manuscripts" "chapter.md")
               "# 本文\n")
  (write-file! (.join path project "manuscripts" "images" "diagram.svg")
               "<svg></svg>\n")
  (transformed-plan project))

(defn- output-path [project]
  (.join path project "build" "manuscripts"))

(defn- lock-path [project]
  (.join path project "build" ".manuscripts.clono-lock"))

(defn- temporary-artifacts [project]
  (let [parent (.join path project "build")]
    (if (.existsSync fs parent)
      (->> (array-seq (.readdirSync fs parent))
           (filter #(or (.startsWith % ".manuscripts.clono-staging-")
                        (.startsWith % ".manuscripts.clono-backup-")
                        (= % ".manuscripts.clono-lock")))
           sort
           vec)
      [])))

(deftest output-publication-test
  (testing "When output is missing, then the generated tree is published without temporary artifacts"
    (with-temporary-project
      (fn [project]
        (let [plan (prepare-plan project)
              output (output-path project)
              result (publish/run plan)]
          (is (:ok? result))
          (is (= output (:output-path result)))
          (is (empty? (:diagnostics result)))
          (is (.isFile (.statSync fs (.join path output "chapter.md"))))
          (is (.isFile (.statSync fs (.join path output
                                            "images"
                                            "diagram.svg"))))
          (is (.isFile (.statSync fs (.join path output
                                            "_clono"
                                            "styles"
                                            "clono.css"))))
          (is (.isFile (.statSync fs (.join path output
                                            ".clono-output.json"))))
          (is (empty? (temporary-artifacts project)))))))

  (testing "When output is an empty directory, then it is initialized with the generated tree"
    (with-temporary-project
      (fn [project]
        (let [plan (prepare-plan project)
              output (output-path project)]
          (.mkdirSync fs output #js {:recursive true})
          (is (:ok? (publish/run plan)))
          (is (.isFile (.statSync fs (.join path output "chapter.md"))))
          (is (empty? (temporary-artifacts project)))))))

  (testing "When output has a matching ownership marker, then it is replaced without retaining stale files"
    (with-temporary-project
      (fn [project]
        (let [plan (prepare-plan project)
              output (output-path project)
              stale (.join path output "stale.txt")]
          (is (:ok? (publish/run plan)))
          (write-file! stale "stale\n")
          (is (:ok? (publish/run plan)))
          (is (false? (.existsSync fs stale)))
          (is (empty? (temporary-artifacts project))))))))

(deftest unowned-output-test
  (testing "When output is non-empty without an ownership marker, then it is preserved and staging is removed"
    (with-temporary-project
      (fn [project]
        (let [plan (prepare-plan project)
              output (output-path project)
              sentinel (.join path output "sentinel.txt")]
          (write-file! sentinel "preserve me\n")
          (let [result (publish/run plan)]
            (is (false? (:ok? result)))
            (is (nil? (:output-path result)))
            (is (= "preserve me\n" (.readFileSync fs sentinel "utf8")))
            (is (= ["空でない生成済み原稿ルートに有効な所有マーカーがありません。"]
                   (mapv :message (:diagnostics result))))
            (is (empty? (temporary-artifacts project))))))))

  (testing "When the ownership marker does not match the project, then output is preserved"
    (with-temporary-project
      (fn [project]
        (let [plan (prepare-plan project)
              output (output-path project)
              marker (.join path output ".clono-output.json")]
          (is (:ok? (publish/run plan)))
          (write-file! marker
                       (str "{\n"
                            "  \"format\": 1,\n"
                            "  \"producer\": \"clono\",\n"
                            "  \"sourceRoot\": \"other\",\n"
                            "  \"outputRoot\": \"build/manuscripts\"\n"
                            "}\n"))
          (let [result (publish/run plan)]
            (is (false? (:ok? result)))
            (is (= "other"
                   (.-sourceRoot
                    (js/JSON.parse (.readFileSync fs marker "utf8")))))
            (is (empty? (temporary-artifacts project)))))))))

(deftest invalid-output-state-test
  (testing "When output is a regular file, then it is preserved and publication is rejected"
    (with-temporary-project
      (fn [project]
        (let [plan (prepare-plan project)
              output (output-path project)]
          (write-file! output "preserve me\n")
          (let [result (publish/run plan)]
            (is (false? (:ok? result)))
            (is (= "preserve me\n" (.readFileSync fs output "utf8")))
            (is (empty? (temporary-artifacts project))))))))

  (testing "When the ownership marker is invalid JSON, then existing output is preserved and publication is rejected"
    (with-temporary-project
      (fn [project]
        (let [plan (prepare-plan project)
              output (output-path project)
              marker (.join path output ".clono-output.json")]
          (is (:ok? (publish/run plan)))
          (write-file! marker "{invalid\n")
          (let [result (publish/run plan)]
            (is (false? (:ok? result)))
            (is (= "{invalid\n" (.readFileSync fs marker "utf8")))
            (is (.includes (:message (first (:diagnostics result)))
                           "所有マーカーを読み取れません"))
            (is (empty? (temporary-artifacts project)))))))))

(deftest output-lock-test
  (testing "When another process holds the output lock, then existing output is preserved and newly generated staging is removed"
    (with-temporary-project
      (fn [project]
        (let [plan (prepare-plan project)
              output (output-path project)
              lock (lock-path project)]
          (is (:ok? (publish/run plan)))
          (let [before (.readFileSync fs (.join path output "chapter.md") "utf8")]
            (.mkdirSync fs lock)
            (let [result (publish/run plan)]
              (is (false? (:ok? result)))
              (is (= before
                     (.readFileSync fs (.join path output "chapter.md") "utf8")))
              (is (= [".manuscripts.clono-lock"]
                     (temporary-artifacts project))))
            (.rmdirSync fs lock)))))))

(deftest publication-rollback-test
  (testing "When publishing staging over owned output fails, then the previous output is restored and temporary artifacts are removed"
    (with-temporary-project
      (fn [project]
        (let [plan (prepare-plan project)
              output (output-path project)
              chapter (.join path output "chapter.md")]
          (is (:ok? (publish/run plan)))
          (write-file! chapter "previous output\n")
          (let [original-rename publish/rename-path!
                failed? (atom false)]
            (with-redefs [publish/rename-path!
                          (fn [source destination]
                            (if (and (not @failed?)
                                     (= destination output)
                                     (.startsWith (.basename path source)
                                                  ".manuscripts.clono-staging-"))
                              (do
                                (reset! failed? true)
                                (throw (js/Error. "publication failed")))
                              (original-rename source destination)))]
              (let [result (publish/run plan)]
                (is (false? (:ok? result)))
                (is (= "previous output\n"
                       (.readFileSync fs chapter "utf8")))
                (is (.includes (:message (first (:diagnostics result)))
                               "既存出力を復元しました"))
                (is (empty? (temporary-artifacts project)))))))))))

(deftest post-publication-cleanup-test
  (testing "When an old backup cannot be removed after publication, then new output and the backup remain and failure is reported"
    (with-temporary-project
      (fn [project]
        (let [plan (prepare-plan project)
              output (output-path project)
              chapter (.join path output "chapter.md")]
          (is (:ok? (publish/run plan)))
          (write-file! chapter "previous output\n")
          (with-redefs [publish/remove-backup!
                        (fn [_]
                          (throw (js/Error. "backup cleanup failed")))]
            (let [result (publish/run plan)
                  artifacts (temporary-artifacts project)]
              (is (false? (:ok? result)))
              (is (not= "previous output\n"
                        (.readFileSync fs chapter "utf8")))
              (is (= 1 (count artifacts)))
              (is (.startsWith (first artifacts)
                               ".manuscripts.clono-backup-"))
              (is (.includes (:message (first (:diagnostics result)))
                             "以前の出力を削除できません"))))))))

  (testing "When the output lock cannot be removed after publication, then new output and the lock remain and failure is reported"
    (with-temporary-project
      (fn [project]
        (let [plan (prepare-plan project)
              output (output-path project)]
          (with-redefs [publish/remove-lock!
                        (fn [_]
                          (throw (js/Error. "lock cleanup failed")))]
            (let [result (publish/run plan)]
              (is (false? (:ok? result)))
              (is (.isFile (.statSync fs (.join path output "chapter.md"))))
              (is (.isDirectory (.statSync fs (lock-path project))))
              (is (.includes (:message (first (:diagnostics result)))
                             "生成済み原稿ツリーは公開しましたが")))))))))

(deftest staging-cleanup-test
  (testing "When generation fails, then partial staging is removed without creating output"
    (with-temporary-project
      (fn [project]
        (let [output (.join path project "build" "manuscripts")
              result (publish/run
                      {:source-root "manuscripts"
                       :output-root "build/manuscripts"
                       :output-path output
                       :publication []
                       :operations [{:action :transform-markdown
                                     :path "chapter.md"}]})]
          (is (false? (:ok? result)))
          (is (false? (.existsSync fs output)))
          (is (empty? (temporary-artifacts project)))))))

  (testing "When generation throws unexpectedly, then staging is removed without creating output"
    (with-temporary-project
      (fn [project]
        (let [plan (prepare-plan project)
              output (output-path project)]
          (with-redefs [generate/run
                        (fn [_ _]
                          (throw (js/Error. "unexpected generation failure")))]
            (let [result (publish/run plan)]
              (is (false? (:ok? result)))
              (is (false? (.existsSync fs output)))
              (is (empty? (temporary-artifacts project))))))))))
