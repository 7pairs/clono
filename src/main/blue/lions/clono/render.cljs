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
            [blue.lions.clono.identifier :as id]
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

(defn format-attributes-for-markdown
  [attributes]
  {:pre [(s/valid? ::spec/attributes attributes)]
   :post [(s/valid? ::spec/formatted-attributes %)]}
  (->> attributes
       (remove (comp nil? second))
       (map (fn [[k v]]
              (str (name k) "=" (gstr/htmlEscape v))))
       (str/join " ")))

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

(defn build-column-html
  [caption content & {:keys [heading-level] :or {heading-level 4}}]
  {:pre [(s/valid? ::spec/caption caption)
         (s/valid? ::spec/markdown content)]
   :post [(s/valid? ::spec/html %)]}
  (gstr/format "<div class=\"cln-column\">\n\n%s %s\n\n%s\n\n</div>"
               (str/join (repeat heading-level "#")) caption content))

(defmethod default-handler "column"
  [node base-name]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)]
   :post [(s/valid? ::spec/node %)]}
  (let [children (:children node)]
    (if (seq children)
      (let [caption (-> children
                        first
                        :children
                        first
                        :value)]
        (if caption
          (let [content (nodes->markdown (vec (rest children)) base-name)]
            {:type "html" :value (build-column-html caption content)})
          (do
            (logger/log :error
                        "Column node does not have caption."
                        {:node node :base-name base-name})
            {:type "html" :value ""})))
      (do
        (logger/log :error
                    "Column node does not have children."
                    {:node node :base-name base-name})
        {:type "html" :value ""}))))

(defn build-image-markdown
  [caption file-path id attributes]
  {:pre [(s/valid? ::spec/caption caption)
         (s/valid? ::spec/file-path file-path)
         (s/valid? ::spec/id id)
         (s/valid? ::spec/attributes attributes)]
   :post [(s/valid? ::spec/markdown %)]}
  (let [id-str (when (seq id) (str "id=" id))
        attr-str (format-attributes-for-markdown attributes)
        brace-content (str/join " " (remove empty? [id-str attr-str]))]
    (if (seq brace-content)
      (gstr/format "![%s](%s){%s}" caption file-path brace-content)
      (gstr/format "![%s](%s)" caption file-path))))

(defmethod default-handler "figure"
  [node base-name]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)]
   :post [(s/valid? ::spec/node %)]}
  (let [child (-> node
                  :children
                  first)
        attributes (:attributes node)
        src (:src attributes)]
    (if (and child src)
      {:type "html"
       :value (build-image-markdown (node->markdown child base-name)
                                    src
                                    (id/extract-base-name src)
                                    (dissoc attributes :src))}
      (do
        (logger/log :error
                    "Figure node is invalid."
                    {:node node
                     :base-name base-name
                     :missing (cond
                                (nil? child) :children
                                (nil? src) :src)})
        {:type "html" :value ""}))))

(defn build-footnote-html
  [footnote]
  {:pre [(s/valid? ::spec/markdown footnote)]
   :post [(s/valid? ::spec/html %)]}
  (if (seq footnote)
    (gstr/format "<span class=\"cln-footnote\">%s</span>" footnote)
    ""))

(defmethod default-handler "footnoteReference"
  [node base-name]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)]
   :post [(s/valid? ::spec/node %)]}
  (let [value (if-let [child (-> node
                                 :children
                                 first)]
                (build-footnote-html (node->markdown child base-name))
                (do
                  (logger/log :error
                              "FootnoteReference node does not have children."
                              {:node node :base-name base-name})
                  ""))]
    {:type "html" :value value}))
