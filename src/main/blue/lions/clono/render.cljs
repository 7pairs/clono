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
  (:require [clojure.string :as str]
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
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  node)

(defn load-plugin
  [plugin-dir type]
  {:pre [(spec/validate ::spec/file-path plugin-dir
                        "Invalid directory is given.")
         (spec/validate ::spec/node-type type "Invalid node type is given.")]
   :post [(spec/validate ::spec/function-or-nil %
                         "Invalid function is returned.")]}
  (let [plugin-path (path/join plugin-dir (str type ".js"))]
    (try
      ; Node.js's require function has its own internal caching mechanism.
      ; Subsequent calls with the same path will return the cached module 
      ; instead of reading the file again, improving performance.
      (let [plugin (js/require plugin-path)]
        (if (and (object? plugin) (empty? (js/Object.keys plugin)))
          (do
            (logger/warn "Invalid JavaScript file is detected."
                         {:plugin-path plugin-path :type type})
            nil)
          plugin))
      (catch js/Error _
        nil))))

(defn apply-plugin-or-default
  [node base-name & {:keys [plugin-dir]
                     :or {plugin-dir (path/join js/__dirname "plugins")}}]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (let [type (ast/get-type node)]
    (or (when-let [plugin (load-plugin plugin-dir type)]
          (try
            (let [result (plugin (clj->js node) base-name)]
              (if (string? result)
                {:type "html" :value result}
                (throw (ex-info "Plugin returns invalid value."
                                {:type type :result result}))))
            (catch js/Error e
              (logger/warn "Plugin execution failed, using default logic."
                           {:type type :node node :cause (ex-message e)})
              nil)))
        (default-handler node base-name))))

(defn- finalize-node-with-depth
  [node base-name depth]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")
         (spec/validate ::spec/order depth "Invalid depth is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (when-let [updated-node (apply-plugin-or-default node base-name)]
    (if (> depth 1000)
      (do
        (logger/warn "Maximum recursion depth reached in finalize-node."
                     {:node-type (:type node) :depth depth})
        updated-node)
      (let [new-children (->> (:children updated-node)
                              (keep #(finalize-node-with-depth %
                                                               base-name
                                                               (inc depth)))
                              vec)]
        (if (seq new-children)
          (assoc updated-node :children new-children)
          (dissoc updated-node :children))))))

(defn finalize-node
  [node base-name]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (finalize-node-with-depth node base-name 0))

(defn ast->markdown
  [ast]
  {:pre [(spec/validate ::spec/node ast "Invalid AST is given.")]
   :post [(spec/validate ::spec/markdown % "Invalid Markdown is returned.")]}
  (try
    (let [converted-ast (clj->js ast {:keywordize-keys false})]
      (esm/to-markdown converted-ast))
    (catch js/Error e
      (throw (ex-info "Failed to convert AST to Markdown."
                      {:ast ast :cause e})))))

(defn nodes->markdown
  [nodes base-name]
  {:pre [(spec/validate ::spec/nodes nodes "Invalid nodes are given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")]
   :post [(spec/validate ::spec/markdown % "Invalid Markdown is returned.")]}
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
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")]
   :post [(spec/validate ::spec/markdown % "Invalid Markdown is returned.")]}
  (try
    (nodes->markdown [node] base-name)
    (catch js/Error e
      (throw (ex-info "Failed to convert node to Markdown."
                      {:node node :base-name base-name :cause e})))))

(defn format-attributes-for-html
  [attributes]
  {:pre [(spec/validate ::spec/attributes attributes
                        "Invalid attributes are given.")]
   :post [(spec/validate ::spec/formatted-attributes %
                         "Invalid attributes are returned.")]}
  (->> attributes
       (remove (comp nil? second))
       (map (fn [[k v]]
              (str (name k) "=\"" (gstr/htmlEscape v) "\"")))
       (str/join " ")))

(defn format-attributes-for-markdown
  [attributes]
  {:pre [(spec/validate ::spec/attributes attributes
                        "Invalid attributes are given.")]
   :post [(spec/validate ::spec/formatted-attributes %
                         "Invalid attributes are returned.")]}
  (->> attributes
       (remove (comp nil? second))
       (map (fn [[k v]]
              (str (name k) "=" (gstr/htmlEscape v))))
       (str/join " ")))

