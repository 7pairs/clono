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
            [blue.lions.clono.log :as logger]
            [blue.lions.clono.spec :as spec]))

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

(defn read-markdown-file
  [file-path]
  {:pre [(s/valid? ::spec/file-path file-path)]
   :post [(s/valid? ::spec/markdown %)]}
  (try
    (read-file file-path)
    (catch js/Error e
      (throw (ex-info "Failed to read Markdown file."
                      {:file-path file-path :cause e})))))

(def valid-manuscript-types #{:forewords :chapters :appendices :afterwords})

(defn read-markdown-files
  [dir-path catalog]
  {:pre [(s/valid? ::spec/file-path dir-path)
         (s/valid? ::spec/catalog catalog)]
   :post [(s/valid? ::spec/manuscripts %)]}
  (vec
   (mapcat
    (fn [[type file-names]]
      (if (valid-manuscript-types type)
        (keep (fn [file-name]
                (let [file-path (path/join dir-path file-name)]
                  (try
                    {:name file-name
                     :type type
                     :markdown (read-markdown-file file-path)}
                    (catch js/Error e
                      (logger/log :error
                                  "Failed to read Markdown file."
                                  {:file-path file-path :cause (ex-message e)})
                      nil))))
              file-names)
        []))
    catalog)))

(defn write-file
  [file-path content]
  {:pre [(s/valid? ::spec/file-path file-path)
         (s/valid? ::spec/file-content content)]}
  (try
    (fs/mkdirSync (path/dirname file-path) #js {:recursive true})
    (fs/writeFileSync file-path content "utf8")
    (catch js/Error e
      (throw (ex-info "Failed to write file."
                      {:file-path file-path :cause e})))))

(defn write-markdown-files
  [dir-path manuscripts]
  {:pre [(s/valid? ::spec/file-path dir-path)
         (s/valid? ::spec/manuscripts manuscripts)]}
  (doseq [{:keys [name markdown]} manuscripts]
    (let [file-path (path/join dir-path name)]
      (try
        (write-file file-path markdown)
        (catch js/Error e
          (logger/log :error
                      "Failed to write Markdown file."
                      {:file-path file-path
                       :cause (ex-message e)}))))))
