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

(ns blue.lions.clono.spec
  (:require [cljs.spec.alpha :as s]
            [clojure.string :as str]
            [blue.lions.clono.spec.anchor :as anchor]
            [blue.lions.clono.spec.catalog :as catalog]
            [blue.lions.clono.spec.common :as common]
            [blue.lions.clono.spec.custom :as custom]
            [blue.lions.clono.spec.directive :as directive]
            [blue.lions.clono.spec.document :as document]
            [blue.lions.clono.spec.heading :as heading]
            [blue.lions.clono.spec.index :as index]
            [blue.lions.clono.spec.log :as log]
            [blue.lions.clono.spec.manuscript :as manuscript]
            [blue.lions.clono.spec.node :as node]
            [blue.lions.clono.spec.toc :as toc]))

(defn validate
  ([spec value message]
   (validate spec value message :value))
  ([spec value message key]
   (or (s/valid? spec value)
       (throw (ex-info message {key value :spec spec})))))

(s/def ::common/alphabet-string
  (s/and string?
         #(re-matches #"[a-zA-Z]+" %)))

(s/def ::common/non-blank-string
  (s/and string?
         #(not (str/blank? %))))

(s/def ::common/non-nil-string
  (s/and string?
         (complement nil?)))

(defn- valid-string?
  [invalid-chars value]
  (not-any? invalid-chars (seq value)))

(def anchor_chapter
  (s/or :id ::id
        :nil nil?))

(def anchor_id
  ::id)

(s/def ::anchor-info
  (s/and (s/keys :req-un [::anchor/chapter
                          ::anchor/id])
         #(every? #{:chapter :id} (keys %))))

(s/def ::anchor-text
  ::common/non-nil-string)

(s/def ::attributes
  (s/map-of keyword? ::common/non-blank-string))

(s/def ::caption
  ::common/non-blank-string)

(s/def ::catalog/afterwords
  (s/coll-of ::file-name :kind vector?))

(s/def ::catalog/appendices
  (s/coll-of ::file-name :kind vector?))

(s/def ::catalog/chapters
  (s/coll-of ::file-name :kind vector?))

(s/def ::catalog/forewords
  (s/coll-of ::file-name :kind vector?))

(s/def ::catalog
  (s/and (s/keys :opt-un [::catalog/forewords
                          ::catalog/chapters
                          ::catalog/appendices
                          ::catalog/afterwords])
         (fn [key]
           (some #(contains? key %)
                 [:forewords :chapters :appendices :afterwords]))))

(def config
  ::edn)

(s/def ::custom/caption
  ::caption)

(s/def ::custom/default
  boolean?)

(s/def ::custom/language
  #{:english :japanese})

(def custom_pattern
  ::pattern-string)

(s/def ::custom-group
  (s/and (s/keys :req-un [::custom/caption]
                 :opt-un [::custom/pattern
                          ::custom/language
                          ::custom/default])
         #(or (:pattern %) (:default %))
         #(every? #{:caption :pattern :language :default} (keys %))))

(s/def ::custom-groups
  (s/coll-of ::custom-group :kind vector?))

(s/def ::depth
  (s/and integer?
         #(<= 1 % 6)))

(s/def ::dic
  (s/map-of ::common/non-blank-string any?))

(s/def ::dics
  (s/map-of keyword? ::dic))

(def directive_name
  ::directive-name)

(s/def ::directive/type
  #{"textDirective" "containerDirective"})

(s/def ::directive-node
  (s/and ::node
         (s/keys :req-un [::directive/type
                          ::directive/name])))

(s/def ::directive-name
  ::common/alphabet-string)

(def document_ast
  ::node)

(def document_name
  ::file-name)

(s/def ::document/type
  #{:forewords :chapters :appendices :afterwords})

(s/def ::document
  (s/and (s/keys :req-un [::document/name
                          ::document/type
                          ::document/ast])
         #(every? #{:name :type :ast} (keys %))))

(s/def ::document-type
  #{:forewords :chapters :appendices :afterwords})

(s/def ::documents
  (s/coll-of ::document :kind vector?))

(s/def ::edn
  (s/and map?
         (complement nil?)
         (s/every-kv keyword? any?)))

(s/def ::file-content
  ::common/non-nil-string)

(def valid-file-name?
  (partial valid-string? #{"\\" "/" ":" "*" "?" "\"" ">" "<" "|"}))

(s/def ::file-name
  (s/and ::common/non-blank-string
         valid-file-name?))

(def valid-file-path? (partial valid-string? #{"*" "?" "\"" ">" "<" "|"}))

(s/def ::file-path
  (s/and ::common/non-blank-string
         valid-file-path?))

(s/def ::footnote-dic
  (s/and map?
         (complement nil?)
         (s/every-kv ::id ::node)))

(s/def ::formatted-attributes
  ::common/non-nil-string)

(s/def ::function
  fn?)

(s/def ::function-or-nil
  (s/or :function ::function
        :nil nil?))

(s/def ::github-slugger
  (fn [value]
    (and (some? value)
         (not (nil? value))
         (fn? (.-slug ^js value))
         (fn? (.-reset ^js value)))))

(s/def ::heading/caption
  ::caption)

(s/def ::heading/depth
  ::depth)

(def heading_id
  ::id)

(def heading_url
  ::url)

(s/def ::heading
  (s/and (s/keys :req-un [::heading/id
                          ::heading/depth
                          ::heading/caption
                          ::heading/url])
         #(every? #{:id :depth :caption :url} (keys %))))

(s/def ::heading-dic
  (s/and map?
         (complement nil?)
         (s/every-kv ::id ::heading)))

(s/def ::heading-or-nil
  (s/or :heading ::heading
        :nil nil?))

(s/def ::html
  ::common/non-nil-string)

(def valid-id?
  (partial valid-string? #{"\\" "/" ":" "*" "?" "\"" ">" "<"}))

(s/def ::id
  (s/and ::common/non-blank-string
         valid-id?))

(s/def ::id-or-nil
  (s/or :id ::id
        :nil nil?))

(s/def ::index/caption
  ::caption)

(s/def ::index/default
  boolean?)

(s/def ::index/language
  #{:english :japanese})

(def index_order
  ::order)

(def index_pattern
  ::pattern)

(def index_ruby
  ::ruby)

(s/def ::index/text
  ::common/non-blank-string)

(s/def ::index/type
  #{:item :caption})

(def index_url
  ::url)

(s/def ::index/urls
  (s/coll-of ::url :kind vector? :min-count 1))

(s/def ::index
  (s/or :item ::index-item
        :caption ::index-caption))

(s/def ::index-caption
  (s/and (s/keys :req-un [::index/type
                          ::index/text])
         #(every? #{:type :text} (keys %))
         #(= (:type %) :caption)))

(s/def ::index-entry
  (s/and (s/keys :req-un [::index/order
                          ::index/text
                          ::index/ruby
                          ::index/url])
         #(every? #{:order :text :ruby :url} (keys %))))

(s/def ::index-group
  (s/and (s/keys :req-un [::index/caption]
                 :opt-un [::index/pattern
                          ::index/language
                          ::index/default])
         #(or (:pattern %) (:default %))
         #(every? #{:caption :pattern :language :default} (keys %))))

(s/def ::index-groups
  (s/coll-of ::index-group :kind vector?))

(s/def ::index-item
  (s/and (s/keys :req-un [::index/type
                          ::index/text
                          ::index/ruby
                          ::index/urls])
         #(every? #{:type :text :ruby :urls} (keys %))
         #(= (:type %) :item)))

(s/def ::indices
  (s/coll-of ::index :kind vector?))

(def log_data
  ::log-data)

(def log_level
  ::log-level)

(def log_message
  ::log-message)

(s/def ::log-enabled
  boolean?)

(s/def ::log-data
  (s/and (s/or :map (s/and map?
                           (s/every-kv keyword? any?))
               :nil nil?)))

(s/def ::log-entries
  (s/coll-of ::log-entry :kind vector?))

(s/def ::log-entry
  (s/and (s/keys :req-un [::log/level
                          ::log/message
                          ::log/data])
         #(every? #{:level :message :data} (keys %))))

(s/def ::log-level
  #{:debug :info :warn :error})

(s/def ::log-level-value
  (s/and integer?
         #(<= 0 % 3)))

(s/def ::log-message
  ::common/non-nil-string)

(def manuscript_markdown
  ::markdown)

(s/def ::manuscript/name
  ::file-name)

(s/def ::manuscript/type
  #{:forewords :chapters :appendices :afterwords})

(s/def ::manuscript
  (s/and (s/keys :req-un [::manuscript/name
                          ::manuscript/type
                          ::manuscript/markdown])
         #(every? #{:name :type :markdown} (keys %))))

(s/def ::manuscripts
  (s/coll-of ::manuscript :kind vector?))

(s/def ::markdown
  ::common/non-nil-string)

(s/def ::module-name
  (s/and string?
         #(re-matches #"[a-zA-Z0-9@/\-._]+" %)))

(def node_type
  ::node-type)

(s/def ::node
  (s/keys :req-un [::node/type]))

(s/def ::node-or-nil
  (s/or :node ::node
        :nil nil?))

(s/def ::node-type
  ::common/alphabet-string)

(s/def ::nodes
  (s/coll-of ::node :kind vector?))

(s/def ::order
  (s/and integer?
         #(>= % 0)))

(s/def ::pattern
  #(instance? js/RegExp %))

(s/def ::pattern-string
  ::common/non-blank-string)

(s/def ::pred-result
  boolean?)

(s/def ::property-name
  (s/and string?
         #(re-matches #"[a-zA-Z_$][a-zA-Z0-9_$]*" %)))

(s/def ::ruby
  ::common/non-blank-string)

(def valid-slug?
  (partial valid-string? #{"!" "\"" "#" "$" "%" "&" "'" "(" ")" "*" "+" "," "."
                           "/" ":" ";" "<" "=" ">" "?" "@" "[" "\\" "]" "^" "`"
                           "{" "|" "}" "~" " "}))

(s/def ::slug
  (s/and ::common/non-blank-string
         valid-slug?))

(s/def ::toc/caption
  ::caption)

(s/def ::toc/depth
  ::depth)

(def toc_url
  ::url)

(s/def ::toc-item
  (s/and (s/keys :req-un [::toc/depth
                          ::toc/caption
                          ::toc/url])
         #(every? #{:depth :caption :url} (keys %))))

(s/def ::toc-items
  (s/coll-of ::toc-item :kind vector?))

(def valid-url?
  (partial valid-string? #{"\\" "/" ":" "*" "?" "\"" ">" "<"}))

(s/def ::url
  (s/and ::common/non-blank-string
         valid-url?))

(s/def ::urls
  (s/coll-of ::url :kind vector?))

(s/def ::anchor/chapter
  anchor_chapter)

(s/def ::anchor/id
  anchor_id)

(s/def ::config
  config)

(s/def ::custom/pattern
  custom_pattern)

(s/def ::directive/name
  directive_name)

(s/def ::document/ast
  document_ast)

(s/def ::document/name
  document_name)

(s/def ::heading/id
  heading_id)

(s/def ::heading/url
  heading_url)

(s/def ::index/order
  index_order)

(s/def ::index/pattern
  index_pattern)

(s/def ::index/ruby
  index_ruby)

(s/def ::index/url
  index_url)

(s/def ::log/data
  log_data)

(s/def ::log/level
  log_level)

(s/def ::log/message
  log_message)

(s/def ::manuscript/markdown
  manuscript_markdown)

(s/def ::node/type
  node_type)

(s/def ::toc/url
  toc_url)
