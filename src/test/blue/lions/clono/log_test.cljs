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

(t/deftest log-test
  (t/testing "enabled? is true and message has data."
    (let [org-info js/console.info
          spy-info (spy/spy dummy-log-fn)
          org-warn js/console.warn
          spy-warn (spy/spy dummy-log-fn)
          org-error js/console.error
          spy-error (spy/spy dummy-log-fn)]
      (try
        (set! js/console.info spy-info)
        (set! js/console.warn spy-warn)
        (set! js/console.error spy-error)
        (reset! logger/enabled? true)
        (reset! logger/entries [])
        (logger/log :info "Info message." {:code 100})
        (logger/log :warn "Warning message." {:code 200})
        (logger/log :error "Error message." {:code 300})
        (let [info-params (spy/first-call spy-info)
              warn-params (spy/first-call spy-warn)
              error-params (spy/first-call spy-error)]
          (t/is (spy/called-once? spy-info))
          (t/is (= 2 (count info-params)))
          (t/is (= "[INFO] Info message." (first info-params)))
          (t/is (= {:code 100}
                   (js->clj (second info-params) {:keywordize-keys true})))
          (t/is (spy/called-once? spy-warn))
          (t/is (= 2 (count warn-params)))
          (t/is (= "[WARN] Warning message." (first warn-params)))
          (t/is (= {:code 200}
                   (js->clj (second warn-params) {:keywordize-keys true})))
          (t/is (spy/called-once? spy-error))
          (t/is (= 2 (count error-params)))
          (t/is (= "[ERROR] Error message." (first error-params)))
          (t/is (= {:code 300}
                   (js->clj (second error-params) {:keywordize-keys true})))
          (t/is (= [{:level :info
                     :message "Info message."
                     :data {:code 100}}
                    {:level :warn
                     :message "Warning message."
                     :data {:code 200}}
                    {:level :error
                     :message "Error message."
                     :data {:code 300}}]
                   @logger/entries)))
        (finally
          (set! js/console.info org-info)
          (set! js/console.warn org-warn)
          (set! js/console.error org-error)))))

  (t/testing "enabled? is true and message does not have data."
    (let [org-info js/console.info
          spy-info (spy/spy dummy-log-fn)
          org-warn js/console.warn
          spy-warn (spy/spy dummy-log-fn)
          org-error js/console.error
          spy-error (spy/spy dummy-log-fn)]
      (try
        (set! js/console.info spy-info)
        (set! js/console.warn spy-warn)
        (set! js/console.error spy-error)
        (reset! logger/enabled? true)
        (reset! logger/entries [])
        (logger/log :info "Info message.")
        (logger/log :warn "Warning message.")
        (logger/log :error "Error message.")
        (t/is (spy/called-once? spy-info))
        (t/is (spy/called-with? spy-info "[INFO] Info message."))
        (t/is (spy/called-once? spy-warn))
        (t/is (spy/called-with? spy-warn "[WARN] Warning message."))
        (t/is (spy/called-once? spy-error))
        (t/is (spy/called-with? spy-error "[ERROR] Error message."))
        (t/is (= [{:level :info :message "Info message." :data nil}
                  {:level :warn :message "Warning message." :data nil}
                  {:level :error :message "Error message." :data nil}]
                 @logger/entries))
        (finally
          (set! js/console.info org-info)
          (set! js/console.warn org-warn)
          (set! js/console.error org-error)))))

  (t/testing "enabled? is false and message has data."
    (let [org-info js/console.info
          spy-info (spy/spy dummy-log-fn)
          org-warn js/console.warn
          spy-warn (spy/spy dummy-log-fn)
          org-error js/console.error
          spy-error (spy/spy dummy-log-fn)]
      (try
        (set! js/console.info spy-info)
        (set! js/console.warn spy-warn)
        (set! js/console.error spy-error)
        (reset! logger/enabled? false)
        (reset! logger/entries [])
        (logger/log :info "Info message." {:code 100})
        (logger/log :warn "Warning message." {:code 200})
        (logger/log :error "Error message." {:code 300})
        (t/is (spy/not-called? spy-info))
        (t/is (spy/not-called? spy-warn))
        (t/is (spy/not-called? spy-error))
        (t/is (= [{:level :info :message "Info message." :data {:code 100}}
                  {:level :warn :message "Warning message." :data {:code 200}}
                  {:level :error :message "Error message." :data {:code 300}}]
                 @logger/entries))
        (finally
          (set! js/console.info org-info)
          (set! js/console.warn org-warn)
          (set! js/console.error org-error)))))

  (t/testing "enabled? is false and message does not have data."
    (let [org-info js/console.info
          spy-info (spy/spy dummy-log-fn)
          org-warn js/console.warn
          spy-warn (spy/spy dummy-log-fn)
          org-error js/console.error
          spy-error (spy/spy dummy-log-fn)]
      (try
        (set! js/console.info spy-info)
        (set! js/console.warn spy-warn)
        (set! js/console.error spy-error)
        (reset! logger/enabled? false)
        (reset! logger/entries [])
        (logger/log :info "Info message.")
        (logger/log :warn "Warning message.")
        (logger/log :error "Error message.")
        (t/is (spy/not-called? spy-info))
        (t/is (spy/not-called? spy-warn))
        (t/is (spy/not-called? spy-error))
        (t/is (= [{:level :info :message "Info message." :data nil}
                  {:level :warn :message "Warning message." :data nil}
                  {:level :error :message "Error message." :data nil}]
                 @logger/entries))
        (finally
          (set! js/console.info org-info)
          (set! js/console.warn org-warn)
          (set! js/console.error org-error))))))
