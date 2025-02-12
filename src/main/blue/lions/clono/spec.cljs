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
            [blue.lions.clono.spec.common :as common]))

(s/def ::common/non-blank-string
  (s/and string?
         #(not (str/blank? %))))

(s/def ::common/non-nil-string
  (s/and string?
         (complement nil?)))

(defn- valid-string?
  [invalid-characters value]
  (not-any? invalid-characters (seq value)))

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

(s/def ::markdown
  ::common/non-nil-string)

(s/def ::config
  config)