(defn build-href
  [key]
  {:pre [(spec/validate ::spec/id key "Invalid key is given.")]
   :post [(spec/validate ::spec/url % "Invalid URL is returned.")]}
  (let [{:keys [chapter id]} (id/parse-dic-key key)]
    (if (seq id)
      (if chapter
        (gstr/format "%s.html#%s" chapter id)
        (gstr/format "#%s" id))
      "")))

(defn build-link-html
  [href text & {:keys [attributes] :or {attributes {}}}]
  {:pre [(spec/validate ::spec/url href "Invalid URL is given.")
         (spec/validate ::spec/anchor-text text "Invalid text is given.")]
   :post [(spec/validate ::spec/html % "Invalid HTML is returned.")]}
  (let [attr-str (format-attributes-for-html attributes)]
    (if (seq href)
      (gstr/format "<a href=\"%s\"%s>%s</a>"
                   (gstr/htmlEscape href)
                   (if (seq attr-str) (str " " attr-str) "")
                   (gstr/htmlEscape text))
      (gstr/htmlEscape text))))

(defn build-code-html
  [content id]
  {:pre [(spec/validate ::spec/markdown content "Invalid Markdown is given.")
         (spec/validate ::spec/id-or-nil id "Invalid ID is given.")]
   :post [(spec/validate ::spec/html % "Invalid HTML is returned.")]}
  (gstr/format "<div class=\"cln-code\"%s>\n\n%s\n\n</div>"
               (if (seq id) (gstr/format " id=\"%s\"" id) "")
               content))

(defmethod default-handler "code"
  [node base-name]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
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
  {:pre [(spec/validate ::spec/caption caption "Invalid caption is given.")
         (spec/validate ::spec/markdown content "Invalid Markdown is given.")]
   :post [(spec/validate ::spec/html % "Invalid HTML is returned.")]}
  (gstr/format "<div class=\"cln-column\">\n\n%s %s\n\n%s\n\n</div>"
               (str/join (repeat heading-level "#")) caption content))

(defmethod default-handler "column"
  [node base-name]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
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
            (logger/error "Column node does not have caption."
                          {:node node :base-name base-name})
            {:type "html" :value ""})))
      (do
        (logger/error "Column node does not have children."
                      {:node node :base-name base-name})
        {:type "html" :value ""}))))

(defn build-image-markdown
  [caption file-path id attributes]
  {:pre [(spec/validate ::spec/caption caption "Invalid caption is given.")
         (spec/validate ::spec/file-path
                        file-path "Invalid file path is given.")
         (spec/validate ::spec/id id "Invalid ID is given.")
         (spec/validate ::spec/attributes attributes
                        "Invalid attributes are given.")]
   :post [(spec/validate ::spec/markdown % "Invalid Markdown is returned.")]}
  (let [id-str (when (seq id) (str "id=" id))
        attr-str (format-attributes-for-markdown attributes)
        brace-content (str/join " " (remove empty? [id-str attr-str]))]
    (if (seq brace-content)
      (gstr/format "![%s](%s){%s}" caption file-path brace-content)
      (gstr/format "![%s](%s)" caption file-path))))

(defmethod default-handler "figure"
  [node base-name]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
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
        (logger/error "Figure node is invalid."
                      {:node node
                       :base-name base-name
                       :missing (cond
                                  (nil? child) :children
                                  (nil? src) :src)})
        {:type "html" :value ""}))))

(defn build-footnote-html
  [footnote]
  {:pre [(spec/validate ::spec/markdown footnote "Invalid Markdown is given.")]
   :post [(spec/validate ::spec/html % "Invalid HTML is returned.")]}
  (if (seq footnote)
    (gstr/format "<span class=\"cln-footnote\">%s</span>" footnote)
    ""))

(defmethod default-handler "footnoteReference"
  [node base-name]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (let [value (if-let [child (-> node
                                 :children
                                 first)]
                (build-footnote-html (node->markdown child base-name))
                (do
                  (logger/error
                   "FootnoteReference node does not have children."
                   {:node node :base-name base-name})
                  ""))]
    {:type "html" :value value}))

(defn build-index-html
  [id content strong?]
  {:pre [(spec/validate ::spec/id-or-nil id "Invalid ID is given.")
         (spec/validate ::spec/markdown content "Invalid Markdown is given.")
         (spec/validate ::spec/pred-result strong? "Invalid result is given.")]
   :post [(spec/validate ::spec/html % "Invalid HTML is returned.")]}
  (let [content (if (seq id)
                  (gstr/format "<span id=\"%s\">%s</span>"
                               (gstr/htmlEscape id) content)
                  (gstr/format "<span>%s</span>" content))]
    (if strong?
      (gstr/format "<strong>%s</strong>" content)
      content)))

