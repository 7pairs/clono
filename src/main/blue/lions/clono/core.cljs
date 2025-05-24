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

(ns blue.lions.clono.core
  (:require ["path" :as path]
            [blue.lions.clono.analyze :as analyze]
            [blue.lions.clono.file :as file]
            [blue.lions.clono.log :as logger]
            [blue.lions.clono.parse :as parse]
            [blue.lions.clono.render :as render]
            [blue.lions.clono.transform :as transform]))

(defn -main
  [& args]
  (try
    (logger/info "Starting document processing...")
    (logger/info "Reading configuration...")
    (let [config (file/read-config-file (path/join js/__dirname
                                                   "./config.edn"))]
      (logger/info "Reading catalog and markdown files...")
      (let [catalog (file/read-catalog-file (:catalog config))
            manuscripts (file/read-markdown-files (:input config) catalog)]
        (logger/info "Parsing manuscripts...")
        (let [documents (parse/parse-manuscripts
                         manuscripts
                         (parse/create-order-generator))]
          (logger/info "Analyzing documents...")
          (let [toc-items (analyze/create-toc-items documents)
                heading-dic (analyze/create-heading-dic documents)
                footnote-dic (analyze/create-footnote-dic documents)
                indices (analyze/create-indices documents config)]
            (logger/info "Transforming documents...")
            (let [transformed-documents (transform/transform-documents
                                         documents
                                         {:heading heading-dic
                                          :footnote footnote-dic})]
              (logger/info "Rendering documents...")
              (let [rendered-documents (render/render-documents
                                        transformed-documents)]
                (logger/info "Writing output files...")
                (file/write-markdown-files (:output config) rendered-documents)
                (file/write-file (path/join (:output config) "toc.md")
                                 (render/render-toc toc-items))
                (file/write-file (path/join (:output config) "index.md")
                                 (render/render-index-page indices))
                0))))))
    (catch js/Error e
      (logger/error "Error occurred during processing:")
      (logger/error (ex-message e) (ex-data e))
      1)))
