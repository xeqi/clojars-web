(ns ^{:doc "Tools to setup a dev db."}
  clojars.dev.setup
  "Tools to setup a dev db."
  (:require [clojars
             [config :refer [config]]
             [db :as db]
             [search :as search]]
            [clojars.db.sql :as sql]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clucy.core :as clucy])
  (:import [org.apache.maven.artifact.repository.metadata Metadata Versioning]
           [org.apache.maven.artifact.repository.metadata.io.xpp3 MetadataXpp3Reader MetadataXpp3Writer]))

(defn reset-db! [db]
  (sql/clear-jars! {} {:connection db})
  (sql/clear-groups! {} {:connection db})
  (sql/clear-users! {} {:connection db}))

(defn add-test-users
  "Adds n test users of the form test0/test0."
  [db n]
  (mapv #(let [name (str "test" %)]
           (if (db/find-user db name)
             (println "User" name "already exists")
             (do
               (printf "Adding user %s/%s\n" name name)
               (db/add-user db (:work-factor config) (str name "@example.com") name name "")))
           name)
    (range n)))

(defn write-metadata [md file]
  (with-open [w (io/writer file)]
    (.write (MetadataXpp3Writer.) w md)))

(defn read-metadata [file]
  (.read (MetadataXpp3Reader.) (io/input-stream file)))

(defn create-metadata [md-file group-id artifact-id version]
  (write-metadata
    (doto (Metadata.)
      (.setGroupId group-id)
      (.setArtifactId artifact-id)
      (.setVersioning
        (doto (Versioning.)
          (.addVersion version))))
    md-file))

(defn update-metadata
  ([dir group-id artifact-id version]
   (let [md-file (io/file dir "maven-metadata.xml")]
     (if (.exists md-file)
       (update-metadata md-file version)
       (create-metadata md-file group-id artifact-id version))))
  ([md-file version]
   (-> md-file
     read-metadata
     (doto (-> .getVersioning (.addVersion version)))
     (write-metadata md-file))))

(defn import-repo
  "Builds a dev db from the contents of the repo."
  [db repo users]
  (let [group-artifact-pattern (re-pattern (str repo "/(.*)/([^/]*)$"))]
    (doseq [version-dir (file-seq (io/file repo))
            :when (and (.isDirectory version-dir)
                    (re-find #"^[0-9]\." (.getName version-dir)))
            :let [parent (.getParentFile version-dir)
                  [_ group-path artifact-id] (re-find group-artifact-pattern (.getPath parent))
                  version (.getName version-dir)
                  group-id (str/lower-case (str/replace group-path "/" "."))
                  user (or (first (db/group-membernames db group-id)) (rand-nth users))]]
      (when-not (db/find-jar db group-id artifact-id version)
        (printf "Importing %s/%s %s (user: %s)\n" group-id artifact-id version user)
        (db/add-jar db
                    user {:group group-id
                          :name artifact-id
                          :version version
                          :description (format "Description for %s/%s" group-id artifact-id)
                          :homepage (format "http://example.com/%s/%s" group-id artifact-id)
                          :authors ["Foo" "Bar" "Basil"]})
        (update-metadata parent group-id artifact-id version)))))

(defn -main []
  (let [db (-> config :db :uri)
        repo (:repo config)]
    (println "NOTE: this will clear the contents of" db
      "and import all of the projects in" repo "into the db.\n")
    (print "Are you sure you want to continue? [y/N] ")
    (flush)
    (when-not (= "y" (.toLowerCase (read-line)))
      (println "Aborting.")
      (System/exit 1))
    (println "==> Clearing the" db "db...")
    (reset-db! db)
    (println "==> Creating 10 test users...")
    (let [test-users (add-test-users db 10)]
      (println "==> Importing" repo "into the db...")
      (import-repo db repo test-users))
    (println "==> Indexing" repo "...")
    (with-open [index (clucy/disk-index (config :index-path))]
      (search/index-repo index repo)))
  (.shutdown (db/write-executor)))
