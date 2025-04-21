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
  (:require [clojure.string :as str]
            [blue.lions.clono.spec :as spec]))

(defonce ^:private enabled? (atom true))

(defn set-enabled!
  [enabled]
  {:pre [(spec/validate ::spec/log-enabled enabled
                        "Invalid enabled flag is given.")]
   :post [(spec/validate ::spec/log-enabled %
                         "Invalid enabled flag is returned.")]}
  (reset! enabled? enabled))

(defonce ^:private entries (atom []))

(defn get-entries
  []
  {:post [(spec/validate ::spec/log-entries % "Invalid entries is returned.")]}
  @entries)

(defn reset-entries!
  []
  {:post [(spec/validate ::spec/log-entries % "Invalid entries is returned.")]}
  (reset! entries []))

(defonce ^:private min-level (atom :info))

(defn set-min-level!
  [level]
  {:pre [(spec/validate ::spec/log-level level "Invalid log level is given.")]
   :post [(spec/validate ::spec/log-level % "Invalid log level is returned.")]}
  (reset! min-level level))

(defn- log-level->value
  [level]
  {:pre [(spec/validate ::spec/log-level level "Invalid log level is given.")]
   :post [(spec/validate ::spec/log-level-value %
                         "Invalid value is returned.")]}
  (case level
    :debug 0
    :info 1
    :warn 2
    :error 3
    1))

(defn- format-log-message
  [level message]
  {:pre [(spec/validate ::spec/log-level level "Invalid log level is given.")
         (spec/validate ::spec/log-message message
                        "Invalid log message is given.")]
   :post [(spec/validate ::spec/log-message %
                         "Invalid log message is returned.")]}
  (str "[" (str/upper-case (name level)) "] " message))

(defn log
  ([level message]
   {:pre [(spec/validate ::spec/log-level level "Invalid log level is given.")
          (spec/validate ::spec/log-message message
                         "Invalid log message is given.")]}
   (log level message nil))
  ([level message data]
   {:pre [(spec/validate ::spec/log-level level "Invalid log level is given.")
          (spec/validate ::spec/log-message message
                         "Invalid log message is given.")
          (spec/validate ::spec/log-data data "Invalid log data is given.")]}
   (let [entry {:level level :message message :data data}]
     (swap! entries conj entry)
     (when (and @enabled?
                (>= (log-level->value level) (log-level->value @min-level)))
       (let [log-fn (case level
                      :debug js/console.debug
                      :info js/console.info
                      :warn js/console.warn
                      :error js/console.error
                      js/console.log)
             formatted-message (format-log-message level message)]
         (if data
           (log-fn formatted-message (clj->js data))
           (log-fn formatted-message)))))))

(defn debug
  [message & [data]]
  {:pre [(spec/validate ::spec/log-message message
                        "Invalid log message is given.")
         (spec/validate ::spec/log-data data "Invalid log data is given.")]}
  (log :debug message data))

(defn info
  [message & [data]]
  {:pre [(spec/validate ::spec/log-message message
                        "Invalid log message is given.")
         (spec/validate ::spec/log-data data "Invalid log data is given.")]}
  (log :info message data))

(defn warn
  [message & [data]]
  {:pre [(spec/validate ::spec/log-message message
                        "Invalid log message is given.")
         (spec/validate ::spec/log-data data "Invalid log data is given.")]}
  (log :warn message data))

(defn error
  [message & [data]]
  {:pre [(spec/validate ::spec/log-message message
                        "Invalid log message is given.")
         (spec/validate ::spec/log-data data "Invalid log data is given.")]}
  (log :error message data))