(defmethod default-handler "index"
  [node base-name]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (let [child (-> node
                  :children
                  first)]
    (if child
      {:type "html"
       :value (build-index-html (:id node)
                                (node->markdown child base-name)
                                (contains? (:attributes node) :strong))}
      (do
        (logger/error "Index node does not have children."
                      {:node node :base-name base-name})
        {:type "html" :value ""}))))

(defn build-ref-link
  [href text attributes]
  {:pre [(spec/validate ::spec/url href "Invalid URL is given.")
         (spec/validate ::spec/anchor-text text "Invalid text is given.")
         (spec/validate ::spec/attributes attributes
                        "Invalid attributes are given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (try
    (let [html (build-link-html href text :attributes attributes)]
      {:type "html" :value html})
    (catch js/Error e
      (logger/error "Failed to build ref link."
                    {:href href
                     :text text
                     :attributes attributes
                     :cause (ex-message e)})
      {:type "html" :value ""})))

(defmethod default-handler "refCode"
  [node base-name]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (if-let [target-id (-> node
                         :attributes
                         :id)]
    (build-ref-link (build-href target-id)
                    ""
                    {:class "cln-ref-code"})
    (do
      (logger/error "RefCode node does not have ID."
                    {:node node :base-name base-name})
      {:type "html" :value ""})))

(defmethod default-handler "refFigure"
  [node base-name]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (if-let [target-id (-> node
                         :attributes
                         :id)]
    (build-ref-link (build-href target-id)
                    ""
                    {:class "cln-ref-figure"})
    (do
      (logger/error "RefFigure node does not have ID."
                    {:node node :base-name base-name})
      {:type "html" :value ""})))

(defmethod default-handler "refHeading"
  [node base-name]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (if-let [href (:url node)]
    (build-ref-link href
                    ""
                    {:class (str "cln-ref-heading cln-level" (:depth node))})
    (do
      (logger/error "RefHeading node does not have URL."
                    {:node node :base-name base-name})
      {:type "html" :value ""})))

(defmethod default-handler "refHeadingName"
  [node base-name]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (if-let [href (:url node)]
    (build-ref-link href
                    (:caption node)
                    {:class (str "cln-ref-heading-name cln-level"
                                 (:depth node))})
    (do
      (logger/error "RefHeadingName node does not have URL."
                    {:node node :base-name base-name})
      {:type "html" :value ""})))

(defmethod default-handler "refTable"
  [node base-name]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
  (if-let [target-id (-> node
                         :attributes
                         :id)]
    (build-ref-link (build-href target-id)
                    ""
                    {:class "cln-ref-table"})
    (do
      (logger/error "RefTable node does not have ID."
                    {:node node :base-name base-name})
      {:type "html" :value ""})))

(defn build-table-html
  [id caption content]
  {:pre [(spec/validate ::spec/id id "Invalid ID is given.")
         (spec/validate ::spec/caption caption "Invalid caption is given.")
         (spec/validate ::spec/markdown content "Invalid Markdown is given.")]
   :post [(spec/validate ::spec/html % "Invalid HTML is returned.")]}
  (gstr/format (str "<div class=\"cln-table\" id=\"%s\">\n"
                    "<figcaption>%s</figcaption>\n\n%s\n\n</div>")
               id (gstr/htmlEscape caption) content))

(defmethod default-handler "table"
  [node base-name]
  {:pre [(spec/validate ::spec/node node "Invalid node is given.")
         (spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")]
   :post [(spec/validate ::spec/node % "Invalid node is returned.")]}
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
            (logger/error "Table node does not have caption."
                          {:node node :base-name base-name})
            {:type "html" :value ""})))
      (do
        (logger/error "Table node is invalid."
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
  {:pre [(spec/validate ::spec/document-type type
                        "Invalid document type is given.")
         (spec/validate ::spec/markdown markdown "Invalid Markdown is given.")]
   :post [(spec/validate ::spec/markdown % "Invalid Markdown is returned.")]}
  (throw (ex-info "Unknown type in append-div."
                  {:type type :markdown (subs markdown
                                              0 (min (count markdown) 30))})))

(defmethod append-outer-div :forewords
  [type markdown]
  {:pre [(spec/validate ::spec/document-type type
                        "Invalid document type is given.")
         (spec/validate ::spec/markdown markdown "Invalid Markdown is given.")]
   :post [(spec/validate ::spec/markdown % "Invalid Markdown is returned.")]}
  (str "<div class=\"cln-foreword\">\n\n" markdown "\n\n</div>"))

(defmethod append-outer-div :chapters
  [type markdown]
  {:pre [(spec/validate ::spec/document-type type
                        "Invalid document type is given.")
         (spec/validate ::spec/markdown markdown "Invalid Markdown is given.")]
   :post [(spec/validate ::spec/markdown % "Invalid Markdown is returned.")]}
  (str "<div class=\"cln-chapter\">\n\n" markdown "\n\n</div>"))

(defmethod append-outer-div :appendices
  [type markdown]
  {:pre [(spec/validate ::spec/document-type type
                        "Invalid document type is given.")
         (spec/validate ::spec/markdown markdown "Invalid Markdown is given.")]
   :post [(spec/validate ::spec/markdown % "Invalid Markdown is returned.")]}
  (str "<div class=\"cln-appendix\">\n\n" markdown "\n\n</div>"))

(defmethod append-outer-div :afterwords
  [type markdown]
  {:pre [(spec/validate ::spec/document-type type
                        "Invalid document type is given.")
         (spec/validate ::spec/markdown markdown "Invalid Markdown is given.")]
   :post [(spec/validate ::spec/markdown % "Invalid Markdown is returned.")]}
  (str "<div class=\"cln-afterword\">\n\n" markdown "\n\n</div>"))

(defn render-documents
  [documents]
  {:pre [(spec/validate ::spec/documents documents
                        "Invalid documents are given.")]
   :post [(spec/validate ::spec/manuscripts %
                         "Invalid manuscripts are returned.")]}
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
  {:pre [(spec/validate ::spec/toc-items toc-items "Invalid items are given.")]
   :post [(spec/validate ::spec/html % "Invalid HTML is returned.")]}
  (str "<nav id=\"cln-toc\" role=\"doc-toc\">\n\n"
       "# 目次\n\n"
       (str/join
        "\n"
        (map (fn [{:keys [depth caption url]}]
               (str (str/join (repeat (* (- depth 1) 4) " "))
                    "- "
                    "<span class=\"cln-toc-item\">"
                    (build-link-html
                     url
                     caption
                     :attributes {:class (str "cln-ref-heading-name cln-level"
                                              depth)})
                    "<span class=\"cln-toc-line\"></span>"
                    (build-link-html
                     url
                     ""
                     :attributes {:class "cln-toc-page"})
                    "</span>"))
             toc-items))
       "\n\n</nav>"))

(defn build-index-caption-html
  [caption & {:keys [heading-level]
              :or {heading-level 2}}]
  {:pre [(spec/validate ::spec/index-caption caption
                        "Invalid caption is given.")]
   :post [(spec/validate ::spec/html % "Invalid HTML is returned.")]}
  (gstr/format "<h%d>%s</h%d>" heading-level (:text caption) heading-level))

(defn build-index-page-html
  [urls]
  {:pre [(spec/validate ::spec/urls urls "Invalid URLs are given.")]
   :post [(spec/validate ::spec/html % "Invalid HTML is returned.")]}
  (str/join ", "
            (map #(build-link-html % "" :attributes {:class "cln-index-page"})
                 urls)))

(defn build-index-item-html
  [item]
  {:pre [(spec/validate ::spec/index-item item "Invalid item is given.")]
   :post [(spec/validate ::spec/html % "Invalid HTML is returned.")]}
  (str "<div class=\"cln-index-item\">"
       "<div class=\"cln-index-word\">" (:text item) "</div>"
       "<div class=\"cln-index-line\"></div>"
       "<div class=\"cln-index-pages-area\">"
       (build-index-page-html (:urls item))
       "</div></div>"))

(defn render-index-page
  [indices]
  {:pre [(spec/validate ::spec/indices indices "Invalid indices are given.")]
   :post [(spec/validate ::spec/html % "Invalid HTML is returned.")]}
  (str "<div class=\"cln-index\">\n\n"
       "# 索引\n\n"
       (str/join "\n"
                 (map (fn [index]
                        (case (:type index)
                          :caption (build-index-caption-html index)
                          :item (build-index-item-html index)
                          (throw (ex-info "Unexpected item type."
                                          {:index index}))))
                      indices))
       "</div>"))
