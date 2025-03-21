; Copyright 2025 HASEBA Junya
; 
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
; 
;     http://www.apache.org/licenses/LICENSE-2.0
; 
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns blue.lions.clono.render-test
  (:require [cljs.test :as t]
            ["fs" :as fs]
            ["os" :as os]
            ["path" :as path]
            [blue.lions.clono.log :as logger]
            [blue.lions.clono.render :as render]))

(defn- setup-tmp-dir
  []
  (let [tmp-dir (path/join (os/tmpdir) "render-test")]
    (when (not (fs/existsSync tmp-dir))
      (fs/mkdirSync tmp-dir))
    tmp-dir))

(defn- teardown-tmp-dir
  [tmp-dir]
  (fs/rmdirSync tmp-dir #js {:recursive true}))

(t/use-fixtures :once
  {:before #(def tmp-dir (setup-tmp-dir))
   :after #(teardown-tmp-dir tmp-dir)})

(t/deftest default-handler-test
  (t/testing "Node is code."
    (t/testing "Node has title and label."
      (t/is (= {:type "html"
                :value (str "<div class=\"cln-code\" id=\"hello\">\n\n"
                            "```clojure title=First code\n"
                            "(println \"Hello, world!\")\n"
                            "```\n\n"
                            "</div>")}
               (render/default-handler
                {:type "code"
                 :lang "clojure"
                 :meta "First code:label{#hello}"
                 :value "(println \"Hello, world!\")"}
                "base-name"))))

    (t/testing "Node has title."
      (t/is (= {:type "html"
                :value (str "<div class=\"cln-code\">\n\n"
                            "```clojure title=First code\n"
                            "(println \"Hello, world!\")\n"
                            "```\n\n"
                            "</div>")}
               (render/default-handler
                {:type "code"
                 :lang "clojure"
                 :meta "First code"
                 :value "(println \"Hello, world!\")"}
                "base-name"))))

    (t/testing "Node has label."
      (t/is (= {:type "html"
                :value (str "<div class=\"cln-code\" id=\"hello\">\n\n"
                            "```clojure\n"
                            "(println \"Hello, world!\")\n"
                            "```\n\n"
                            "</div>")}
               (render/default-handler
                {:type "code"
                 :lang "clojure"
                 :meta ":label{#hello}"
                 :value "(println \"Hello, world!\")"}
                "base-name"))))

    (t/testing "Node does not have meta."
      (t/is (= {:type "html"
                :value (str "<div class=\"cln-code\">\n\n"
                            "```clojure\n"
                            "(println \"Hello, world!\")\n"
                            "```\n\n"
                            "</div>")}
               (render/default-handler
                {:type "code"
                 :lang "clojure"
                 :meta nil
                 :value "(println \"Hello, world!\")"}
                "base-name")))))

  (t/testing "Node is column."
    (t/testing "Node is valid."
      (t/is (= {:type "html"
                :value (str "<div class=\"cln-column\">\n\n"
                            "#### Column title\n\n"
                            "I am a column.\n\n"
                            "</div>")}
               (render/default-handler
                {:type "containerDirective"
                 :name "column"
                 :children [{:type "paragraph"
                             :data {:directiveLabel true}
                             :children [{:type "text"
                                         :value "Column title"}]}
                            {:type "paragraph"
                             :children [{:type "text"
                                         :value "I am a column."}]}]}
                "base-name"))))

    (t/testing "Node does not have caption."
      (reset! logger/enabled? false)
      (reset! logger/entries [])
      (let [node {:type "containerDirective"
                  :name "column"
                  :children [{:type "paragraph"
                              :data {:directiveLabel true}
                              :children []}
                             {:type "paragraph"
                              :children [{:type "text"
                                          :value "I am a column."}]}]}
            base-name "base-name"]
        (t/is (= {:type "html" :value ""}
                 (render/default-handler node base-name)))
        (t/is (= [{:level :error
                   :message "Column node does not have caption."
                   :data {:node node :base-name base-name}}]
                 @logger/entries))))

    (t/testing "Node does not have children."
      (reset! logger/enabled? false)
      (reset! logger/entries [])
      (let [node {:type "containerDirective" :name "column" :children []}
            base-name "base-name"]
        (t/is (= {:type "html" :value ""}
                 (render/default-handler node base-name)))
        (t/is (= [{:level :error
                   :message "Column node does not have children."
                   :data {:node node :base-name base-name}}]
                 @logger/entries)))))

  (t/testing "Node is figure."
    (t/testing "Node has attributes."
      (t/is (= {:type "html"
                :value "![Caption](image.jpg){id=image class=image-class}"}
               (render/default-handler
                {:type "textDirective"
                 :name "figure"
                 :attributes {:src "image.jpg" :class "image-class"}
                 :children [{:type "text" :value "Caption"}]}
                "base-name")))
      (t/is (= {:type "html"
                :value (str "![Caption](image.jpg)"
                            "{id=image class=image-class width=100}")}
               (render/default-handler
                {:type "textDirective"
                 :name "figure"
                 :attributes {:src "image.jpg"
                              :class "image-class"
                              :width "100"}
                 :children [{:type "text" :value "Caption"}]}
                "base-name"))))

    (t/testing "Node does not have attributes."
      (t/is (= {:type "html" :value "![Caption](image.jpg){id=image}"}
               (render/default-handler
                {:type "textDirective"
                 :name "figure"
                 :attributes {:src "image.jpg"}
                 :children [{:type "text" :value "Caption"}]}
                "base-name"))))

    (t/testing "Node does not have children."
      (reset! logger/enabled? false)
      (reset! logger/entries [])
      (let [node {:type "textDirective"
                  :name "figure"
                  :attributes {:src "image.jpg"}
                  :children []}
            base-name "base-name"]
        (t/is (= {:type "html" :value ""}
                 (render/default-handler node base-name))
              (t/is (= [{:level :error
                         :message "Figure node is invalid."
                         :data {:node node
                                :base-name base-name
                                :missing :children}}]
                       @logger/entries)))))

    (t/testing "Node does not have src."
      (reset! logger/enabled? false)
      (reset! logger/entries [])
      (let [node {:type "textDirective"
                  :name "figure"
                  :attributes {}
                  :children [{:type "text" :value "Caption"}]}
            base-name "base-name"]
        (t/is (= {:type "html" :value ""}
                 (render/default-handler node base-name))
              (t/is (= [{:level :error
                         :message "Figure node is invalid."
                         :data {:node node
                                :base-name base-name
                                :missing :src}}]
                       @logger/entries))))))

  (t/testing "Node does not to be updated."
    (let [node {:type "notExists"}]
      (t/is (= node (render/default-handler node "base-name"))))))

(t/deftest load-plugin-test
  (let [plugin-dir (path/join tmp-dir "load-plugin")]
    (fs/mkdirSync plugin-dir)
    (fs/writeFileSync (path/join plugin-dir "valid.js")
                      "module.exports = function(n, b) { return n; };"
                      "utf8")
    (fs/writeFileSync (path/join plugin-dir "invalid.js") "" "utf8")

    (t/testing "Plugin is valid."
      (reset! render/plugin-cache {})
      (reset! logger/enabled? false)
      (reset! logger/entries [])
      (t/is (fn? (render/load-plugin plugin-dir "valid")))
      (t/is (= [{:level :info
                 :message "Plugin is successfully loaded."
                 :data {:plugin-dir plugin-dir :type "valid"}}]
               @logger/entries)))

    (t/testing "Plugin is already loaded."
      (reset! render/plugin-cache {})
      (reset! logger/enabled? false)
      (let [plugin (render/load-plugin plugin-dir "valid")]
        (reset! logger/entries [])
        (t/is (= plugin (render/load-plugin plugin-dir "valid")))
        (t/is (= [] @logger/entries))))

    (t/testing "Plugin is invalid."
      (reset! render/plugin-cache {})
      (reset! logger/enabled? false)
      (reset! logger/entries [])
      (t/is (nil? (render/load-plugin plugin-dir "invalid")))
      (t/is (= [{:level :warn
                 :message "Invalid JavaScript file is detected."
                 :data {:plugin-path (path/join plugin-dir "invalid.js")
                        :type "invalid"}}]
               @logger/entries)))

    (t/testing "Plugin does not exist."
      (reset! render/plugin-cache {})
      (reset! logger/enabled? false)
      (reset! logger/entries [])
      (t/is (nil? (render/load-plugin plugin-dir "notExists")))
      (t/is (= [] @logger/entries)))))

(t/deftest apply-plugin-or-default-test
  (let [plugin-dir (path/join tmp-dir "apply-plugin-or-default")]
    (fs/mkdirSync plugin-dir)
    (fs/writeFileSync
     (path/join plugin-dir "valid.js")
     "module.exports = function(n, b) { return 'Plugin is called.'; };"
     "utf8")
    (fs/writeFileSync
     (path/join plugin-dir "invalid.js")
     "module.exports = function(n, b) { return 0; };"
     "utf8")

    (t/testing "Plugin is valid."
      (t/is (= {:type "html" :value "Plugin is called."}
               (render/apply-plugin-or-default {:type "valid"}
                                               "base-name"
                                               :plugin-dir plugin-dir))))

    (t/testing "Plugin is invalid."
      (let [node {:type "invalid"}]
        (reset! logger/enabled? false)
        (reset! logger/entries [])
        (t/is (= {:type "invalid"}
                 (render/apply-plugin-or-default node
                                                 "base-name"
                                                 :plugin-dir plugin-dir)))
        (t/is (= [{:level :info
                   :message "Plugin is successfully loaded."
                   :data {:plugin-dir plugin-dir :type "invalid"}}
                  {:level :warn
                   :message "Plugin execution failed, using default logic."
                   :data {:type "invalid"
                          :node node
                          :cause "Plugin returns invalid value."}}]
                 @logger/entries)))))

    (t/testing "Plugin does not exist."
      (let [node {:type "notExists"}]
        (t/is (= node (render/apply-plugin-or-default node "base-name"))))))

(t/deftest finalize-node-test
  (t/testing "All Nodes do not to be updated."
    (let [node {:type "root"
                :children [{:type "paragraph"
                            :children [{:type "text"
                                        :value "Hello, world!"}]}]}]
      (t/is (= node (render/finalize-node node "base-name"))))))

(t/deftest ast->markdown-test
  (t/testing "Valid AST is given."
    (t/is (= "Hello, world!\n"
             (render/ast->markdown
              {:type "root"
               :children [{:type "paragraph"
                           :children [{:type "text"
                                       :value "Hello, world!"}]}]}))))

  (t/testing "Invalid AST is given."
    (let [ast {:type "root"
               :children [{:type "paragraph"
                           :children [{:type "text"
                                       :value "Hello, world!"}]}
                          {:type "invalid"}]}]
      (try
        (render/ast->markdown ast)
        (catch js/Error e
          (let [data (ex-data e)]
            (t/is (= "Failed to convert AST to Markdown." (ex-message e)))
            (t/is (= ast (:ast data)))
            (t/is (= "Cannot handle unknown node `invalid`"
                     (ex-message (:cause data))))))))))

(t/deftest nodes->markdown-test
  (t/testing "Multiple nodes are given."
    (t/is (= "# Markdown\n\nHello, world!"
             (render/nodes->markdown
              [{:type "heading"
                :depth 1
                :children [{:type "text" :value "Markdown"}]}
               {:type "paragraph"
                :children [{:type "text" :value "Hello, world!"}]}]
              "base-name"))))

  (t/testing "Single node is given."
    (t/is (= "Hello, world!"
             (render/nodes->markdown
              [{:type "paragraph"
                :children [{:type "text" :value "Hello, world!"}]}]
              "base-name"))))

  (t/testing "No nodes are given."
    (t/is (= "" (render/nodes->markdown [] "base-name"))))

  (t/testing "Some nodes are invalid."
    (let [nodes [{:type "paragraph"
                  :children [{:type "text" :value "Hello, world!"}]}
                 {:type "invalid"}]
          base-name "base-name"]
      (try
        (render/nodes->markdown nodes base-name)
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)
                cause-data (ex-data cause)]
            (t/is (= "Failed to convert nodes to Markdown." (ex-message e)))
            (t/is (= nodes (:nodes data)))
            (t/is (= base-name (:base-name data)))
            (t/is (= "Failed to convert AST to Markdown." (ex-message cause)))
            (t/is (= {:type "root" :children nodes} (:ast cause-data)))
            (t/is (= "Cannot handle unknown node `invalid`"
                     (ex-message (:cause cause-data))))))))))

