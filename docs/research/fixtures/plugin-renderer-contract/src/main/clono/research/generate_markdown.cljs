(ns clono.research.generate-markdown
  (:require
   ["./custom_column_renderer.js" :refer [customColumnRenderer]]
   ["node:fs" :as fs]
   ["node:path" :as path]
   [clono.research.plugin-renderer-contract :as renderer-contract]))

(def body-markdown
  (str "本文には**強い強調**、*強調*、`inline-code`、"
       "[外部リンク](https://example.com/)がある。\n\n"
       "- 最初の項目\n"
       "- 次の項目"))

(defn- write-rendered-markdown! [output-directory file-name renderer]
  (let [input (js/Object.freeze
               #js {:title "休憩 & <雑談>"
                    :body body-markdown})
        output (renderer-contract/render-column renderer input)]
    (.writeFileSync fs (.join path output-directory file-name) output "utf8")))

(defn main []
  (let [fixture-root (.resolve path js/__dirname "..")
        output-directory (.join path fixture-root "output")]
    (.rmSync fs output-directory #js {:recursive true :force true})
    (.mkdirSync fs output-directory #js {:recursive true})
    (write-rendered-markdown! output-directory
                              "default-column.md"
                              renderer-contract/default-column-renderer)
    (write-rendered-markdown! output-directory
                              "custom-column.md"
                              customColumnRenderer)))
