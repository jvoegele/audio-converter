(ns audio-converter.metadata
  (:require [clojure.string :as str])
  (:import [org.jaudiotagger.audio AudioFile AudioFileIO AudioHeader]
           [org.jaudiotagger.tag Tag FieldKey]
           [org.jaudiotagger.tag.id3 AbstractID3v2Tag]
           [org.jaudiotagger.tag.id3.framebody FrameBodyTXXX]))

(def ^:private common-field-keys (seq (FieldKey/values)))

(def null-char #"\x00")

(defn- split
  ([data] (split data null-char))
  ([data splitter]
   (str/split data splitter)))

(defn- split-if-null-char [s]
  (let [split-string (str/split s null-char)]
    (if (= 1 (count split-string))
      (first split-string)
      split-string)))

(defn- create-id3v2-txxx-frame-body [description text]
  (FrameBodyTXXX. org.jaudiotagger.tag.id3.valuepair.TextEncoding/UTF_8
                  description
                  text))

(defn- create-id3v2-frame [id3v2-tag frame-id]
  (.createFrame id3v2-tag frame-id))

(defn- set-id3v2-txx-frame [tag description text]
  (let [frame-body (create-id3v2-txxx-frame-body description text)
        frame (create-id3v2-frame tag "TXXX")]
    (.setBody frame frame-body)
    (.addField tag frame)))

(defn- filter-not-binary [fields]
  (filter #(not (.isBinary %)) fields))

(defn- field-value [tag field-key]
  (let [values (filter-not-binary (.getFields tag field-key))]
    (cond
      (empty? values) nil
      (= 1 (count values)) (split-if-null-char (.getContent (first values)))
      :else (vec (map #(.getContent %) values)))))

(defn- set-multi-value-field
  [tag field-key values]
  (doseq [value (reverse values)]
    (.addField tag field-key (str value))))

(defmulti set-field
  (fn [tag field-key value]
    (if (instance? FieldKey field-key)
      :common
      (cond
        (instance? AbstractID3v2Tag tag) :id3-txxx
        (instance? org.jaudiotagger.tag.mp4.Mp4Tag tag) :mp4-custom
        :else :common))))

(defmethod set-field :common
  [tag field-key value]
  (if (coll? value)
    (set-multi-value-field tag field-key value)
    (.setField tag field-key (str value))))

(defmethod set-field :id3-txxx
  [tag field-key value]
  (set-id3v2-txx-frame tag field-key value))

(defmethod set-field :mp4-custom
  [tag field-key value])

(defn- delete-fields! [tag field-keys]
  (doseq [field-key field-keys]
    (when (.hasField tag field-key)
      (println (str "deleting " field-key))
      (.deleteField tag field-key))))

(defn- read-clean!
  "Reads the specified audio file using org.jaudiotagger.audio.AudioFileIO/read,
  deletes any existing tags, then rereads the audio file to ensure that there
  are no associated tags."
  [audio-file]
  (let [read-file
        (fn [f] (AudioFileIO/read (clojure.java.io/file audio-file)))]
    (AudioFileIO/delete (read-file audio-file))
    (read-file audio-file)))

(defn copy-artwork [src-tag dest-tag]
  (doseq [artwork (.getArtworkList src-tag)]
    (.addField dest-tag artwork)))

(defn copy-common-fields
  "Copy common fields from src-tag to dest-tag."
  [src-tag dest-tag]
  (doseq [field-key common-field-keys]
    (when-let [value (field-value src-tag field-key)]
      (set-field dest-tag field-key value))))

(defn copy-fields [src-tag dest-tag]
  (doseq [field (iterator-seq (.getFields src-tag))]
    (let [field-key (.getId field)
          value (field-value src-tag field-key)]
      (println (str "copying " field-key))
      (set-field dest-tag field-key value))))

(defn field-key [id]
  (let [field-id (str id)]
    (try
      (FieldKey/valueOf field-id)
      (catch IllegalArgumentException e
        field-id))))

(defn- update-field-value [new-value existing-value]
  (let [new-value (str new-value)]
    (cond
      (nil? existing-value) new-value
      (coll? existing-value) (conj existing-value new-value)
      :else [existing-value new-value])))

(defn- tag->map
  "Create a map that corresponds to the fields in the given tag. The keys of
  the map will be the field keys, either as an instance of the
  org.jaudiotagger.tag.FieldKey enum (if applicable), or as a string.
  The values in the map will be field values, either as a string (for
  single-valued fields) or as a vector of strings (in the case of
  multi-valued fields)."
  [tag]
  (let [fields (filter-not-binary (iterator-seq (.getFields tag)))]
    (reduce (fn [field-map field]
              (let [fkey (field-key (.getId field))
                    fval (.toString field)]
                (update field-map fkey (partial update-field-value fval))))
            (hash-map)
            fields)))

;; Options:
;;  id3v2 version
;;  skip encoder field?
;;  skip arbitrary fields
;;  map src field keys to dest field keys
(defn copy-metadata [src-file dest-file options]
  (let [src-file (AudioFileIO/read (clojure.java.io/file src-file))
        dest-file (read-clean! dest-file)
        skip-fields (get options :skip-fields [])]
    (when-let [src-tag (.getTag src-file)]
      (let [dest-tag (.getTagAndConvertOrCreateAndSetDefault dest-file)]
        (delete-fields! src-tag skip-fields)
        (copy-artwork src-tag dest-tag)
        (.deleteArtworkField src-tag)
        (copy-common-fields src-tag dest-tag)
        (delete-fields! src-tag common-field-keys)
        (copy-fields src-tag dest-tag)
        )
      (.commit dest-file))))

