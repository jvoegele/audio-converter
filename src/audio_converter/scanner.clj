(ns audio-converter.scanner
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]))

(defn- has-extension? [f extensions]
  (if-let [ext (fs/extension f)]
    (some #(= (subs (str/lower-case ext) 1) %1) extensions)))

(defn- trim-path-separator [path]
  (let [separators #"^[/\\]*"]
    (str/replace-first path separators "")))

(defn- relative-to [root-dir path]
  (let [root-str (str root-dir)
        path-str (str path)]
    (if (.startsWith path-str root-str)
      (io/as-file
        (trim-path-separator
          (subs path-str (count root-str)))))))

(defn- audio-file? [file audio-file-extensions]
  (has-extension? file audio-file-extensions))

(defn- partition-dir [[root dirs files] audio-file-extensions]
  (let [grouped-files (group-by #(audio-file? % audio-file-extensions) files)]
    {:dir root
     :subdirs dirs
     :audio-files (get grouped-files true [])
     :other-files (get grouped-files nil [])}))

(defn- album-dir? [partitioned-dir]
  (and (not (nil? partition-dir)) (not (empty? partition-dir))))

(defn- next-dir [current-dir])
(defn- find-next-album-dir [current-dir audio-file-extensions]
  (when-not (nil? current-dir)
    (let [dir (io/as-file current-dir)
          contents (partition-dir dir)])))

(defn- audio-files-seq
  "Returns a lazy sequence of java.io.File objects representing audio files
  found in the directory tree beginning at root-dir. The directory tree is
  scanned depth first."
  [root-dir extensions]
  (let [root (io/as-file root-dir)
        audio-file? #(has-extension? % extensions)]
    (filter audio-file?
            (tree-seq fs/directory? fs/list-dir root))))

(defn album-dirs-seq
  "Returns a lazy sequence of album directories and their contents, where an
  album directory is any directory that contains one or more files whose
  extension matches one of the given audio-file-extensions.

  Each entry in the sequence is a map with the following keys and values:
  1. :dir - A java.io.File object representing the directory path
  2. :audio-files - A seq containing one java.io.File object for each audio
       file in the directory
  3. other-files - A seq containing one java.io.File object for each
       non-audio file in the directory"
  [root-dir audio-file-extensions]
  (let [dir-seq (fs/iterate-dir root-dir)]
    (->> dir-seq
         (map #(partition-dir % audio-file-extensions))
         (filter #((comp not empty?) (:audio-files %))))))
