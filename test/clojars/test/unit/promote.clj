(ns clojars.test.unit.promote
  (:require [clojars.config :refer [config]]
            [clojars.db :as db]
            [clojars.maven :as maven]
            [clojars.promote :refer :all]
            [clojars.test.test-helper :as help]
            [clojure.java.io :as io]
            [clojure.test :refer :all])
  (:import java.nio.file.Files
           java.nio.file.OpenOption
           java.nio.file.StandardOpenOption
           java.nio.file.attribute.FileAttribute))

(use-fixtures :each
  help/default-fixture
  help/with-memory-fs
  help/with-clean-database)

(defn copy-resource [version & [extension]]
  (let [extension (or extension "pom")]
    (Files/createDirectories (.getParent (path-for help/fs "robert" "hooke" version ""))
                             (into-array FileAttribute []))
    (io/copy (io/reader (io/resource (str "hooke-" version "." extension)))
             (Files/newOutputStream
              (path-for help/fs "robert" "hooke" version extension)
              (into-array OpenOption [StandardOpenOption/CREATE StandardOpenOption/WRITE])))))

(deftest test-snapshot-blockers
  (is (= ["Snapshot versions cannot be promoted"]
         (blockers help/database
                   help/fs
                   {:group "robert" :name "hooke"
                    :version "1.2.0-SNAPSHOT"}))))

(deftest test-metadata-blockers
  (copy-resource "1.1.1")
  (is (clojure.set/subset? #{"Missing url" "Missing description"}
                           (set (blockers help/database
                                          help/fs
                                          {:group "robert" :name "hooke"
                                           :version "1.1.1"})))))

(deftest test-unsigned
  (copy-resource "1.1.2")
  (let [b (blockers help/database
                    help/fs
                    {:group "robert" :name "hooke" :version "1.1.2"})]
    (is (some #(.endsWith % "hooke-1.1.2.pom is not signed.") b))
    (is (some #(.endsWith % "hooke-1.1.2.jar is not signed.") b))
    (is (some #(= % "Missing file hooke-1.1.2.jar") b))))

(deftest test-success
  (copy-resource "1.1.2")
  (io/copy "dummy hooke jar file"
           (Files/newOutputStream
            (path-for help/fs "robert" "hooke" "1.1.2" "jar")
            (into-array OpenOption [StandardOpenOption/CREATE StandardOpenOption/WRITE])))
  (copy-resource "1.1.2" "jar.asc")
  (copy-resource "1.1.2" "pom.asc")
  (db/add-user help/database "test@ex.com" "testuser" "password"
               (slurp (io/resource "pubring.gpg")))
  (db/add-member help/database "robert" "testuser" nil)
  (is (empty? (blockers help/database
                        help/fs
                        {:group "robert" :name "hooke" :version "1.1.2"}))))

(deftest test-failed-signature
  (copy-resource "1.1.2")
  (io/copy "dummy hooke jar file corrupted"
           (Files/newOutputStream
            (path-for help/fs "robert" "hooke" "1.1.2" "jar")
            (into-array OpenOption [StandardOpenOption/CREATE StandardOpenOption/WRITE])))
  (copy-resource "1.1.2" "jar.asc")
  (copy-resource "1.1.2" "pom.asc")
  (db/add-user help/database "test@ex.com" "testuser" "password"
               (slurp (io/resource "pubring.gpg")))
  (db/add-member help/database "robert" "testuser" nil)
  (is (= [(str "Could not verify signature of "
               (config :repo) "/robert/hooke/1.1.2/hooke-1.1.2.jar. "
               "Ensure your public key is in your profile.")]
         (blockers help/database
                   help/fs
                   {:group "robert" :name "hooke" :version "1.1.2"}))))

(deftest test-no-key
  (copy-resource "1.1.2")
  (io/copy "dummy hooke jar file corrupted"
           (Files/newOutputStream
            (path-for help/fs "robert" "hooke" "1.1.2" "jar")
            (into-array OpenOption [StandardOpenOption/CREATE StandardOpenOption/WRITE])))
  (copy-resource "1.1.2" "jar.asc")
  (copy-resource "1.1.2" "pom.asc")
  (db/add-user help/database "test@ex.com" "testuser" "password" "")
  (db/add-member help/database "robert" "testuser" nil)
  (is (= [(str "Could not verify signature of "
               (config :repo) "/robert/hooke/1.1.2/hooke-1.1.2.jar. "
               "Ensure your public key is in your profile.")
          (str "Could not verify signature of "
               (config :repo) "/robert/hooke/1.1.2/hooke-1.1.2.pom. "
               "Ensure your public key is in your profile.")]
         (blockers help/database
                   help/fs
                   {:group "robert" :name "hooke" :version "1.1.2"}))))
