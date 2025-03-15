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
  (t/testing "Node does not to be updated."
    (let [node {:type "notExists"}]
      (t/is (= node (render/default-handler node "base-name"))))))

(t/deftest load-plugin-test
  (let [plugin-dir (path/join tmp-dir "load-plugin")]
    (fs/mkdirSync plugin-dir)
    (fs/writeFileSync (path/join plugin-dir "valid.js")
                      "module.exports = function(n, b) { return n; };")
    (fs/writeFileSync (path/join plugin-dir "invalid.js") "")

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
     "module.exports = function(n, b) { return 'Plugin is called.'; };")
    (fs/writeFileSync
     (path/join plugin-dir "invalid.js")
     "module.exports = function(n, b) { return 0; };")

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
        (let [entries @logger/entries]
          (t/is (= 2 (count entries)))
          (t/is (= {:level :warn
                    :message "Plugin execution failed, using default logic."
                    :data {:type "invalid"
                           :node node
                           :cause "Plugin returns invalid value."}}
                   (second entries)))))))

    (t/testing "Plugin does not exist."
      (let [node {:type "notExists"}]
        (t/is (= node (render/apply-plugin-or-default node "base-name"))))))

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
