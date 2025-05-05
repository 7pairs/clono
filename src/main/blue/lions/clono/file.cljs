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
  [file-path & {:keys [threshold]
                :or {threshold (* 10 1024 1024)}}]
  {:pre [(spec/validate ::spec/file-path file-path
                        "Invalid file path is given.")]
   :post [(spec/validate ::spec/file-content %
                         "Invalid file content is returned.")]}
  (try
    (when-not (fs/existsSync file-path)
      (throw (ex-info "File does not exist." {:file-path file-path})))
    (let [stats (fs/statSync file-path)
          size (.-size stats)]
      (when (> size threshold)
        (logger/warn "Reading very large file."
                     {:file-path file-path :size (/ size 1024 1024)})))
    (fs/readFileSync file-path "utf8")
    (catch js/Error e
      (throw (ex-info "Failed to read file."
                      {:file-path file-path :cause e})))))

(defn read-edn-file
  [file-path & {:keys [threshold required-keys]}]
  {:pre [(spec/validate ::spec/file-path file-path
                        "Invalid file path is given.")]
   :post [(spec/validate ::spec/edn % "Invalid EDN is returned.")]}
  (try
    (let [opts (cond-> {}
                 threshold (assoc :threshold threshold))
          content (apply read-file file-path (mapcat identity opts))
          edn (reader/read-string content)]
      (when (nil? edn)
        (throw (ex-info "EDN file is empty or invalid."
                        {:file-path file-path :content content})))
      (when required-keys
        (doseq [key required-keys]
          (when-not (contains? edn key)
            (throw (ex-info "Required key is missing in EDN file."
                            {:file-path file-path
                             :required-keys required-keys
                             :missing-key key})))))
      edn)
    (catch js/Error e
      (throw (ex-info "Failed to read or parse EDN file."
                      {:file-path file-path :cause e})))))

(defn read-config-file
  [file-path]
  {:pre [(spec/validate ::spec/file-path file-path
                        "Invalid file path is given.")]
   :post [(spec/validate ::spec/config %
                         "Invalid configuration is returned.")]}
  (try
    (let [config (read-edn-file file-path
                                :required-keys [:catalog :input :output])]
      (doseq [path-key [:catalog :input :output]]
        (let [path (get config path-key)]
          (when-not (fs/existsSync path)
            (logger/warn "Path does not exist." {:key path-key :path path}))))
      config)
    (catch js/Error e
      (throw (ex-info "Failed to read config file."
                      {:file-path file-path :cause e})))))

(defn read-catalog-file
  [file-path]
  {:pre [(spec/validate ::spec/file-path file-path
                        "Invalid file path is given.")]
   :post [(spec/validate ::spec/catalog % "Invalid catalog is returned.")]}
  (try
    (let [catalog (read-edn-file file-path :required-keys [:chapters])]
      (doseq [[key file-names] catalog]
        (when (seq file-names)
          (logger/debug (str "Catalog has " (count file-names) " files.")
                        {:type (name key) :files file-names})))
      catalog)
    (catch js/Error e
      (throw (ex-info "Failed to read catalog file."
                      {:file-path file-path :cause e})))))

(defn read-markdown-file
  [file-path & {:keys [threshold]}]
  {:pre [(spec/validate ::spec/file-path file-path
                        "Invalid file path is given.")]
   :post [(spec/validate ::spec/markdown % "Invalid Markdown is returned.")]}
  (try
    (let [opts (cond-> {}
                 threshold (assoc :threshold threshold))]
      (apply read-file file-path (mapcat identity opts)))
    (catch js/Error e
      (throw (ex-info "Failed to read Markdown file."
                      {:file-path file-path :cause e})))))

(defn read-markdown-files
  [dir-path catalog]
  {:pre [(spec/validate ::spec/file-path dir-path
                        "Invalid file path is given.")
         (spec/validate ::spec/catalog catalog "Invalid catalog is given.")]
   :post [(spec/validate ::spec/manuscripts %
                         "Invalid manuscripts are returned.")]}
  (vec
   (mapcat
    (fn [[type file-names]]
      (if (s/valid? ::spec/document-type type)
        (keep (fn [file-name]
                (let [file-path (path/join dir-path file-name)]
                  (try
                    {:name file-name
                     :type type
                     :markdown (read-markdown-file file-path)}
                    (catch js/Error e
                      (logger/error (str "Failed to read Markdown file: "
                                         file-name)
                                    {:file-path file-path
                                     :type type
                                     :cause (ex-message e)})
                      nil))))
              file-names)
        []))
    catalog)))

(defn write-file
  [file-path content & {:keys [force?]
                        :or {force? false}}]
  {:pre [(spec/validate ::spec/file-path file-path
                        "Invalid file path is given.")
         (spec/validate ::spec/file-content content
                        "Invalid file content is given.")]}
  (try
    (when (and (fs/existsSync file-path)
               (not force?))
      (logger/warn "File already exists and will be overwritten."
                   {:file-path file-path}))
    (fs/mkdirSync (path/dirname file-path) #js {:recursive true})
    (fs/writeFileSync file-path content "utf8")
    (catch js/Error e
      (throw (ex-info "Failed to write file."
                      {:file-path file-path :cause e})))))

(defn write-markdown-files
  [dir-path manuscripts & {:keys [force?]}]
  {:pre [(spec/validate ::spec/file-path dir-path
                        "Invalid file path is given.")
         (spec/validate ::spec/manuscripts manuscripts
                        "Invalid manuscripts are given.")]}
  (let [total (count manuscripts)
        counter (atom 0)]
   (doseq [{:keys [name markdown]} manuscripts]
    (let [file-path (path/join dir-path name)]
      (try
        (swap! counter inc)
        (write-file file-path markdown :force? force?)
        (logger/info (str "Wrote Markdown file ("
                          @counter
                          "/"
                          total
                          "): "
                          name))
        (catch js/Error e
          (logger/error "Failed to write Markdown file."
                        {:file-path file-path
                         :cause (ex-message e)})))))))
