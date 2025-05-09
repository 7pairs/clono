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

(defn- validate-resources
  [resources]
  (doseq [{:keys [src]} resources]
    (let [src-file (io/file src)]
      (when-not (.exists src-file)
        (let [err-msg (str "Source resource file is not found: "
                           (.getAbsolutePath src-file))]
          (println (str "✗ Error: " err-msg))
          (throw (ex-info err-msg {:src-path (.getAbsolutePath src-file)}))))))
  resources)

(defn- safe-copy-file
  [src dst]
  (try
    (io/make-parents dst)
    (if (.exists src)
      (do
        (when (and (.exists dst) (not (.isDirectory dst)))
          (println (str "⚠ Warning: Overwriting existing file: "
                        (.getAbsolutePath dst))))
        (io/copy src dst)
        (println (str "✓ Copied: " (.getName src) " → " (.getName dst))))
      (let [err-msg (str "Source file is not found: " (.getAbsolutePath src))]
        (println (str "✗ Error: " err-msg))
        (throw (ex-info err-msg
                        {:src-path (.getAbsolutePath src)
                         :dst-path (.getAbsolutePath dst)}))))
    (catch Exception e
      (let [err-msg (str "Failed to copy file: " (.getName src))]
        (println (str "✗ Error: " err-msg))
        (throw (ex-info err-msg
                        {:src-path (.getAbsolutePath src)
                         :dst-path (.getAbsolutePath dst)
                         :cause e}))))))

(defn copy-resources
  {:shadow.build/stage :compile-finish}
  [build-state]
  (let [output-file (:output-to (:node-config build-state))
        output-dir (.getParentFile output-file)
        build-config (:shadow.build/config build-state)
        resources (get build-config :clono/resources [])
        validated-resources (validate-resources resources)]
    (doseq [{:keys [src dst]} validated-resources]
      (safe-copy-file (io/file src) (io/file output-dir dst)))
    build-state))
