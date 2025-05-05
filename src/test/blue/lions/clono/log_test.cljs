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

(ns blue.lions.clono.log-test
  (:require [cljs.test :as t]
            [spy.core :as spy]
            [blue.lions.clono.log :as logger]))

(defn- dummy-log-fn
  [& _])

(t/deftest set-enabled!-test
  (t/testing "enabled? is set to true."
    (let [org-info js/console.info
          spy-info (spy/spy dummy-log-fn)]
      (try
        (set! js/console.info spy-info)
        (logger/set-enabled! true)
        (logger/info "Info message.")
        (t/is (spy/called-once? spy-info))
        (finally
          (set! js/console.info org-info)
          (logger/set-enabled! true)))))

  (t/testing "enabled? is set to false."
    (let [org-info js/console.info
          spy-info (spy/spy dummy-log-fn)]
      (try
        (set! js/console.info spy-info)
        (logger/set-enabled! false)
        (logger/info "Info message.")
        (t/is (spy/not-called? spy-info))
        (finally
          (set! js/console.info org-info)
          (logger/set-enabled! true))))))

(t/deftest get-entries-test
  (t/testing "Entries do not have data."
    (logger/reset-entries!)
    (t/is (= [] (logger/get-entries))))

  (t/testing "Entries have data."
    (try
      (logger/set-enabled! false)
      (logger/reset-entries!)
      (logger/info "Info message." {:code 100})
      (t/is (= [{:level :info
                 :message "Info message."
                 :data {:code 100}}]
               (logger/get-entries)))
      (finally
        (logger/set-enabled! true)))))

(t/deftest reset-entries!-test
  (t/testing "Entries are reset."
    (try
      (logger/set-enabled! false)
      (logger/reset-entries!)
      (logger/info "Info message." {:code 100})
      (t/is (= [{:level :info
                 :message "Info message."
                 :data {:code 100}}]
               (logger/get-entries)))
      (logger/reset-entries!)
      (t/is (= [] (logger/get-entries)))
      (finally
        (logger/set-enabled! true)))))

(t/deftest set-min-level!-test
  (t/testing "min-level is set to :info."
    (let [org-info js/console.info
          spy-info (spy/spy dummy-log-fn)]
      (try
        (set! js/console.info spy-info)
        (logger/set-min-level! :info)
        (logger/info "Info message.")
        (t/is (spy/called-once? spy-info))
        (finally
          (set! js/console.info org-info)
          (logger/set-min-level! :info)))))

  (t/testing "min-level is set to :warn."
    (let [org-info js/console.info
          spy-info (spy/spy dummy-log-fn)]
      (try
        (set! js/console.info spy-info)
        (logger/set-min-level! :warn)
        (logger/info "Info message.")
        (t/is (spy/not-called? spy-info))
        (finally
          (set! js/console.info org-info)
          (logger/set-min-level! :info))))))

