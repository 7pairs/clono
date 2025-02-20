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
            [blue.lions.clono.spec.manuscript :as manuscript]
            [blue.lions.clono.spec.node :as node]))

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

(s/def ::caption
  ::common/non-blank-string)

(def config
  ::edn)

(s/def ::directive-name
  ::common/alphabet-string)

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

(s/def ::function
  fn?)

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

(def valid-slug?
  (partial valid-string? #{"!" "\"" "#" "$" "%" "&" "'" "(" ")" "*" "+" "," "."
                           "/" ":" ";" "<" "=" ">" "?" "@" "[" "\\" "]" "^" "`"
                           "{" "|" "}" "~" " "}))

(s/def ::slug
  (s/and ::common/non-blank-string
         valid-slug?))

(s/def ::config
  config)

(s/def ::manuscript/markdown
  manuscript_markdown)

(s/def ::node/type
  node_type)
