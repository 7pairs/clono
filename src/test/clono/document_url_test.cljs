(ns clono.document-url-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.document-url :as document-url]))

(deftest relative-html-url-test
  (testing "When a target is in the same Markdown document, then its URL contains only the fragment"
    (is (= "#figure-overview"
           (document-url/relative-html-url
            "chapters/overview.md"
            "chapters/overview.md"
            "figure-overview"))))

  (testing "When a target is in another Markdown document, then its URL is relative to the source HTML document"
    (is (= "../../chapters/overview.html#figure-overview"
           (document-url/relative-html-url
            "appendices/details/appendix.md"
            "chapters/overview.md"
            "figure-overview"))))

  (testing "When a target path contains reserved and non-ASCII characters, then each URL path segment is RFC 3986 encoded"
    (is (= (str "../appendices/%E5%9B%B3%232%3F%21%27%28%29%2A%25.html"
                "#figure-external")
           (document-url/relative-html-url
            "chapters/chapter-one.md"
            "appendices/図#2?!'()*%.MD"
            "figure-external")))))

(deftest ambiguous-html-path-test
  (testing "When distinct Markdown paths resolve to the same HTML path, then URL generation rejects the ambiguity"
    (let [error
          (try
            (document-url/relative-html-url
             "chapter.md"
             "chapter.MD"
             "target")
            nil
            (catch :default caught
              caught))]
      (is (some? error)))))