(t/deftest log-test
  (t/testing "enabled? is true and message has data."
    (let [org-debug js/console.debug
          spy-debug (spy/spy dummy-log-fn)
          org-info js/console.info
          spy-info (spy/spy dummy-log-fn)
          org-warn js/console.warn
          spy-warn (spy/spy dummy-log-fn)
          org-error js/console.error
          spy-error (spy/spy dummy-log-fn)]
      (try
        (logger/set-min-level! :debug)
        (set! js/console.debug spy-debug)
        (set! js/console.info spy-info)
        (set! js/console.warn spy-warn)
        (set! js/console.error spy-error)
        (logger/set-enabled! true)
        (logger/reset-entries!)
        (logger/log :debug "Debug message." {:code 100})
        (logger/log :info "Info message." {:code 200})
        (logger/log :warn "Warning message." {:code 300})
        (logger/log :error "Error message." {:code 400})
        (let [debug-params (spy/first-call spy-debug)
              info-params (spy/first-call spy-info)
              warn-params (spy/first-call spy-warn)
              error-params (spy/first-call spy-error)]
          (t/is (spy/called-once? spy-debug))
          (t/is (= 2 (count debug-params)))
          (t/is (= "[DEBUG] Debug message." (first debug-params)))
          (t/is (= {:code 100}
                   (js->clj (second debug-params) {:keywordize-keys true})))
          (t/is (spy/called-once? spy-info))
          (t/is (= 2 (count info-params)))
          (t/is (= "[INFO] Info message." (first info-params)))
          (t/is (= {:code 200}
                   (js->clj (second info-params) {:keywordize-keys true})))
          (t/is (spy/called-once? spy-warn))
          (t/is (= 2 (count warn-params)))
          (t/is (= "[WARN] Warning message." (first warn-params)))
          (t/is (= {:code 300}
                   (js->clj (second warn-params) {:keywordize-keys true})))
          (t/is (spy/called-once? spy-error))
          (t/is (= 2 (count error-params)))
          (t/is (= "[ERROR] Error message." (first error-params)))
          (t/is (= {:code 400}
                   (js->clj (second error-params) {:keywordize-keys true})))
          (t/is (= [{:level :debug
                     :message "Debug message."
                     :data {:code 100}}
                    {:level :info
                     :message "Info message."
                     :data {:code 200}}
                    {:level :warn
                     :message "Warning message."
                     :data {:code 300}}
                    {:level :error
                     :message "Error message."
                     :data {:code 400}}]
                   (logger/get-entries))))
        (finally
          (set! js/console.debug org-debug)
          (set! js/console.info org-info)
          (set! js/console.warn org-warn)
          (set! js/console.error org-error)
          (logger/set-min-level! :info)))))

  (t/testing "enabled? is true and message does not have data."
    (let [org-debug js/console.debug
          spy-debug (spy/spy dummy-log-fn)
          org-info js/console.info
          spy-info (spy/spy dummy-log-fn)
          org-warn js/console.warn
          spy-warn (spy/spy dummy-log-fn)
          org-error js/console.error
          spy-error (spy/spy dummy-log-fn)]
      (try
        (logger/set-min-level! :debug)
        (set! js/console.debug spy-debug)
        (set! js/console.info spy-info)
        (set! js/console.warn spy-warn)
        (set! js/console.error spy-error)
        (logger/set-enabled! true)
        (logger/reset-entries!)
        (logger/log :debug "Debug message.")
        (logger/log :info "Info message.")
        (logger/log :warn "Warning message.")
        (logger/log :error "Error message.")
        (t/is (spy/called-once? spy-debug))
        (t/is (spy/called-with? spy-debug "[DEBUG] Debug message."))
        (t/is (spy/called-once? spy-info))
        (t/is (spy/called-with? spy-info "[INFO] Info message."))
        (t/is (spy/called-once? spy-warn))
        (t/is (spy/called-with? spy-warn "[WARN] Warning message."))
        (t/is (spy/called-once? spy-error))
        (t/is (spy/called-with? spy-error "[ERROR] Error message."))
        (t/is (= [{:level :debug :message "Debug message." :data nil}
                  {:level :info :message "Info message." :data nil}
                  {:level :warn :message "Warning message." :data nil}
                  {:level :error :message "Error message." :data nil}]
                 (logger/get-entries)))
        (finally
          (set! js/console.debug org-debug)
          (set! js/console.info org-info)
          (set! js/console.warn org-warn)
          (set! js/console.error org-error)
          (logger/set-min-level! :info)))))

  (t/testing "enabled? is false and message has data."
    (let [org-debug js/console.debug
          spy-debug (spy/spy dummy-log-fn)
          org-info js/console.info
          spy-info (spy/spy dummy-log-fn)
          org-warn js/console.warn
          spy-warn (spy/spy dummy-log-fn)
          org-error js/console.error
          spy-error (spy/spy dummy-log-fn)]
      (try
        (logger/set-min-level! :debug)
        (set! js/console.debug spy-debug)
        (set! js/console.info spy-info)
        (set! js/console.warn spy-warn)
        (set! js/console.error spy-error)
        (logger/set-enabled! false)
        (logger/reset-entries!)
        (logger/log :debug "Debug message." {:code 100})
        (logger/log :info "Info message." {:code 200})
        (logger/log :warn "Warning message." {:code 300})
        (logger/log :error "Error message." {:code 400})
        (t/is (spy/not-called? spy-debug))
        (t/is (spy/not-called? spy-info))
        (t/is (spy/not-called? spy-warn))
        (t/is (spy/not-called? spy-error))
        (t/is (= [{:level :debug :message "Debug message." :data {:code 100}}
                  {:level :info :message "Info message." :data {:code 200}}
                  {:level :warn :message "Warning message." :data {:code 300}}
                  {:level :error :message "Error message." :data {:code 400}}]
                 (logger/get-entries)))
        (finally
          (set! js/console.debug org-debug)
          (set! js/console.info org-info)
          (set! js/console.warn org-warn)
          (set! js/console.error org-error)
          (logger/set-min-level! :info)))))

  (t/testing "enabled? is false and message does not have data."
    (let [org-debug js/console.debug
          spy-debug (spy/spy dummy-log-fn)
          org-info js/console.info
          spy-info (spy/spy dummy-log-fn)
          org-warn js/console.warn
          spy-warn (spy/spy dummy-log-fn)
          org-error js/console.error
          spy-error (spy/spy dummy-log-fn)]
      (try
        (logger/set-min-level! :debug)
        (set! js/console.debug spy-debug)
        (set! js/console.info spy-info)
        (set! js/console.warn spy-warn)
        (set! js/console.error spy-error)
        (logger/set-enabled! false)
        (logger/reset-entries!)
        (logger/log :debug "Debug message.")
        (logger/log :info "Info message.")
        (logger/log :warn "Warning message.")
        (logger/log :error "Error message.")
        (t/is (spy/not-called? spy-debug))
        (t/is (spy/not-called? spy-info))
        (t/is (spy/not-called? spy-warn))
        (t/is (spy/not-called? spy-error))
        (t/is (= [{:level :debug :message "Debug message." :data nil}
                  {:level :info :message "Info message." :data nil}
                  {:level :warn :message "Warning message." :data nil}
                  {:level :error :message "Error message." :data nil}]
                 (logger/get-entries)))
        (finally
          (set! js/console.debug org-debug)
          (set! js/console.info org-info)
          (set! js/console.warn org-warn)
          (set! js/console.error org-error)
          (logger/set-min-level! :info)))))

  (t/testing "Minimum log level is warn."
    (let [org-debug js/console.debug
          spy-debug (spy/spy dummy-log-fn)
          org-info js/console.info
          spy-info (spy/spy dummy-log-fn)
          org-warn js/console.warn
          spy-warn (spy/spy dummy-log-fn)
          org-error js/console.error
          spy-error (spy/spy dummy-log-fn)]
      (try
        (logger/set-min-level! :warn)
        (set! js/console.debug spy-debug)
        (set! js/console.info spy-info)
        (set! js/console.warn spy-warn)
        (set! js/console.error spy-error)
        (logger/set-enabled! true)
        (logger/reset-entries!)
        (logger/log :debug "Debug message.")
        (logger/log :info "Info message.")
        (logger/log :warn "Warning message.")
        (logger/log :error "Error message.")
        (t/is (spy/not-called? spy-debug))
        (t/is (spy/not-called? spy-info))
        (t/is (spy/called-once? spy-warn))
        (t/is (spy/called-with? spy-warn "[WARN] Warning message."))
        (t/is (spy/called-once? spy-error))
        (t/is (spy/called-with? spy-error "[ERROR] Error message."))
        (t/is (= [{:level :debug :message "Debug message." :data nil}
                  {:level :info :message "Info message." :data nil}
                  {:level :warn :message "Warning message." :data nil}
                  {:level :error :message "Error message." :data nil}]
                 (logger/get-entries)))
        (finally
          (set! js/console.debug org-debug)
          (set! js/console.info org-info)
          (set! js/console.warn org-warn)
          (set! js/console.error org-error)
          (logger/set-min-level! :info))))))

