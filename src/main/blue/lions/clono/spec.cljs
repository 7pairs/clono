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
            [blue.lions.clono.spec.catalog :as catalog]
            [blue.lions.clono.spec.common :as common]
            [blue.lions.clono.spec.document :as document]
            [blue.lions.clono.spec.heading :as heading]
            [blue.lions.clono.spec.index :as index]
            [blue.lions.clono.spec.manuscript :as manuscript]
            [blue.lions.clono.spec.node :as node]
            [blue.lions.clono.spec.toc :as toc]))

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

(s/def ::depth
  (s/and integer?
         #(<= 1 % 6)))

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

(s/def ::function
  fn?)

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

(def valid-id?
  (partial valid-string? #{"\\" "/" ":" "*" "?" "\"" ">" "<"}))

(s/def ::id
  (s/and ::common/non-blank-string
         valid-id?))

(def index_ruby
  ::ruby)

(s/def ::index/text
  ::common/non-blank-string)

(s/def ::index/type
  #{:item :caption})

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

(s/def ::index-item
  (s/and (s/keys :req-un [::index/type
                          ::index/text
                          ::index/ruby
                          ::index/urls])
         #(every? #{:type :text :ruby :urls} (keys %))
         #(= (:type %) :item)))

(s/def ::indices
  (s/coll-of ::index :kind vector?))

(s/def ::log-data
  (s/and (s/or :map (s/and map?
                           (s/every-kv keyword? any?))
               :nil nil?)))

(s/def ::log-level
  #{:info :warn :error})

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

(s/def ::node-type
  ::common/alphabet-string)

(s/def ::nodes
  (s/coll-of ::node :kind vector?))

(s/def ::order
  (s/and integer?
         #(>= % 0)))

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

(s/def ::config
  config)

(s/def ::document/ast
  document_ast)

(s/def ::document/name
  document_name)

(s/def ::heading/id
  heading_id)

(s/def ::heading/url
  heading_url)

(s/def ::index/ruby
  index_ruby)

(s/def ::manuscript/markdown
  manuscript_markdown)

(s/def ::node/type
  node_type)

(s/def ::toc/url
  toc_url)
