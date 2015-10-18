(ns clojars.stats
  (:require [clojars.config :as config]
            [clojure.core.memoize :as memo]
            [clojure.java.io :as io])
  (:import java.nio.file.FileSystem
           java.nio.file.Files
           java.nio.file.LinkOption
           java.nio.charset.Charset))

(defn all* [^FileSystem fs]
  (let [path (.getPath fs (config/config :stats-dir) (into-array String ["/all.edn"]))]
    (if (Files/exists path (into-array LinkOption []))
      (read (java.io.PushbackReader. (Files/newBufferedReader path (Charset/defaultCharset))))
      {})))

(def all (memo/ttl all* :ttl/threshold (* 60 60 1000))) ;; 1 hour

(defn download-count [dls group-id artifact-id & [version]]
  (let [ds (dls [group-id artifact-id])]
    (or (if version
          (get ds version)
          (->> ds
               (map second)
               (apply +)))
        0)))

(defn total-downloads [dls]
  (apply +
         (for [[[g a] vs] dls
               [v c] vs]
           c)))
