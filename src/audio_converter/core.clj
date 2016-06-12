(ns audio-converter.core
  (:require [audio-converter.metadata :as metadata])
  (:gen-class))

(defn -main
  [& args]
  (let [[src-file dest-file] args
        options {:skip-fields ["ENCODING"
                               "ENCODED-BY"
                               "ENCODING-DATE"
                               "COMPRESSION-RATIO"]}]
    (println (str src-file " -> " dest-file))
    (metadata/copy-metadata src-file dest-file options)))
