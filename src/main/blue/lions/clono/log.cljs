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

(ns blue.lions.clono.log
  (:require [cljs.spec.alpha :as s]
            [clojure.string :as str]
            [blue.lions.clono.spec :as spec]))

(defonce enabled? (atom true))

(defonce entries (atom []))

(defn log
  ([level message]
   {:pre [(s/valid? ::spec/log-level level)
          (s/valid? ::spec/log-message message)]}
   (log level message nil))
  ([level message data]
   {:pre [(s/valid? ::spec/log-level level)
          (s/valid? ::spec/log-message message)
          (s/valid? ::spec/log-data data)]}
   (let [entry {:level level :message message :data data}]
     (swap! entries conj entry)
     (when @enabled?
       (let [log-fn (case level
                      :info js/console.info
                      :warn js/console.warn
                      :error js/console.error
                      js/console.log)
             formatted-message (str "["
                                    (str/upper-case (name level))
                                    "] "
                                    message)]
         (if data
           (log-fn formatted-message (clj->js data))
           (log-fn formatted-message)))))))
