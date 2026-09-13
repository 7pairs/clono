(ns clono.index.generator
  (:require
   [clojure.string :as string]
   [clono.document-url :as document-url]
   [clono.index.entries :as entries]
   [clono.index.sorting :as sorting]
   [clono.markdown :as markdown]
   [goog.string :as gstring]))

(def ^:private separator-html
  "<span class=\"clono-index-separator\">,&nbsp;</span>")

(defn- heading-markdown [title]
  (markdown/serialize
   #js {:type "root"
        :children
        #js [#js {:type "heading"
                  :depth 1
                  :children #js [#js {:type "text" :value title}]}]}))

(defn- occurrence-link [index-path term index occurrence]
  (let [href (document-url/relative-html-url
              index-path
              (:source-name occurrence)
              (:marker-id occurrence))]
    (str "<a class=\"clono-index-page\" href=\""
         (gstring/htmlEscape href)
         "\" aria-label=\""
         (gstring/htmlEscape (str term "の出現" (inc index)))
         "\"></a>")))

(defn- occurrence-links [index-path term occurrences]
  (->> occurrences
       (map-indexed #(occurrence-link index-path term %1 %2))
       (interpose separator-html)
       (apply str)))

(defn- entry-html [index-path {:keys [term occurrences]}]
  (str "<div class=\"clono-index-entry\">\n"
       "<dt>" (gstring/htmlEscape term) "</dt>\n"
       "<dd>" (occurrence-links index-path term occurrences) "</dd>\n"
       "</div>"))

(defn- group-html [index-path {:keys [id title index-entries]}]
  (str "<section class=\"clono-index-group clono-index-group-"
       (name id)
       "\">\n"
       "<h2>" (gstring/htmlEscape title) "</h2>\n"
       "<dl class=\"clono-index-list\">\n"
       (string/join "\n" (map #(entry-html index-path %) index-entries))
       "\n</dl>\n"
       "</section>"))

(defn- populated-groups [index-entries]
  (let [entries-by-group (group-by :group-id index-entries)]
    (->> sorting/group-definitions
         (keep (fn [{:keys [id title]}]
                 (when-let [group-entries (seq (get entries-by-group id))]
                   {:id id
                    :title title
                    :index-entries (vec group-entries)})))
         vec)))

(defn generate-markdown [{:keys [path title]} occurrences]
  (let [groups (-> occurrences
                   entries/merge-and-sort
                   populated-groups)]
    (str (heading-markdown title)
         "\n"
         (string/join "\n\n" (map #(group-html path %) groups))
         (when (seq groups) "\n"))))
