(ns clono.book.index-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.book.index :as book-index]
   [clono.pipeline :as pipeline]))

(defn- analyzed-manuscript [source-name source]
  (let [context {:mode :build
                 :source-name source-name}
        analysis (pipeline/analyze context source)]
    {:tree (:tree analysis)
     :context context}))

(deftest published-index-entry-collection-test
  (let [chapter-source
        (str "最初は:index[五木]{reading=\"いつき\"}です。\n\n"
             "次は:index[一気]{reading=\"いっき\"}です。\n")
        appendix-source
        ":index[Android]{reading=\"ＡＮＤＲＯＩＤ\"}を使います。\n"
        unlisted-source
        ":index[対象外]{reading=\"たいしようかい\"}です。\n"
        publication
        [{:type :document
          :path "zeta.md"
          :kind "chapter"
          :include-in-toc true}
         {:type :blank-page}
         {:type :document
          :path "nested/alpha.MD"
          :kind "appendix"
          :include-in-toc true}
         {:type :index
          :path "generated/index.md"
          :title "索引"
          :include-in-toc true}
         {:type :document
          :path "colophon.html"
          :kind "backmatter"
          :include-in-toc false}]
        manuscripts
        [(analyzed-manuscript "nested/alpha.MD" appendix-source)
         (analyzed-manuscript "unlisted.md" unlisted-source)
         (analyzed-manuscript "zeta.md" chapter-source)]
        entries (book-index/collect publication manuscripts)]
    (testing "When analyzed manuscripts are collected for a book, then only published Markdown entries remain in publication and source order"
      (is (= [{:term "五木"
               :normalized-reading "いつき"
               :sort-key "いつき"
               :source-name "zeta.md"}
              {:term "一気"
               :normalized-reading "いっき"
               :sort-key "いつき"
               :source-name "zeta.md"}
              {:term "Android"
               :normalized-reading "android"
               :sort-key "android"
               :source-name "nested/alpha.MD"}]
             (mapv #(select-keys %
                                [:term
                                 :normalized-reading
                                 :sort-key
                                 :source-name])
                   entries)))
      (is (= [(.indexOf chapter-source ":index[五木]")
              (.indexOf chapter-source ":index[一気]")
              (.indexOf appendix-source ":index[Android]")]
             (mapv :offset entries))))))

(deftest empty-index-entry-collection-test
  (testing "When published Markdown contains no index directive, then the book collection is empty"
    (let [publication [{:type :document
                        :path "chapter.md"
                        :kind "chapter"
                        :include-in-toc true}]
          manuscripts [(analyzed-manuscript "chapter.md" "# 本文\n")]]
      (is (empty? (book-index/collect publication manuscripts))))))

(deftest book-index-preflight-test
  (let [index-entry {:type :index
                     :path "generated/index.md"
                     :title "索引"
                     :include-in-toc true}
        documents [{:type :document
                    :path "chapter-one.md"
                    :kind "chapter"
                    :include-in-toc true}
                   {:type :document
                    :path "chapter-two.md"
                    :kind "chapter"
                    :include-in-toc true}]
        manuscripts
        [(analyzed-manuscript
          "chapter-two.md"
          ":index[後]{reading=\"あと\"}です。\n")
         (analyzed-manuscript
          "chapter-one.md"
          ":index[先]{reading=\"さき\"}です。\n")]
        config {:config-path "/book/clono.config.mjs"
                :publication (conj documents index-entry)}]
    (testing "When a valid book index passes preflight, then its markers are numbered in publication order"
      (let [result (book-index/prepare config manuscripts [])]
        (is (:ok? result))
        (is (empty? (:diagnostics result)))
        (is (= [{:term "先"
                 :source-name "chapter-one.md"
                 :marker-id "clono-index-marker-1"}
                {:term "後"
                 :source-name "chapter-two.md"
                 :marker-id "clono-index-marker-2"}]
               (mapv #(select-keys % [:term :source-name :marker-id])
                     (:entries result))))))

    (testing "When published Markdown contains an index directive without an index output, then book preflight rejects it"
      (let [result (book-index/prepare
                    (assoc config :publication documents)
                    manuscripts
                    [])]
        (is (false? (:ok? result)))
        (is (nil? (:entries result)))
        (is (= [{:file "/book/clono.config.mjs"
                 :message (str "`publication`には、掲載Markdownの索引指定を出力する"
                               "`index`が必要です。")}]
               (:diagnostics result)))))

    (testing "When one index term has conflicting readings across manuscripts, then the later occurrence is diagnosed"
      (let [conflicting-manuscripts
            [(analyzed-manuscript
              "chapter-one.md"
              ":index[橋]{reading=\"はし\"}です。\n")
             (analyzed-manuscript
              "chapter-two.md"
              ":index[橋]{reading=\"ばし\"}です。\n")]
            result (book-index/prepare config conflicting-manuscripts [])]
        (is (false? (:ok? result)))
        (is (nil? (:entries result)))
        (is (= [{:file "chapter-two.md"
                 :line 1
                 :column 1
                 :directive "index"
                 :message "`index`の索引語`橋`には異なる読みを指定できません。"}]
               (:diagnostics result)))))))

(deftest optional-empty-book-index-test
  (testing "When published Markdown has no index directive or index output, then book preflight succeeds with no entries"
    (let [publication [{:type :document
                        :path "chapter.md"
                        :kind "chapter"
                        :include-in-toc true}]
          result (book-index/prepare
                  {:config-path "/book/clono.config.mjs"
                   :publication publication}
                  [(analyzed-manuscript "chapter.md" "# 本文\n")]
                  [])]
      (is (:ok? result))
      (is (= [] (:entries result)))
      (is (empty? (:diagnostics result))))))
