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
            [clojure.string :as str]))

(s/def :blue.lions.clono.spec.common/non-blank-string
  (s/and string?
         #(not (str/blank? %))))

(s/def :blue.lions.clono.spec.common/non-nil-string
  (s/and string?
         (complement nil?)))

(defn- valid-string?
  [invalid-chars target]
  (not-any? invalid-chars (seq target)))

(def delayed-config ::edn)

(s/def ::edn
  (s/and map?
         (complement nil?)
         (s/every-kv keyword? any?)))

(s/def ::file-content
  :blue.lions.clono.spec.common/non-nil-string)

(def valid-file-path? (partial valid-string? #{"*" "?" "\"" ">" "<" "|"}))

(s/def ::file-path
  (s/and :blue.lions.clono.spec.common/non-blank-string
         valid-file-path?))

(s/def ::id
  (s/and :blue.lions.clono.spec.common/non-blank-string
         valid-id?))
(s/def ::config
  delayed-config)

