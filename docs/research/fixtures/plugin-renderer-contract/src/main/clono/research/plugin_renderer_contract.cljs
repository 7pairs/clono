(ns clono.research.plugin-renderer-contract
  (:require
   [goog.object :as gobj]
   [goog.string :as gstring]))

(defn render-column [renderer input]
  (renderer input))

(defn default-column-renderer [input]
  (let [title (gobj/get input "title")
        body (gobj/get input "body")]
    (str "<aside class=\"clono-column\">\n\n"
         "<p class=\"clono-column-title\">"
         (gstring/htmlEscape title)
         "</p>\n\n"
         body
         "\n\n</aside>")))
