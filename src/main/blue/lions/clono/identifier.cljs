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

(ns blue.lions.clono.identifier
  (:require [cljs.spec.alpha :as s]
            [clojure.string :as str]
            ["path" :as path]
            [blue.lions.clono.log :as logger]
            [blue.lions.clono.spec :as spec]))

(defn extract-base-name
  [file-path]
  {:pre [(or (s/valid? ::spec/file-path file-path)
             (throw (ex-info "Invalid file path." {:file-path file-path})))]
   :post [(s/valid? ::spec/file-name %)]}
  (let [ext (path/posix.extname file-path)]
    (path/posix.basename file-path ext)))

(defn build-url
  [base-name id & {:keys [extension separator]
                   :or {extension ".html" separator "#"}}]
  {:pre [(or (s/valid? ::spec/file-name base-name)
             (throw (ex-info "Invalid base name." {:base-name base-name})))
         (or (s/valid? ::spec/id id)
             (throw (ex-info "Invalid ID." {:id id})))]
   :post [(s/valid? ::spec/url %)]}
  (str base-name extension separator (js/encodeURIComponent id)))

(defn build-dic-key
  [base-name id]
  {:pre [(or (s/valid? ::spec/file-name base-name)
             (throw (ex-info "Invalid base name." {:base-name base-name})))
         (or (s/valid? ::spec/id id)
             (throw (ex-info "Invalid ID." {:id id})))]
   :post [(s/valid? ::spec/id %)]}
  (let [key (str base-name "|" id)]
    (when (> (count key) 100)
      (logger/debug "Generated very long dictionary key."
                    {:base-name base-name :id id}))
    key))

(defn parse-dic-key
  [key]
  {:pre [(or (s/valid? ::spec/id key)
             (throw (ex-info "Invalid dictionary key." {:key key})))]
   :post [(s/valid? ::spec/anchor-info %)]}
  (let [[value1 value2] (str/split key #"\|" 2)]
    {:chapter (when (and (seq value1) (seq value2)) value1)
     :id (if (seq value2) value2 value1)}))
