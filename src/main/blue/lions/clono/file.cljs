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

(ns blue.lions.clono.file
  (:require [cljs.reader :as reader]
            [cljs.spec.alpha :as s]
            ["fs" :as fs]
            ["path" :as path]
            [blue.lions.clono.spec :as spec]))

(defn extract-base-name
  [file-path]
  {:pre [(s/valid? ::spec/file-path file-path)]
   :post [(s/valid? ::spec/file-name %)]}
  (let [ext (path/extname file-path)]
    (path/basename file-path ext)))

(defn read-file
  [file-path]
  {:pre [(s/valid? ::spec/file-path file-path)]
   :post [(s/valid? ::spec/file-content %)]}
  (try
    (when-not (fs/existsSync file-path)
      (throw (ex-info "File does not exist." {:file-path file-path})))
    (fs/readFileSync file-path "utf8")
    (catch js/Error e
      (throw (ex-info "Failed to read file."
                      {:file-path file-path :cause e})))))

(defn read-edn-file
  [file-path]
  {:pre [(s/valid? ::spec/file-path file-path)]
   :post [(s/valid? ::spec/edn %)]}
  (try
    (let [content (read-file file-path)
          edn (reader/read-string content)]
      (when (nil? edn)
        (throw (ex-info "EDN file is empty or invalid."
                        {:file-path file-path :content content})))
      edn)
    (catch js/Error e
      (throw (ex-info "Failed to read or parse EDN file."
                      {:file-path file-path :cause e})))))

(defn read-config-file
  [file-path]
  {:pre [(s/valid? ::spec/file-path file-path)]
   :post [(s/valid? ::spec/config %)]}
  (try
    (read-edn-file file-path)
    (catch js/Error e
      (throw (ex-info "Failed to read config file."
                      {:file-path file-path :cause e})))))

(defn read-catalog-file
  [file-path]
  {:pre [(s/valid? ::spec/file-path file-path)]
   :post [(s/valid? ::spec/catalog %)]}
  (try
    (read-edn-file file-path)
    (catch js/Error e
      (throw (ex-info "Failed to read catalog file."
                      {:file-path file-path :cause e})))))