(t/deftest node->markdown-test
  (t/testing "Node is valid."
    (t/is (= "Hello, world!"
             (render/node->markdown
              {:type "paragraph"
               :children [{:type "text" :value "Hello, world!"}]}
              "base-name"))))

  (t/testing "Node is invalid."
    (let [node {:type "invalid"}
          base-name "base-name"]
      (try
        (render/node->markdown node base-name)
        (catch js/Error e
          (let [data (ex-data e)
                cause (:cause data)
                cause-data (ex-data cause)]
            (t/is (= "Failed to convert node to Markdown." (ex-message e)))
            (t/is (= node (:node data)))
            (t/is (= base-name (:base-name data)))
            (t/is (= "Failed to convert nodes to Markdown."
                     (ex-message cause)))
            (t/is (= [node] (:nodes cause-data)))
            (t/is (= base-name (:base-name cause-data)))
            (t/is (= "Failed to convert AST to Markdown."
                     (ex-message (:cause cause-data))))))))))

(t/deftest format-attributes-test
  (t/testing "Single attribute is given."
    (t/is (= "class=class-value"
             (render/format-attributes-for-markdown {:class "class-value"}))))

  (t/testing "Multiple attributes are given."
    (t/is (= "class=class-value id=id-value"
             (render/format-attributes-for-markdown {:class "class-value"
                                                     :id "id-value"}))))

  (t/testing "Empty map are given."
    (t/is (= "" (render/format-attributes-for-markdown {})))))

