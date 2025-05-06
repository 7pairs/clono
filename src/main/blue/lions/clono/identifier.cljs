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
  (:require [clojure.string :as str]
            ["path" :as path]
            [blue.lions.clono.log :as logger]
            [blue.lions.clono.spec :as spec]))

(defn extract-base-name
  [file-path]
  {:pre [(spec/validate ::spec/file-path file-path
                        "Invalid file path is given.")]
   :post [(spec/validate ::spec/file-name % "Invalid file name is returned.")]}
  (let [ext (path/posix.extname file-path)]
    (path/posix.basename file-path ext)))

(defn build-url
  [base-name id & {:keys [extension separator]
                   :or {extension ".html" separator "#"}}]
  {:pre [(spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")
         (spec/validate ::spec/id id "Invalid ID is given.")]
   :post [(spec/validate ::spec/url % "Invalid URL is returned.")]}
  (str (js/encodeURIComponent base-name)
       extension
       separator
       (js/encodeURIComponent id)))

(defn build-dic-key
  [base-name id & {:keys [separator]
                   :or {separator "|"}}]
  {:pre [(spec/validate ::spec/file-name base-name
                        "Invalid base name is given.")
         (spec/validate ::spec/id id "Invalid ID is given.")]
   :post [(spec/validate ::spec/id % "Invalid ID is returned.")]}
  (let [key (str base-name separator id)]
    (when (> (count key) 100)
      (logger/warn "Generated very long dictionary key."
                   {:base-name base-name :id id}))
    key))

(defn parse-dic-key
  [key & {:keys [separator]
          :or {separator #"\|"}}]
  {:pre [(spec/validate ::spec/id key "Invalid dictionary key is given.")]
   :post [(spec/validate ::spec/anchor-info %
                         "Invalid anchor information is returned.")]}
  (let [[value1 value2] (str/split key separator 2)]
    {:chapter (when (and (seq value1) (seq value2)) value1)
     :id (if (seq value2) value2 value1)}))
