(ns clojars.maven
  (:require [clojars.config :refer [config]]
            [clojure.string :refer [split]])
  (:import java.nio.charset.Charset
           java.nio.file.Files
           org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader
           org.apache.maven.model.io.xpp3.MavenXpp3Reader))

(defn model-to-map [model]
  {:name (or (.getArtifactId model)
             (-> model .getParent .getArtifactId))
   :group (or (.getGroupId model)
              (-> model .getParent .getGroupId))
   :version (or (.getVersion model)
                (-> model .getParent .getVersion))
   :description (.getDescription model)
   :homepage (.getUrl model)
   :url (.getUrl model)
   :licenses (.getLicenses model)
   :scm (.getScm model)
   :authors (vec (map #(.getName %) (.getContributors model)))
   :dependencies (vec (map
                       (fn [d] {:group_name (.getGroupId d)
                                :jar_name (.getArtifactId d)
                                :version (.getVersion d)
                                :scope (or (.getScope d) "compile")})
                       (.getDependencies model)))})

(defn read-pom
  "Reads a pom file returning a maven Model object."
  [reader]
  (with-open [reader reader]
    (.read (MavenXpp3Reader.) reader)))

(defn path-to-reader [path]
  (Files/newBufferedReader path (Charset/defaultCharset)))

(def reader-to-map (comp model-to-map read-pom))

(def pom-to-map (comp reader-to-map path-to-reader))

(defn read-metadata
  "Reads a maven-metadata file returning a maven Metadata object."
  [path]
  (with-open [reader (Files/newBufferedReader path (Charset/defaultCharset))]
    (.read (MetadataXpp3Reader.) reader)))

(defn snapshot-version
  "Get snapshot version from maven-metadata.xml used in pom filename"
  [path]
  (let [versioning (-> (read-metadata path) .getVersioning .getSnapshot)]
    (str (.getTimestamp versioning) "-" (.getBuildNumber versioning))))

(defn directory-for
  "Directory for a jar under repo"
  [fs {:keys [group_name jar_name version]}]
  (.getPath fs (config :repo)
            (into-array String (concat (split group_name #"\.") [jar_name version]))))

(defn snapshot-pom-file [parent {:keys [jar_name version] :as jar}]
  (let [metadata-path (.resolve parent "maven-metadata.xml")
        snapshot (snapshot-version metadata-path)
        filename (format "%s-%s-%s.pom" jar_name (re-find #"\S+(?=-SNAPSHOT$)" version) snapshot)]
    (.resolve parent filename)))

(defn jar-to-pom-map [fs {:keys [jar_name version] :as jar}]
  (let [parent (directory-for fs jar)
        pom-path (if (re-find #"SNAPSHOT$" version)
                     (snapshot-pom-file parent jar)
                     (.resolve parent (format "%s-%s.%s" jar_name version "pom")))]
    (pom-to-map pom-path)))

(defn github-info [pom-map]
  (let [scm (:scm pom-map)
        url (and scm (.getUrl scm))
        github-re #"^https?://github.com/([^/]+/[^/]+)"
        user-repo (->> (str url) (re-find github-re) second)]
    user-repo))

(defn commit-url [pom-map]
  (let [scm (:scm pom-map)
        url (and scm (.getUrl scm))
        base-url (re-find #"https?://github.com/[^/]+/[^/]+" (str url))]
    (if (and base-url (.getTag scm)) (str base-url "/commit/" (.getTag scm)))))