(t/deftest build-code-html-test
  (t/testing "ID is given."
    (t/is (= (str "<div class=\"cln-code\" id=\"hello\">\n\n"
                  "```\n"
                  "(println \"Hello, world!\")\n"
                  "```\n\n"
                  "</div>")
             (render/build-code-html (str "```\n"
                                          "(println \"Hello, world!\")\n"
                                          "```")
                                     "hello"))))

  (t/testing "ID is not given."
    (t/is (= (str "<div class=\"cln-code\">\n\n"
                  "```\n"
                  "(println \"Hello, world!\")\n"
                  "```\n\n"
                  "</div>")
             (render/build-code-html (str "```\n"
                                          "(println \"Hello, world!\")\n"
                                          "```")
                                     nil)))))

(t/deftest build-column-html-test
  (t/testing "Heading level is not given."
    (t/is (= (str "<div class=\"cln-column\">\n\n"
                  "#### Column title\n\n"
                  "I am a column.\n\n"
                  "</div>")
             (render/build-column-html "Column title" "I am a column."))))

  (t/testing "Heading level is given."
    (t/is (= (str "<div class=\"cln-column\">\n\n"
                  "## Column title\n\n"
                  "I am a column.\n\n"
                  "</div>")
             (render/build-column-html "Column title"
                                       "I am a column."
                                       :heading-level 2)))))

(t/deftest build-image-markdown-test
  (t/testing "Image has attributes."
    (t/is (= "![Caption](image.jpg){id=image-id class=image-class}"
             (render/build-image-markdown "Caption"
                                          "image.jpg"
                                          "image-id"
                                          {:class "image-class"})))
    (t/is (= "![Caption](image.jpg){id=image-id class=image-class width=100}"
             (render/build-image-markdown "Caption"
                                          "image.jpg"
                                          "image-id"
                                          {:class "image-class"
                                           :width "100"}))))

  (t/testing "Image does not have attributes."
    (t/is (= "![Caption](image.jpg){id=image-id}"
             (render/build-image-markdown "Caption"
                                          "image.jpg"
                                          "image-id"
                                          {})))))
