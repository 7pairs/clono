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

(defn format-attributes-for-html
  [attributes]
  {:pre [(s/valid? ::spec/attributes attributes)]
   :post [(s/valid? ::spec/formatted-attributes %)]}
  (->> attributes
       (remove (comp nil? second))
       (map (fn [[k v]]
              (str (name k) "=\"" (gstr/htmlEscape v) "\"")))
       (str/join " ")))

(defn format-attributes-for-markdown
  [attributes]
  {:pre [(s/valid? ::spec/attributes attributes)]
   :post [(s/valid? ::spec/formatted-attributes %)]}
  (->> attributes
       (remove (comp nil? second))
       (map (fn [[k v]]
              (str (name k) "=" (gstr/htmlEscape v))))
       (str/join " ")))

(defn build-href
  [key]
  {:pre [(s/valid? ::spec/id key)]
   :post [(s/valid? ::spec/url %)]}
  (let [{:keys [chapter id]} (id/parse-dic-key key)]
    (if (seq id)
      (if chapter
        (gstr/format "%s.html#%s" chapter id)
        (gstr/format "#%s" id))
      "")))

(defn build-link-html
  [href text & {:keys [attributes] :or {attributes {}}}]
  {:pre [(s/valid? ::spec/url href)
         (s/valid? ::spec/anchor-text text)]
   :post [(s/valid? ::spec/html %)]}
  (let [attr-str (format-attributes-for-html attributes)]
    (if (seq href)
      (gstr/format "<a href=\"%s\"%s>%s</a>"
                   (gstr/htmlEscape href)
                   (if (seq attr-str) (str " " attr-str) "")
                   (gstr/htmlEscape text))
      (gstr/htmlEscape text))))

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

(defn build-index-html
  [id content]
  {:pre [(s/valid? ::spec/id-or-nil id)
         (s/valid? ::spec/markdown content)]
   :post [(s/valid? ::spec/html %)]}
  (if (seq id)
    (gstr/format "<span id=\"%s\">%s</span>" (gstr/htmlEscape id) content)
    (gstr/format "<span>%s</span>" content)))

(defmethod default-handler "index"
  [node base-name]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)]
   :post [(s/valid? ::spec/node %)]}
  (let [child (-> node
                  :children
                  first)]
    (if child
      {:type "html"
       :value (build-index-html (:id node)
                                (node->markdown child base-name))}
      (do
        (logger/log :error
                    "Index node does not have children."
                    {:node node :base-name base-name})
        {:type "html" :value ""}))))

(defn build-ref-link
  [href text attributes]
  {:pre [(s/valid? ::spec/url href)
         (s/valid? ::spec/anchor-text text)
         (s/valid? ::spec/attributes attributes)]
   :post [(s/valid? ::spec/node %)]}
  (try
    (let [html (build-link-html href text :attributes attributes)]
      {:type "html" :value html})
    (catch js/Error e
      (logger/log :error
                  "Failed to build ref link."
                  {:href href
                   :text text
                   :attributes attributes
                   :cause (.-message e)})
      {:type "html" :value ""})))

(defmethod default-handler "refCode"
  [node base-name]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)]
   :post [(s/valid? ::spec/node %)]}
  (if-let [target-id (-> node
                         :attributes
                         :id)]
    (build-ref-link (build-href target-id)
                    ""
                    {:class "cln-ref-code"})
    (do
      (logger/log :error
                  "RefCode node does not have ID."
                  {:node node :base-name base-name})
      {:type "html" :value ""})))

(defmethod default-handler "refFigure"
  [node base-name]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)]
   :post [(s/valid? ::spec/node %)]}
  (if-let [target-id (-> node
                         :attributes
                         :id)]
    (build-ref-link (build-href target-id)
                    ""
                    {:class "cln-ref-figure"})
    (do
      (logger/log :error
                  "RefFigure node does not have ID."
                  {:node node :base-name base-name})
      {:type "html" :value ""})))

(defmethod default-handler "refHeading"
  [node base-name]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)]
   :post [(s/valid? ::spec/node %)]}
  (if-let [href (:url node)]
    (build-ref-link href
                    ""
                    {:class (str "cln-ref-heading cln-depth" (:depth node))})
    (do
      (logger/log :error
                  "RefHeading node does not have URL."
                  {:node node :base-name base-name})
      {:type "html" :value ""})))

(defmethod default-handler "refHeadingName"
  [node base-name]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)]
   :post [(s/valid? ::spec/node %)]}
  (if-let [href (:url node)]
    (build-ref-link href
                    (:caption node)
                    {:class (str "cln-ref-heading-name cln-depth"
                                 (:depth node))})
    (do
      (logger/log :error
                  "RefHeadingName node does not have URL."
                  {:node node :base-name base-name})
      {:type "html" :value ""})))

(defmethod default-handler "refTable"
  [node base-name]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)]
   :post [(s/valid? ::spec/node %)]}
  (if-let [target-id (-> node
                         :attributes
                         :id)]
    (build-ref-link (build-href target-id)
                    ""
                    {:class "cln-ref-table"})
    (do
      (logger/log :error
                  "RefTable node does not have ID."
                  {:node node :base-name base-name})
      {:type "html" :value ""})))

(defn build-table-html
  [id caption content]
  {:pre [(s/valid? ::spec/id id)
         (s/valid? ::spec/caption caption)
         (s/valid? ::spec/markdown content)]
   :post [(s/valid? ::spec/html %)]}
  (gstr/format (str "<div class=\"cln-table\" id=\"%s\">\n"
                    "<figcaption>%s</figcaption>\n\n%s\n\n</div>")
               id (gstr/htmlEscape caption) content))

