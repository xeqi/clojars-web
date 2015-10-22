(ns clojars.test.integration.jars
  (:require [clojars.test.integration.steps :refer :all]
            [clojars.test.test-helper :as help]
            [clojure.test :refer :all]
            [kerodon
             [core :refer :all]
             [test :refer :all]]
            [net.cgrand.enlive-html :as html]))

(use-fixtures :each
  help/default-fixture
  help/with-clean-database
  help/with-memory-fs)

(deftest jars-can-be-viewed
  (inject-artifacts-into-repo! help/database "someuser" "test.jar" "test-0.0.1/test.pom")
  (inject-artifacts-into-repo! help/database "someuser" "test.jar" "test-0.0.2/test.pom")
  (inject-artifacts-into-repo! help/database "someuser" "test.jar" "test-0.0.3-SNAPSHOT/test.pom")
  (inject-artifacts-into-repo! help/database "someuser" "test.jar" "test-0.0.3-SNAPSHOT/test.pom")
  (-> (session (help/clojars-ui))
      (visit "/fake/test")
      (within [:div#jar-title :h1 :a]
              (has (text? "fake/test")))
      (within [[:.package-config-example html/first-of-type] :pre]
              (has (text? "[fake/test \"0.0.2\"]")))
      (within [:ul#versions]
              (has (text? "0.0.3-SNAPSHOT0.0.20.0.1")))))

(deftest jars-with-only-snapshots-can-be-viewed
  (inject-artifacts-into-repo! help/database "someuser" "test.jar" "test-0.0.3-SNAPSHOT/test.pom")
  (-> (session (help/clojars-ui))
      (visit "/fake/test")
      (within [:div#jar-title :h1 :a]
              (has (text? "fake/test")))
      (within [[:.package-config-example html/first-of-type] :pre]
              (has (text? "[fake/test \"0.0.3-SNAPSHOT\"]")))
      (within [:span.commit-url]
              (has (text? " with this commit")))
      (within [:ul#versions]
              (has (text? "0.0.3-SNAPSHOT")))))

(deftest canonical-jars-can-be-viewed
  (inject-artifacts-into-repo! help/database "someuser" "fake.jar" "fake-0.0.1/fake.pom")
  (inject-artifacts-into-repo! help/database "someuser" "fake.jar" "fake-0.0.2/fake.pom")
  (inject-artifacts-into-repo! help/database "someuser" "fake.jar" "fake-0.0.3-SNAPSHOT/fake.pom")
  (-> (session (help/clojars-ui))
      (visit "/fake")
      (within [:div#jar-title :h1 :a]
              (has (text? "fake")))
      (within [[:.package-config-example html/first-of-type] :pre]
              (has (text? "[fake \"0.0.2\"]")))
      (within [:ul#versions]
              (has (text? "0.0.3-SNAPSHOT0.0.20.0.1")))))

(deftest specific-versions-can-be-viewed
  (inject-artifacts-into-repo! help/database "someuser" "test.jar" "test-0.0.1/test.pom")
  (inject-artifacts-into-repo! help/database "someuser" "test.jar" "test-0.0.2/test.pom")
  (inject-artifacts-into-repo! help/database "someuser" "test.jar" "test-0.0.3-SNAPSHOT/test.pom")
  (-> (session (help/clojars-ui))
      (visit "/fake/test")
      (follow "0.0.3-SNAPSHOT")
      (within [:div#jar-title :h1 :a]
              (has (text? "fake/test")))
      (within [[:.package-config-example html/first-of-type] :pre]
              (has (text? "[fake/test \"0.0.3-SNAPSHOT\"]")))
      (within [:ul#versions]
              (has (text? "0.0.3-SNAPSHOT0.0.20.0.1")))))

(deftest specific-canonical-versions-can-be-viewed
  (inject-artifacts-into-repo! help/database "someuser" "fake.jar" "fake-0.0.1/fake.pom")
  (inject-artifacts-into-repo! help/database "someuser" "fake.jar" "fake-0.0.2/fake.pom")
  (inject-artifacts-into-repo! help/database "someuser" "fake.jar" "fake-0.0.3-SNAPSHOT/fake.pom")
  (-> (session (help/clojars-ui))
      (visit "/fake")
      (follow "0.0.1")
      (within [:div#jar-title :h1 :a]
              (has (text? "fake")))
      (within [[:.package-config-example html/first-of-type] :pre]
              (has (text? "[fake \"0.0.1\"]")))
      (within [:ul#versions]
              (has (text? "0.0.3-SNAPSHOT0.0.20.0.1")))))


(deftest canonical-jars-can-view-dependencies
  (inject-artifacts-into-repo! help/database "someuser" "fake.jar" "fake-0.0.1/fake.pom")
  (inject-artifacts-into-repo! help/database "someuser" "fake.jar" "fake-0.0.2/fake.pom")
  (-> (session (help/clojars-ui))
      (visit "/fake")
      (within [:ul#dependencies]
               (has (text? "org.clojure/clojure 1.3.0-beta1")))))
