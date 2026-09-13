(ns clono.document-url
  (:require
   ["node:path" :as path]
   [clojure.string :as string]))

(defn- html-path [markdown-path]
  (string/replace markdown-path #"\.[mM][dD]$" ".html"))

(defn- encode-rfc3986-path-segment [segment]
  (if (contains? #{"." ".."} segment)
    segment
    (.replace
     (js/encodeURIComponent segment)
     (js/RegExp. "[!'()*]" "g")
     (fn [character]
       (str "%" (.toUpperCase (.toString (.charCodeAt character 0) 16)))))))

(defn- url-path [relative-path]
  (->> (string/split relative-path #"/")
       (map encode-rfc3986-path-segment)
       (string/join "/")))

(defn relative-html-url [source-markdown-path target-markdown-path target-id]
  (let [relative-path
        (when-not (= source-markdown-path target-markdown-path)
          (let [source-html (html-path source-markdown-path)
                target-html (html-path target-markdown-path)]
            (when (= source-html target-html)
              (throw (js/Error.
                      "Distinct Markdown paths resolve to the same HTML path")))
            (.relative (.-posix path)
                       (.dirname (.-posix path) source-html)
                       target-html)))]
    (str (when relative-path (url-path relative-path))
         "#"
         target-id)))
