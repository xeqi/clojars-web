(ns clojars.promote
  (:require [cemerick.pomegranate.aether :as aether]
            [clj-pgp
             [core :as pgp]
             [signature :as pgp-sig]]
            [clojars
             [config :refer [config]]
             [db :as db]
             [maven :as maven]]
            [clojure.string :as str])
  (:import java.io.ByteArrayInputStream
           java.nio.charset.Charset
           [java.nio.file Files LinkOption OpenOption StandardOpenOption]
           [org.bouncycastle.openpgp PGPObjectFactory PGPUtil]
           org.springframework.aws.maven.SimpleStorageServiceWagon))

(defonce _
  (do (java.security.Security/addProvider
       (org.bouncycastle.jce.provider.BouncyCastleProvider.))
      (aether/register-wagon-factory!
       "s3" (constantly (SimpleStorageServiceWagon.)))))

(defn decode-signature [data]
  ; File could be signed with multiple signatures.
  ; In this case it isn't
  (first (pgp/decode-signatures data)))

(defn verify [sig-path data public-key]
  (if public-key
    (let [sig (decode-signature (slurp (Files/newBufferedReader sig-path (Charset/defaultCharset)))) ]
      (if (= (pgp/key-id sig) (pgp/key-id public-key))
        (let [stream (Files/newInputStream data (into-array OpenOption [StandardOpenOption/READ]))]
          (try (pgp-sig/verify stream sig public-key)
               (finally (.close stream))))))))

(defn parse-keys [s]
  (try
    (-> s
        .getBytes
        ByteArrayInputStream.
        PGPUtil/getDecoderStream
        PGPObjectFactory.
        .nextObject
        .getPublicKeys
        iterator-seq)
    (catch NullPointerException e)))

(defn path-for [fs group artifact version extension]
  (let [filename (format "%s-%s.%s" artifact version extension)]
    (.getPath fs (config :repo)
              (into-array String (concat (str/split group #"\.") [artifact version filename])))))

(defn check-path-exists [blockers path]
  (if (Files/exists path (into-array LinkOption []))
    blockers
    (conj blockers (str "Missing file " (-> path .getFileName)))))

(defn check-version [version]
  (if (re-find #"-SNAPSHOT$" version)
    "Snapshot versions cannot be promoted"))

(defn check-field [blockers info field pred]
  (if (pred (field info))
    blockers
    (conj blockers (str "Missing " (name field)))))

(defn signed-with? [path sig-path keys]
  (some #(verify sig-path path %) (mapcat parse-keys keys)))

(defn signed? [blockers path keys]
  (let [sig-path (.resolveSibling path (str (-> path .getFileName) ".asc"))]
    (if (Files/exists sig-path (into-array LinkOption []))
      (if (signed-with? path sig-path keys)
        blockers
        (conj blockers (str "Could not verify signature of " path "."
                            " Ensure your public key is in your profile.")))
      (conj blockers (str path " is not signed.")))))

(defn unpromoted? [blockers db {:keys [group name version]}]
  (let [[{:keys [promoted_at]}] (db/promoted? db group name version)]
    (if promoted_at
      (conj blockers "Already promoted.")
      blockers)))

(defn blockers [db fs {:keys [group name version]}]
  (let [jar (path-for fs group name version "jar")
        pom (path-for fs group name version "pom")
        keys (remove nil? (db/group-keys db group))
        info (try (when (Files/exists pom (into-array LinkOption []))
                    (maven/pom-to-map pom))
                  (catch Exception e
                    (.printStackTrace e) {}))]
    (if-let [version-blocker (check-version version)]
      [version-blocker]
      (-> []
        (check-path-exists jar)
        (check-path-exists pom)

        (check-field info :description (complement empty?))
        (check-field info :url #(re-find #"^http" (str %)))
        (check-field info :licenses seq)
        (check-field info :scm identity)

        (signed? jar keys)
        (signed? pom keys)
        (unpromoted? db info)))))

(defn- add-coords [fs {:keys [group name version classifier] :as info}
                   files extension]
  ;; TODO: classifier?
  (assoc files [(symbol group name) version :extension extension]
         (.toFile (path-for fs group name version extension))))

(defn- deploy-to-s3 [fs info]
  (let [files (reduce (partial add-coords fs info) {}
                      ["jar" "jar.asc" "pom" "pom.asc"])
        releases-repo {:url (config :releases-url)
                       :username (config :releases-access-key)
                       :passphrase (config :releases-secret-key)}]
    (aether/deploy-artifacts :artifacts (keys files)
                             :files files
                             :transfer-listener :stdout
                             :repository {"releases" releases-repo})))

(defn promote [db fs {:keys [group name version] :as info}]
  (println "checking" group "/" name "for promotion...")
  (let [blockers (blockers db fs info)]
    (if (empty? blockers)
      (if (config :releases-url)
        (do
          (println "Promoting" info)
          (deploy-to-s3 fs info)
          (db/promote db group name version))
        (println "Didn't promote since :releases-url wasn't set."))
      (do (println "...failed.")
          blockers))))