(defmethod default-handler "table"
  [node base-name]
  {:pre [(s/valid? ::spec/node node)
         (s/valid? ::spec/file-name base-name)]
   :post [(s/valid? ::spec/node %)]}
  (let [id (-> node
               :attributes
               :id)
        children (:children node)]
    (if (and id (seq children))
      (let [caption (-> children
                        first
                        :children
                        first
                        :value)
            content (nodes->markdown (vec (rest children)) base-name)]
        (if caption
          {:type "html" :value (build-table-html id caption content)}
          (do
            (logger/log :error
                        "Table node does not have caption."
                        {:node node :base-name base-name})
            {:type "html" :value ""})))
      (do
        (logger/log :error
                    "Table node is invalid."
                    {:node node
                     :base-name base-name
                     :missing (cond
                                (not id) :id
                                (not (seq children)) :children)})
        {:type "html" :value ""}))))

(defmulti append-outer-div
  (fn [type _markdown]
    type))

(defmethod append-outer-div :default
  [type markdown]
  {:pre [(s/valid? ::spec/document-type type)
         (s/valid? ::spec/markdown markdown)]
   :post [(s/valid? ::spec/markdown %)]}
  (throw (ex-info "Unknown type in append-div."
                  {:type type :markdown markdown})))

(defmethod append-outer-div :forewords
  [type markdown]
  {:pre [(s/valid? ::spec/document-type type)
         (s/valid? ::spec/markdown markdown)]
   :post [(s/valid? ::spec/markdown %)]}
  (str "<div class=\"cln-foreword\">\n\n" markdown "\n\n</div>"))

(defmethod append-outer-div :chapters
  [type markdown]
  {:pre [(s/valid? ::spec/document-type type)
         (s/valid? ::spec/markdown markdown)]
   :post [(s/valid? ::spec/markdown %)]}
  (str "<div class=\"cln-chapter\">\n\n" markdown "\n\n</div>"))

(defmethod append-outer-div :appendices
  [type markdown]
  {:pre [(s/valid? ::spec/document-type type)
         (s/valid? ::spec/markdown markdown)]
   :post [(s/valid? ::spec/markdown %)]}
  (str "<div class=\"cln-appendix\">\n\n" markdown "\n\n</div>"))

(defmethod append-outer-div :afterwords
  [type markdown]
  {:pre [(s/valid? ::spec/document-type type)
         (s/valid? ::spec/markdown markdown)]
   :post [(s/valid? ::spec/markdown %)]}
  (str "<div class=\"cln-afterword\">\n\n" markdown "\n\n</div>"))

(defn render-documents
  [documents]
  {:pre [(s/valid? ::spec/documents documents)]
   :post [(s/valid? ::spec/manuscripts %)]}
  (vec
   (keep
    (fn [{:keys [name type ast]}]
      (try
        (let [finalized-ast (finalize-node ast (id/extract-base-name name))]
          (when finalized-ast
            {:name name
             :type type
             :markdown (append-outer-div type (ast->markdown finalized-ast))}))
        (catch js/Error e
          (throw (ex-info "Failed to render AST."
                          {:name name :ast ast :cause e})))))
    documents)))

(defn render-toc
  [toc-items]
  {:pre [(s/valid? ::spec/toc-items toc-items)]
   :post [(s/valid? ::spec/html %)]}
  (str "<nav id=\"toc\" role=\"doc-toc\">\n\n"
       "# 目次\n\n"
       (str/join
        "\n"
        (map (fn [{:keys [depth caption url]}]
               (str (str/join (repeat (* (- depth 1) 4) " "))
                    "- "
                    (build-link-html
                     url
                     caption
                     :attributes {:class (str "cln-ref-heading-name cln-depth"
                                              depth)})))
             toc-items))
       "\n\n</nav>"))

(defn build-index-caption-html
  [caption & {:keys [heading-level] :or {heading-level 2}}]
  {:pre [(s/valid? ::spec/index-caption caption)]
   :post [(s/valid? ::spec/html %)]}
  (gstr/format "<h%d>%s</h%d>" heading-level (:text caption) heading-level))

(defn build-index-page-html
  [urls]
  {:pre [(s/valid? ::spec/urls urls)]
   :post [(s/valid? ::spec/html %)]}
  (str/join ", "
            (map #(build-link-html % "" :attributes {:class "cln-index-page"})
                 urls)))

(defn build-index-item-html
  [item]
  {:pre [(s/valid? ::spec/index-item item)]
   :post [(s/valid? ::spec/html %)]}
  (str "<div class=\"cln-index-item\"><div class=\"cln-index-word\">"
       (:text item)
       "</div>"
       "<div class=\"cln-index-line-area\"><div class=\"cln-index-line\">"
       "</div></div><div class=\"cln-index-page-area\">"
       (build-index-page-html (:urls item))
       "</div></div>"))

(defn render-index-page
  [indices]
  {:pre [(s/valid? ::spec/indices indices)]
   :post [(s/valid? ::spec/html %)]}
  (str "<div class=\"cln-index\">\n"
       (str/join "\n"
                 (map (fn [index]
                        (case (:type index)
                          :caption (build-index-caption-html index)
                          :item (build-index-item-html index)
                          (throw (ex-info "Unexpected item type."
                                          {:index index}))))
                      indices))
       "</div>"))
