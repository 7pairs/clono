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

(ns blue.lions.clono.render
  (:require [cljs.spec.alpha :as s]
            ["path" :as path]
            [blue.lions.clono.esm :as esm]
            [blue.lions.clono.log :as logger]
            [blue.lions.clono.spec :as spec]))

(def plugin-cache (atom {}))

(defn load-plugin
  [type & {:keys [plugin-dir]
           :or {plugin-dir (path/join js/__dirname "plugins")}}]
  {:pre [(s/valid? ::spec/node-type type)]
   :post [(s/valid? ::spec/function-or-nil %)]}
  (or (@plugin-cache type)
      (let [plugin-path (path/join plugin-dir (str type ".js"))]
        (try
          (let [plugin (js/require plugin-path)]
            (if (and (object? plugin) (empty? (js/Object.keys plugin)))
              (do
                (logger/log :warn
                            "Invalid JavaScript file is detected."
                            {:type type :plugin-path plugin-path})
                nil)
              (do
                (swap! plugin-cache assoc type plugin)
                (logger/log :info
                            "Plugin is successfully loaded."
                            {:type type :plugin-dir plugin-dir})
                plugin)))
          (catch js/Error _
            nil)))))

(defn ast->markdown
  [ast]
  {:pre [(s/valid? ::spec/node ast)]
   :post [(s/valid? ::spec/markdown %)]}
  (try
    (let [converted-ast (clj->js ast {:keywordize-keys false})]
      (esm/to-markdown converted-ast))
    (catch js/Error e
      (throw (ex-info "Failed to convert AST to Markdown."
                      {:ast ast :cause e})))))
