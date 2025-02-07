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

(ns blue.lions.clono.etc.hook
  (:require [clojure.java.io :as io]))

(defn- safe-copy-file
  [src dst]
  (try
    (io/make-parents dst)
    (if (.exists src)
      (io/copy src dst)
      (throw (ex-info "Source file is not found."
                      {:src-path (.getAbsolutePath src)
                       :dst-path (.getAbsolutePath dst)})))
    (catch Exception e
      (throw (ex-info "Failed to copy file."
                      {:src-path (.getAbsolutePath src)
                       :dst-path (.getAbsolutePath dst)
                       :cause e})))))

(defn copy-resources
  {:shadow.build/stage :compile-finish}
  [build-state & {:keys [resources]
                  :or {resources [{:src "resources/config.edn"
                                   :dst "config.edn"}]}}]
  (let [output-file (:output-to (:node-config build-state))
        output-dir (.getParentFile output-file)]
    (doseq [{:keys [src dst]} resources]
      (safe-copy-file (io/file src) (io/file output-dir dst)))
    build-state))
