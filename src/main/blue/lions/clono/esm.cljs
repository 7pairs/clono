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

(ns blue.lions.clono.esm
  (:require ["fix-esm" :as fix-esm]
            [blue.lions.clono.spec :as spec]))

(defn load-esm
  [module-name property-name]
  {:pre [(spec/validate ::spec/module-name module-name
                        "Invalid module name is given.")
         (spec/validate ::spec/property-name property-name
                        "Invalid property name is given.")]
   :post [(spec/validate ::spec/function % "Invalid function is returned.")]}
  (try
    (let [module (.require fix-esm module-name)
          property (aget module property-name)]
      (if (some? property)
        property
        (throw (ex-info (str "Cannot find property '" property-name "'.")
                        {:module-name module-name
                         :property-name property-name}))))
    (catch js/Error e
      (throw (ex-info "Failed to load ESM."
                      {:module-name module-name
                       :property-name property-name
                       :cause e})))
    (finally
      (.unregister fix-esm))))

(def ^:private from-markdown-fn
  (delay (load-esm "mdast-util-from-markdown" "fromMarkdown")))

(defn from-markdown
  [& args]
  (apply @from-markdown-fn args))

(def ^:private to-markdown-fn
  (delay (load-esm "mdast-util-to-markdown" "toMarkdown")))

(defn to-markdown
  [& args]
  (apply @to-markdown-fn args))

(def ^:private gfm-footnote-fn
  (delay (load-esm "micromark-extension-gfm-footnote" "gfmFootnote")))

(defn gfm-footnote
  [& args]
  (apply @gfm-footnote-fn args))

(def ^:private gfm-footnote-from-markdown-fn
  (delay (load-esm "mdast-util-gfm-footnote" "gfmFootnoteFromMarkdown")))

(defn gfm-footnote-from-markdown
  [& args]
  (apply @gfm-footnote-from-markdown-fn args))

(def ^:private directive-fn
  (delay (load-esm "micromark-extension-directive" "directive")))

(defn directive
  [& args]
  (apply @directive-fn args))

(def ^:private directive-from-markdown-fn
  (delay (load-esm "mdast-util-directive" "directiveFromMarkdown")))

(defn directive-from-markdown
  [& args]
  (apply @directive-from-markdown-fn args))

(def ^:private github-slugger-class
  (delay (load-esm "github-slugger" "default")))

(def ^:private github-slugger-instance (atom nil))

(defn- create-github-slugger
  []
  {:post [(spec/validate ::spec/github-slugger %
                         "Invalid slugger is returned.")]}
  (let [slugger @github-slugger-class]
    (new slugger)))

(defn- get-github-slugger
  []
  {:post [(spec/validate ::spec/github-slugger %
                         "Invalid slugger is returned.")]}
  (when (nil? @github-slugger-instance)
    (reset! github-slugger-instance (create-github-slugger)))
  @github-slugger-instance)

(defn generate-slug
  [caption]
  {:pre [(spec/validate ::spec/caption caption "Invalid caption is given.")]
   :post [(spec/validate ::spec/slug % "Invalid slug is returned.")]}
  (try
    (.slug ^js (get-github-slugger) caption)
    (catch js/Error e
      (throw (ex-info "Failed to generate slug."
                      {:caption caption :cause e})))))

(defn reset-slugger!
  []
  (if-let [instance @github-slugger-instance]
    (.reset instance)
    (reset! github-slugger-instance (create-github-slugger)))
  nil)
