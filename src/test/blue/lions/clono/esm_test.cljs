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
            [blue.lions.clono.esm :as esm]
            [blue.lions.clono.spec :as spec]))

(t/deftest load-esm-test
  (t/testing "Module and property exist."
    (t/is (fn? (esm/load-esm "mdast-util-from-markdown" "fromMarkdown"))))

  (t/testing "Module does not exist."
    (try
      (esm/load-esm "not-exists-module" "property")
      (t/is false "Exception should be thrown.")
      (catch js/Error e
        (let [data (ex-data e)]
          (t/is (= "Failed to load ESM." (ex-message e)))
          (t/is (= "not-exists-module" (:module-name data)))
          (t/is (= "property" (:property-name data)))
          (t/is (str/starts-with?
                 (ex-message (:cause data))
                 "Cannot find module 'not-exists-module'"))))))

  (t/testing "Property does not exist."
    (try
      (esm/load-esm "mdast-util-from-markdown" "notExistsProperty")
      (t/is false "Exception should be thrown.")
      (catch js/Error e
        (let [data (ex-data e)
              cause (:cause data)
              cause-data (ex-data cause)]
          (t/is (= "Failed to load ESM." (ex-message e)))
          (t/is (= "mdast-util-from-markdown" (:module-name data)))
          (t/is (= "notExistsProperty" (:property-name data)))
          (t/is (= "Cannot find property 'notExistsProperty'."
                   (ex-message cause)))
          (t/is (= "mdast-util-from-markdown" (:module-name cause-data)))
          (t/is (= "notExistsProperty" (:property-name cause-data))))))))

(t/deftest generate-slug-test
  (t/testing "Caption contains upper case letters."
    (t/is (= "pascalcase" (esm/generate-slug "PascalCase")))
    (t/is (= "uppercase" (esm/generate-slug "UPPERCASE"))))

  (t/testing "Caption does not contain upper case letters."
    (t/is (= "lowercase" (esm/generate-slug "lowercase"))))

  (t/testing "Caption contains symbols."
    (t/is (= "helloworld" (esm/generate-slug "Hello,World!!")))
    (t/is (= "334" (esm/generate-slug "33:4"))))

  (t/testing "Caption contains spaces."
    (t/is (= "1-2-3" (esm/generate-slug "1 2 3"))))

  (t/testing "Caption contains Japanese letters."
    (t/is (= "日本語" (esm/generate-slug "日本語")))
    (t/is (= "ａｎｄｒｏｉｄ" (esm/generate-slug "Ａｎｄｒｏｉｄ")))
    (t/is (= "こんにちは世界" (esm/generate-slug "こんにちは、世界！"))))

  (t/testing "Captions are duplicated."
    (t/is (= "duplicated" (esm/generate-slug "duplicated")))
    (t/is (= "duplicated-1" (esm/generate-slug "duplicated"))))

  (t/testing "Caption is invalid."
    (let [captions [""
                    nil]]
      (doseq [caption captions]
        (try
          (esm/generate-slug caption)
          (catch js/Error e
            (t/is (= "Invalid caption is given." (ex-message e)))
            (t/is (= {:value caption :spec ::spec/caption} (ex-data e)))))))))

(t/deftest reset-slugger!-test
  (t/testing "Reset slugger."
    (let [caption "Duplicated string."]
      (t/is (= "duplicated-string" (esm/generate-slug caption)))
      (t/is (= "duplicated-string-1" (esm/generate-slug caption)))
      (esm/reset-slugger!)
      (t/is (= "duplicated-string" (esm/generate-slug caption))))))
