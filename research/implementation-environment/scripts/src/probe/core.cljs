;; Copyright 2026 HASEBA Junya (7pairs)
;; SPDX-License-Identifier: Apache-2.0
(ns probe.core
  (:require ["unified" :refer [unified]]
            ["remark-parse" :default remark-parse]
            ["remark-directive" :default remark-directive]
            ["remark-gfm" :default remark-gfm]))

(defn echo [value]
  ;; Exercise an ESM-only npm dependency in both output formats.
  (.parse (.use (unified) remark-parse) "# probe")
  value)
(defn run-extension [extension node context]
  (.then (js/Promise.resolve) (fn [] (extension node context))))
(defn parse [source]
  (-> (unified) (.use remark-parse) (.use remark-directive) (.use remark-gfm) (.parse source)))

(defn- nodes [node]
  (cons node (mapcat nodes (array-seq (or (.-children ^js node) #js [])))))

(defn- text [node]
  (or (.-value ^js node) (apply str (map text (array-seq (or (.-children ^js node) #js []))))))

(defn collect [documents]
  (let [chapters (map-indexed
                  (fn [i doc]
                    #js {:file (.-file ^js doc) :number (inc i)
                         :title (text (first (filter #(and (= "heading" (.-type ^js %))
                                                          (= 1 (.-depth ^js %)))
                                                    (nodes (.-tree ^js doc)))) )})
                  (array-seq documents))
        index (mapcat
                (fn [doc chapter]
                  (map (fn [node]
                         #js {:term (text node) :file (.-file ^js doc)
                              :number (.-number ^js chapter)
                              :line (.. ^js node -position -start -line)})
                       (filter #(and (= "textDirective" (.-type ^js %))
                                     (= "index" (.-name ^js %)))
                               (nodes (.-tree ^js doc)))))
                (array-seq documents) chapters)]
    #js {:chapters (into-array chapters) :index (into-array index)}))

(defn transform [tree context]
  (letfn [(visit [node]
            (when-let [children (.-children ^js node)]
              (set! (.-children ^js node) (.map children visit)))
            (case (.-type ^js node)
              "textDirective"
              (case (.-name ^js node)
                "chapter" (let [target (text node)
                                chapter (first (filter #(= target (.-file ^js %))
                                                       (array-seq (.-chapters ^js context))))]
                            (when-not chapter
                              (throw (js/Error. (str (.-file ^js context) ":"
                                                    (.. ^js node -position -start -line) ":"
                                                    (.. ^js node -position -start -column)
                                                    " Unknown chapter: " target))))
                            #js {:type "text" :value (str "第" (.-number ^js chapter) "章 " (.-title ^js chapter))})
                "index" #js {:type "text" :value (text node)}
                node)
              "leafDirective"
              (if (= "pagebreak" (.-name ^js node))
                #js {:type "html" :value "<div class=\"probe-page-break\"></div>"}
                node)
              "containerDirective"
              (if (= "column" (.-name ^js node))
                #js {:type "html" :value ((.-renderColumn ^js context) node)}
                node)
              node))]
    (visit tree)))
(defn prepare-columns [tree]
  ;; Keep Markdown children and footnote definitions for the downstream parser.
  (letfn [(expand [node]
            (when-let [children (.-children ^js node)]
              (set! (.-children ^js node)
                    (into-array (mapcat expand (array-seq children)))))
            (if (and (= "containerDirective" (.-type ^js node))
                     (= "column" (.-name ^js node)))
              (concat [#js {:type "html" :value "<div class=\"column\">"}]
                      (array-seq (.-children ^js node))
                      [#js {:type "html" :value "</div>"}])
              [node]))]
    (first (expand tree))))
(defn fail [] (throw (js/Error. "CLJS_PROBE_FAILURE")))

(defn main [& args]
  (when (exists? js/module)
    (set! (.-exports js/module) #js {:echo echo :runExtension run-extension :fail fail}))
  (when (= "--smoke" (first (array-seq (.-argv js/process) 2)))
    (js/console.log (js/JSON.stringify (echo #js {:title "導入" :depth 1})))))