(t/deftest debug-test
  (t/testing "Debug is called."
    (let [spy-log (spy/spy logger/log)]
      (with-redefs [logger/log spy-log]
        (logger/set-enabled! false)
        (logger/reset-entries!)
        (logger/debug "Debug message." {:data 100})
        (t/is (spy/called-once? spy-log))
        (t/is (spy/called-with? spy-log
                                :debug "Debug message." {:data 100}))))))

(t/deftest info-test
  (t/testing "Info is called."
    (let [spy-log (spy/spy logger/log)]
      (with-redefs [logger/log spy-log]
        (logger/set-enabled! false)
        (logger/reset-entries!)
        (logger/info "Info message." {:data 200})
        (t/is (spy/called-once? spy-log))
        (t/is (spy/called-with? spy-log
                                :info "Info message." {:data 200}))))))

(t/deftest warn-test
  (t/testing "Warn is called."
    (let [spy-log (spy/spy logger/log)]
      (with-redefs [logger/log spy-log]
        (logger/set-enabled! false)
        (logger/reset-entries!)
        (logger/warn "Warning message." {:data 300})
        (t/is (spy/called-once? spy-log))
        (t/is (spy/called-with? spy-log
                                :warn "Warning message." {:data 300}))))))

(t/deftest error-test
  (t/testing "Error is called."
    (let [spy-log (spy/spy logger/log)]
      (with-redefs [logger/log spy-log]
        (logger/set-enabled! false)
        (logger/reset-entries!)
        (logger/error "Error message." {:data 400})
        (t/is (spy/called-once? spy-log))
        (t/is (spy/called-with? spy-log
                                :error "Error message." {:data 400}))))))
