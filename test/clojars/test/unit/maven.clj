(ns clojars.test.unit.maven
  (:require [clojars
             [config :refer [config]]
             [maven :refer :all]]
            [clojars.test.test-helper :as help]
            [clojure.java.io :as io]
            [clojure.test :refer :all])
  (:import [java.nio.file Files OpenOption]
           java.nio.file.attribute.FileAttribute))

(use-fixtures :each help/with-memory-fs)

(deftest pom-to-map-returns-corrects-dependencies
  (is (=
        [{:group_name "org.clojure", :jar_name "clojure", :version "1.3.0-beta1" :scope "compile"}
         {:group_name "org.clojurer", :jar_name "clojure", :version "1.6.0" :scope "provided"}
         {:group_name "midje", :jar_name "midje", :version "1.3-alpha4", :scope "test"}]
     (:dependencies (reader-to-map (io/reader (io/resource "test-maven/test-maven.pom")))))))

(deftest pom-to-map-handles-group-and-version-inheritance
  (let [m (reader-to-map (io/reader (io/resource "test-maven/test-maven-child.pom")))]
    (is (= "0.0.4" (:version m)))
    (is (= "fake" (:group m)))
    (is (= "child" (:name m)))))

(deftest directory-for-handles-normal-group-name
  (is (= (.getPath help/fs (config :repo) (into-array String ["fake" "test" "1.0.0"]))
         (directory-for help/fs
                        {:group_name "fake"
                         :jar_name "test"
                         :version "1.0.0"}))))

(deftest directory-for-handles-group-names-with-dots
  (is (= (.getPath help/fs (config :repo)
                   (into-array String ["com" "novemberain" "monger" "1.2.0-alpha1"]))
         (directory-for help/fs
                        {:group_name "com.novemberain"
                         :jar_name "monger"
                         :version "1.2.0-alpha1"}))))

(defn make-metadata [group-id artifact-id versions]
  (str "<metadata>
  <groupId>" group-id "</groupId>
  <artifactId>" artifact-id "</artifactId>
  <versioning>
  <versions>"
  (clojure.string/join "\n"
                       (for [v versions]
                         (str "<version>"v"</version>"))) "
  </versions>
  <snapshot>
    <timestamp>20120427.113221</timestamp>
    <buildNumber>133</buildNumber>
  </snapshot>
  <lastUpdated>20120810193549</lastUpdated>
  </versioning>
  </metadata>"))

(defn expected-file [& [d1 d2 d3 file :as args]]
  (.getPath help/fs (config :repo)
            (into-array String [d1 d2 d3 (str file "-20120427.113221-133.pom")])))

(defn snapshot-pom-file-with [{:keys [group_name jar_name version] :as jar-map}]
  (let [parent (directory-for help/fs jar-map)]
    (Files/createDirectories parent (into-array FileAttribute []))
    (with-open [s (Files/newOutputStream (.resolve parent "maven-metadata.xml")
                                         (into-array OpenOption []))]
      (spit s (make-metadata group_name jar_name [version])))
    (snapshot-pom-file parent jar-map)))

(deftest snapshot-pom-file-handles-single-digit-patch-version
  (is (=
        (expected-file "fake" "test" "0.1.3-SNAPSHOT" "test-0.1.3")
        (snapshot-pom-file-with {:group_name "fake"
                                 :jar_name "test"
                                 :version "0.1.3-SNAPSHOT"}))))

(deftest snapshot-pom-file-handles-multi-digit-patch-version
  (is (=
        (expected-file "fake" "test" "0.11.13-SNAPSHOT" "test-0.11.13")
        (snapshot-pom-file-with {:group_name "fake"
                                 :jar_name "test"
                                 :version "0.11.13-SNAPSHOT"}))))

(deftest snapshot-pom-file-handles-no-patch-version
  (is (=
        (expected-file "fake" "test" "0.1-SNAPSHOT" "test-0.1")
        (snapshot-pom-file-with {:group_name "fake"
                                 :jar_name "test"
                                 :version "0.1-SNAPSHOT"}))))

(deftest snapshot-pom-file-handles-release-candidate-version
  (is (=
        (expected-file "fake" "test" "0.2.1-alpha-SNAPSHOT" "test-0.2.1-alpha")
        (snapshot-pom-file-with {:group_name "fake"
                                 :jar_name "test"
                                 :version "0.2.1-alpha-SNAPSHOT"}))))
