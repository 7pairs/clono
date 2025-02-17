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
  (:require [cljs.spec.alpha :as s]
            ["fix-esm" :as fix-esm]
            [blue.lions.clono.spec :as spec]))

(defn load-esm
  [module-name property-name]
  {:pre [(s/valid? ::spec/module-name module-name)
         (s/valid? ::spec/property-name property-name)]
   :post [(s/valid? ::spec/function %)]}
  (try
    (let [module (.require fix-esm module-name)
          property (aget module property-name)]
      (if (some? property)
        property
        (throw (ex-info "Property is not found."
                        {:module-name module-name
                         :property-name property-name}))))
    (catch js/Error e
      (throw (ex-info "Failed to load ESM."
                      {:module-name module-name
                       :property-name property-name
                       :cause e})))
    (finally
      (.unregister fix-esm))))

(def github-slugger
  (load-esm "github-slugger" "default"))
