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

(ns blue.lions.clono.render
  (:require [cljs.spec.alpha :as s]
            [clojure.string :as str]
            [goog.string :as gstr]
            [goog.string.format]
            ["path" :as path]
            [blue.lions.clono.ast :as ast]
            [blue.lions.clono.esm :as esm]
            [blue.lions.clono.log :as logger]
            [blue.lions.clono.parse :as parse]
            [blue.lions.clono.spec :as spec]))

(defmulti default-handler
  (fn [node _base-name]
    (ast/get-type node)))

(defmethod default-handler :default
  [node base-name]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)]
   :post [(s/valid? ::spec/node %)]}
  node)

(def plugin-cache (atom {}))

(defn load-plugin
  [plugin-dir type]
  {:pre [(s/valid? ::spec/file-path plugin-dir)
         (s/valid? ::spec/node-type type)]
   :post [(s/valid? ::spec/function-or-nil %)]}
  (or (@plugin-cache type)
      (let [plugin-path (path/join plugin-dir (str type ".js"))]
        (try
          (let [plugin (js/require plugin-path)]
            (if (and (object? plugin) (empty? (js/Object.keys plugin)))
              (do
                (logger/log :warn
                            "Invalid JavaScript file is detected."
                            {:plugin-path plugin-path :type type })
                nil)
              (do
                (swap! plugin-cache assoc type plugin)
                (logger/log :info
                            "Plugin is successfully loaded."
                            {:plugin-dir plugin-dir :type type })
                plugin)))
          (catch js/Error _
            nil)))))

(defn apply-plugin-or-default
  [node base-name & {:keys [plugin-dir]
                     :or {plugin-dir (path/join js/__dirname "plugins")}}]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)]
   :post [(s/valid? ::spec/node %)]}
  (let [type (ast/get-type node)]
    (or (when-let [plugin (load-plugin plugin-dir type)]
          (try
            (let [result (plugin (clj->js node) base-name)]
              (if (string? result)
                {:type "html" :value result}
                (throw (ex-info "Plugin returns invalid value."
                                {:type type :result result}))))
            (catch js/Error e
              (logger/log :warn
                          "Plugin execution failed, using default logic."
                          {:type type :node node :cause (.-message e)})
              nil)))
        (default-handler node base-name))))

(defn finalize-node
  [node base-name]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)]
   :post [(s/valid? ::spec/node %)]}
  (when-let [updated-node (apply-plugin-or-default node base-name)]
    (let [new-children (->> (:children updated-node)
                            (keep #(finalize-node % base-name))
                            vec)]
      (if (seq new-children)
        (assoc updated-node :children new-children)
        (dissoc updated-node :children)))))

(defn ast->markdown
  [ast]
  {:pre [(s/valid? ::spec/node ast)]
   :post [(s/valid? ::spec/markdown %)]}
  (try
    (let [converted-ast (clj->js ast {:keywordize-keys false})]
      (esm/to-markdown converted-ast))
    (catch js/Error e
      (throw (ex-info "Failed to convert AST to Markdown."
                      {:ast ast :cause e})))))

(defn nodes->markdown
  [nodes base-name]
  {:pre [(s/valid? ::spec/nodes nodes)
         (s/valid? ::spec/file-name base-name)]
   :post [(s/valid? ::spec/markdown %)]}
  (if (empty? nodes)
    ""
    (try
      (if-let [ast (finalize-node {:type "root" :children nodes} base-name)]
        (-> ast
            ast->markdown
            str/trim-newline)
        "")
      (catch js/Error e
        (throw (ex-info "Failed to convert nodes to Markdown."
                        {:nodes nodes :base-name base-name :cause e}))))))

(defn node->markdown
  [node base-name]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)]
   :post [(s/valid? ::spec/markdown %)]}
  (try
    (nodes->markdown [node] base-name)
    (catch js/Error e
      (throw (ex-info "Failed to convert node to Markdown."
                      {:node node :base-name base-name :cause e})))))

(defn build-code-html
  [content id]
  {:pre [(s/valid? ::spec/markdown content)
         (s/valid? ::spec/id-or-nil id)]
   :post [(s/valid? ::spec/html %)]}
  (gstr/format "<div class=\"cln-code\"%s>\n\n%s\n\n</div>"
               (if (seq id) (gstr/format " id=\"%s\"" id) "")
               content))

(defmethod default-handler "code"
  [node base-name]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)]
   :post [(s/valid? ::spec/node %)]}
  (let [lang (:lang node)
        meta (:meta node)
        value (:value node)
        parsed-meta (when meta
                      (parse/markdown->ast meta
                                           (parse/create-order-generator)))
        title (when parsed-meta
                (->> parsed-meta
                     ast/extract-texts
                     (map :value)
                     str/join))
        markdown (gstr/format "```%s%s\n%s\n```"
                              lang
                              (if (seq title) (str " title=" title) "")
                              value)
        id (when parsed-meta
             (-> parsed-meta
                 ast/extract-labels
                 first
                 :attributes
                 :id))]
    {:type "html" :value (build-code-html markdown id)}))
