(ns clono.index.generator-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.ast :as ast]
   [clono.index.generator :as generator]
   [clono.markdown :as markdown]))

(defn- occurrence
  [term normalized-reading sort-key group-id source-name marker-id]
  {:term term
   :normalized-term (.normalize term "NFKC")
   :reading normalized-reading
   :normalized-reading normalized-reading
   :sort-key sort-key
   :group-id group-id
   :source-name source-name
   :marker-id marker-id})

(deftest index-markdown-generation-contract-test
  (testing "When representative index occurrences are generated, then the complete Markdown follows the public structure contract"
    (let [output
          (generator/generate-markdown
           {:path "generated/index.md" :title "索引"}
           [(occurrence "バックナンバー"
                        "ばっくなんばー"
                        "はつくなんはあ"
                        :ha
                        "nested/chapter-two.md"
                        "clono-index-marker-3")
            (occurrence "Android"
                        "android"
                        "android"
                        :alphanumeric
                        "chapter-one.md"
                        "clono-index-marker-1")
            (occurrence "Android"
                        "android"
                        "android"
                        :alphanumeric
                        "nested/chapter-two.md"
                        "clono-index-marker-2")])
          expected
          (str "# 索引\n\n"
               "<section class=\"clono-index-group "
               "clono-index-group-alphanumeric\">\n"
               "<h2>英数字</h2>\n"
               "<dl class=\"clono-index-list\">\n"
               "<div class=\"clono-index-entry\">\n"
               "<dt>Android</dt>\n"
               "<dd><a class=\"clono-index-page\" "
               "href=\"../chapter-one.html#clono-index-marker-1\" "
               "aria-label=\"Androidの出現1\"></a>"
               "<span class=\"clono-index-separator\">,&nbsp;</span>"
               "<a class=\"clono-index-page\" "
               "href=\"../nested/chapter-two.html#clono-index-marker-2\" "
               "aria-label=\"Androidの出現2\"></a></dd>\n"
               "</div>\n"
               "</dl>\n"
               "</section>\n\n"
               "<section class=\"clono-index-group clono-index-group-ha\">\n"
               "<h2>は行</h2>\n"
               "<dl class=\"clono-index-list\">\n"
               "<div class=\"clono-index-entry\">\n"
               "<dt>バックナンバー</dt>\n"
               "<dd><a class=\"clono-index-page\" "
               "href=\"../nested/chapter-two.html#clono-index-marker-3\" "
               "aria-label=\"バックナンバーの出現1\"></a></dd>\n"
               "</div>\n"
               "</dl>\n"
               "</section>\n")]
      (is (= expected output)))))

(deftest generated-index-markdown-test
  (let [index-entry {:path "generated/index.md"
                     :title "索引 *draft*"}
        android-one (occurrence "Android"
                                "android"
                                "android"
                                :alphanumeric
                                "chapter-one.md"
                                "clono-index-marker-1")
        api (occurrence "API"
                        "api"
                        "api"
                        :alphanumeric
                        "appendix #notes.md"
                        "clono-index-marker-3")
        android-two (occurrence "Android"
                                "android"
                                "android"
                                :alphanumeric
                                "nested/chapter-two.md"
                                "clono-index-marker-4")
        back-number (occurrence "バックナンバー"
                                "ばっくなんばー"
                                "はつくなんはあ"
                                :ha
                                "nested/chapter-two.md"
                                "clono-index-marker-2")
        output (generator/generate-markdown
                index-entry
                [back-number api android-one android-two])]
    (testing "When index occurrences are generated, then populated groups and merged terms appear in deterministic order"
      (is (< (.indexOf output "clono-index-group-alphanumeric")
             (.indexOf output "clono-index-group-ha")))
      (is (< (.indexOf output "<dt>Android</dt>")
             (.indexOf output "<dt>API</dt>")
             (.indexOf output "<dt>バックナンバー</dt>")))
      (is (= 1 (count (re-seq #"<dt>Android</dt>" output))))
      (is (not (.includes output "clono-index-group-a\""))))

    (testing "When one index term has multiple occurrences, then its encoded links and accessible labels retain occurrence order"
      (is (.includes
           output
           (str "href=\"../chapter-one.html#clono-index-marker-1\" "
                "aria-label=\"Androidの出現1\"")))
      (is (.includes
           output
           (str "href=\"../nested/chapter-two.html#clono-index-marker-4\" "
                "aria-label=\"Androidの出現2\"")))
      (is (< (.indexOf output "#clono-index-marker-1")
             (.indexOf output
                       "<span class=\"clono-index-separator\">,&nbsp;</span>")
             (.indexOf output "#clono-index-marker-4"))))

    (testing "When an occurrence path contains reserved characters, then the complete href is safely encoded"
      (is (.includes
           output
           (str "href=\"../appendix%20%23notes.html"
                "#clono-index-marker-3\""))))

    (testing "When an index title contains Markdown punctuation, then the generated H1 preserves it as plain text"
      (let [tree (markdown/parse output)
            heading (first (filter #(= "heading" (.-type %))
                                   (ast/nodes tree)))]
        (is (= 1 (.-depth heading)))
        (is (= ["text"] (mapv #(.-type %) (ast/children heading))))
        (is (= "索引 *draft*" (.-value (first (ast/children heading)))))))))

(deftest generated-index-escaping-test
  (testing "When an index term contains HTML-sensitive text, then its term and accessible label are encoded without exposing reading data"
    (let [output
          (generator/generate-markdown
           {:path "index.md" :title "索引"}
           [(occurrence "A & \"B\""
                        "private-reading"
                        "private-sort-key"
                        :alphanumeric
                        "chapter.md"
                        "clono-index-marker-1")])]
      (is (.includes output "<dt>A &amp; &quot;B&quot;</dt>"))
      (is (.includes output "aria-label=\"A &amp; &quot;B&quot;の出現1\""))
      (is (not (.includes output "private-reading")))
      (is (not (.includes output "private-sort-key"))))))

(deftest empty-index-markdown-test
  (testing "When an index has no occurrences, then only its plain-text H1 is generated"
    (let [output (generator/generate-markdown
                  {:path "generated/index.md" :title "空の索引"}
                  [])
          tree (markdown/parse output)]
      (is (= 1 (count (ast/children tree))))
      (is (= "heading" (.-type (first (ast/children tree)))))
      (is (= "空の索引"
             (.-value (first (ast/children (first (ast/children tree))))))))))
