(ns clono.research.markdown-ast-test
  (:require
   ["node:fs" :as fs]
   [cljs.test :refer [deftest is testing]]
   [clono.research.directive-syntax :as directive-syntax]
   [clono.research.markdown-ast :as markdown-ast]
   [goog.object :as gobj]))

(defn read-input [name]
  (.readFileSync fs (str "input/" name ".md") "utf8"))

(defn children [node]
  (when (js/Array.isArray (.-children node))
    (array-seq (.-children node))))

(defn nodes [tree]
  (tree-seq #(some? (children %)) children tree))

(defn nodes-by-type [tree type]
  (filter #(= type (.-type %)) (nodes tree)))

(defn directive [tree name]
  (first (filter #(= name (.-name %)) (nodes tree))))

(defn node-text [node]
  (if (string? (.-value node))
    (.-value node)
    (apply str (map node-text (children node)))))

(defn property [object & names]
  (reduce gobj/get object names))

(deftest candidate-directives-test
  (let [tree (markdown-ast/parse (read-input "candidate"))
        column (directive tree "column")
        align (directive tree "align")
        page-break (directive tree "page-break")
        index (directive tree "index")
        xref (directive tree "xref")]
    (testing "When candidate directives are parsed, then each directive kind and its attributes are exposed in mdast"
      (is (= "containerDirective" (.-type column)))
      (is (= "ちょっと休憩" (property column "attributes" "title")))
      (is (= "containerDirective" (.-type align)))
      (is (= "right" (property align "attributes" "position")))
      (is (= "leafDirective" (.-type page-break)))
      (is (= "textDirective" (.-type index)))
      (is (= "さくいんこうもく" (property index "attributes" "reading")))
      (is (= "索引項目" (node-text index)))
      (is (= "textDirective" (.-type xref)))
      (is (= "chapter-introduction" (property xref "attributes" "target")))
      (is (= "第1章" (node-text xref))))

    (testing "When Markdown is nested in a container directive, then its block and inline structure is preserved"
      (is (= ["paragraph" "list"]
             (mapv #(.-type %) (children column))))
      (is (= 1 (count (nodes-by-type column "strong"))))
      (is (= 1 (count (nodes-by-type column "inlineCode"))))
      (is (= 1 (count (nodes-by-type column "link"))))
      (is (= 1 (count (nodes-by-type column "list")))))

    (testing "When source locations are inspected, then directive line, column, and offset information is available"
      (is (= {:line 18 :column 4 :offset 178}
             {:line (property index "position" "start" "line")
              :column (property index "position" "start" "column")
              :offset (property index "position" "start" "offset")})))))

(deftest round-trip-test
  (let [tree (markdown-ast/parse (read-input "candidate"))
        serialized (markdown-ast/serialize tree)
        reparsed (markdown-ast/parse serialized)
        column (directive reparsed "column")
        index (directive reparsed "index")
        xref (directive reparsed "xref")]
    (testing "When candidate Markdown is parsed, serialized, and parsed again, then its directive meaning is preserved"
      (is (= ["column" "align" "page-break" "index" "xref"]
             (->> (nodes reparsed)
                  (keep #(.-name %))
                  vec)))
      (is (= "ちょっと休憩" (property column "attributes" "title")))
      (is (= ["paragraph" "list"]
             (mapv #(.-type %) (children column))))
      (is (= 1 (count (nodes-by-type column "strong"))))
      (is (= 1 (count (nodes-by-type column "inlineCode"))))
      (is (= 1 (count (nodes-by-type column "link"))))
      (is (= 1 (count (nodes-by-type column "list"))))
      (is (= "さくいんこうもく" (property index "attributes" "reading")))
      (is (= "chapter-introduction" (property xref "attributes" "target")))))

  (let [tree (markdown-ast/parse (read-input "candidate"))
        index (directive tree "index")]
    (gobj/set (.-attributes index) "reading" "さくいん")
    (set! (.-value (first (children index))) "索引")
    (let [reparsed (-> tree markdown-ast/serialize markdown-ast/parse)
          updated-index (directive reparsed "index")]
      (testing "When mdast fields are changed from ClojureScript, then the changes survive serialization and reparsing"
        (is (= "さくいん" (property updated-index "attributes" "reading")))
        (is (= "索引" (node-text updated-index)))))))

(deftest unknown-directive-test
  (let [tree (markdown-ast/parse (read-input "unknown"))
        unknown (directive tree "third-party")
        reparsed (-> tree markdown-ast/serialize markdown-ast/parse)
        reparsed-unknown (directive reparsed "third-party")]
    (testing "When an unknown directive is parsed, then its name, attributes, and content remain available"
      (is (= "containerDirective" (.-type unknown)))
      (is (= "sample" (property unknown "attributes" "mode")))
      (is (= "clonoが知らない記法です。" (node-text unknown))))

    (testing "When an unknown directive is serialized and parsed again, then it is preserved without semantic handling"
      (is (= "containerDirective" (.-type reparsed-unknown)))
      (is (= "sample" (property reparsed-unknown "attributes" "mode")))
      (is (= "clonoが知らない記法です。" (node-text reparsed-unknown))))))

(deftest invalid-directive-test
  (let [tree (markdown-ast/parse (read-input "malformed"))
        indexes (vec (filter #(= "index" (.-name %)) (nodes tree)))
        column (directive tree "column")]
    (testing "When a known directive omits a required semantic attribute, then the directive and its source position remain inspectable"
      (is (= 2 (count indexes)))
      (is (empty? (js/Object.keys (.-attributes (first indexes)))))
      (is (= 1 (property (first indexes) "position" "start" "line"))))

    (testing "When directive attribute syntax is not closed, then the unparsed suffix remains ordinary text"
      (is (empty? (js/Object.keys (.-attributes (second indexes)))))
      (is (some #(and (= "text" (.-type %))
                      (.includes (.-value %) "{reading="))
                (nodes tree))))

    (testing "When a container directive has no closing marker, then the parser extends it to the end of the document"
      (is (= "containerDirective" (.-type column)))
      (is (= 8 (property column "position" "end" "line"))))))

(deftest code-fence-test
  (let [tree (markdown-ast/parse (read-input "code-fence"))
        code (first (nodes-by-type tree "code"))]
    (testing "When directive-like text appears in a code fence, then it remains code instead of becoming directive nodes"
      (is (= 1 (count (children tree))))
      (is (= "markdown" (.-lang code)))
      (is (.includes (.-value code) ":index[索引項目]"))
      (is (empty? (filter #(some? (.-name %)) (nodes tree)))))))

(deftest directive-syntax-diagnostic-test
  (let [options {:known-names #{"column" "index"}
                 :required-attribute-names #{"column" "index"}}]
    (testing "When a known directive has an unclosed attribute block, then its source position is reported"
      (is (= [{:file "input/malformed-attributes.md"
               :line 1
               :column 23
               :directive "index"
               :kind :malformed-attributes
               :message "`index`の属性を解析できません。"}]
             (directive-syntax/syntax-diagnostics
              (read-input "malformed-attributes")
              "input/malformed-attributes.md"
              options))))

    (testing "When a known container has no closing fence, then its opening position is reported"
      (is (= [{:file "input/unclosed-container.md"
               :line 1
               :column 1
               :directive "column"
               :kind :unclosed-container
               :message "`column`の終了マーカーがありません。"}]
             (directive-syntax/syntax-diagnostics
              (read-input "unclosed-container")
              "input/unclosed-container.md"
              options))))

    (testing "When directive-like text is protected or ordinary prose, then no syntax diagnostic is reported"
      (is (empty?
           (directive-syntax/syntax-diagnostics
            (read-input "directive-like-literals")
            "input/directive-like-literals.md"
            options))))

    (testing "When valid candidate directives are inspected, then no syntax diagnostic is reported"
      (is (empty?
           (directive-syntax/syntax-diagnostics
            (read-input "candidate")
            "input/candidate.md"
            options))))))
