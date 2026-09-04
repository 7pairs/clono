(ns clono.book.reference-resolution
  (:require
   ["node:path" :as path]
   [clojure.string :as string]
   [clono.diagnostic :as diagnostic]
   [clono.transform :as transform]))

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

(defn- relative-html-path [source-name target-name]
  (when-not (= source-name target-name)
    (let [source-html (html-path source-name)
          target-html (html-path target-name)]
      (when (= source-html target-html)
        (throw (js/Error. "distinct manuscripts resolve to the same HTML path")))
      (.relative (.-posix path)
                 (.dirname (.-posix path) source-html)
                 target-html))))

(defn- url-path [relative-path]
  (->> (string/split relative-path #"/")
       (map encode-rfc3986-path-segment)
       (string/join "/")))

(defn- target-url [relative-path target-id]
  (str (when relative-path (url-path relative-path))
       "#"
       target-id))

(defn- resolve-target [source-name target]
  (try
    (let [relative-path (relative-html-path source-name (:source-name target))]
      (cond-> (assoc target
                     :href (target-url relative-path (:target-id target)))
        (:title-target-id target)
        (assoc :title-href
               (target-url relative-path (:title-target-id target)))))
    (catch :default _error
      (assoc target :resolution-error? true))))

(defn- resolve-manuscript [targets {:keys [tree context] :as manuscript}]
  (let [resolved-targets
        (mapv #(resolve-target (:source-name context) %) targets)
        resolved-context (assoc context :reference-targets resolved-targets)
        diagnostics
        (diagnostic/finalize
         (transform/reference-diagnostics tree resolved-context))]
    {:manuscript (assoc manuscript :context resolved-context)
     :diagnostics diagnostics}))

(defn resolve-references [manuscripts targets]
  (let [results (mapv #(resolve-manuscript targets %) manuscripts)
        diagnostics (into [] (mapcat :diagnostics) results)]
    (if (seq diagnostics)
      {:ok? false
       :manuscripts nil
       :diagnostics diagnostics}
      {:ok? true
       :manuscripts (mapv :manuscript results)
       :diagnostics []})))
