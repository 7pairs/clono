(ns clono.research.transform-config-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clono.research.transform-config :as transform-config]))

(deftest transform-project-argument-test
  (testing "When a project is explicitly selected for transform, then its path is retained independently of input and output"
    (is (= {:ok? true
            :input "chapter.md"
            :output "preview.md"
            :project "../book project#1"}
           (transform-config/parse-transform-arguments
            ["--project" "../book project#1"
             "chapter.md"
             "--output" "preview.md"]))))

  (testing "When transform has no project option, then no project configuration is selected"
    (is (= {:ok? true
            :input "chapter.md"
            :output "preview.md"
            :project nil}
           (transform-config/parse-transform-arguments
            ["chapter.md" "-o" "preview.md"])))))
