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

(ns blue.lions.clono.esm-test
  (:require [cljs.test :as t]
            [clojure.string :as str]
            [blue.lions.clono.esm :as esm]))

(t/deftest load-esm-test
  (t/testing "Module and property exist."
    (t/is (fn? (esm/load-esm "mdast-util-from-markdown" "fromMarkdown"))))

  (t/testing "Module does not exist."
    (try
      (esm/load-esm "not-exists-module" "property")
      (catch js/Error e
        (let [data (ex-data e)]
          (t/is (= "Failed to load ESM." (ex-message e)))
          (t/is (= "not-exists-module" (:module-name data)))
          (t/is (= "property" (:property-name data)))
          (t/is (str/starts-with? (ex-message (:cause data))
                                  "Cannot find module"))))))

  (t/testing "Property does not exist."
    (try
      (esm/load-esm "mdast-util-from-markdown" "notExistsProperty")
      (catch js/Error e
        (let [data (ex-data e)
              cause (:cause data)
              cause-data (ex-data cause)]
          (t/is (= "Failed to load ESM." (ex-message e)))
          (t/is (= "mdast-util-from-markdown" (:module-name data)))
          (t/is (= "notExistsProperty" (:property-name data)))
          (t/is (= "Property is not found." (ex-message cause)))
          (t/is (= "mdast-util-from-markdown" (:module-name cause-data)))
          (t/is (= "notExistsProperty" (:property-name cause-data))))))))
