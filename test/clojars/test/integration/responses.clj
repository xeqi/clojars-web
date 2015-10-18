(ns clojars.test.integration.responses
  (:require [clojars.test.test-helper :as help]
            [clojars.web :as web]
            [clojure.test :refer :all]
            [kerodon
             [core :refer :all]
             [test :refer :all]]))

(use-fixtures :each
  help/using-test-config
  help/with-clean-database)

(deftest respond-404
  (-> (session (help/clojars-ui))
      (visit "/nonexistent-route")
      (has (status? 404))
      (within [:title]
              (has (text? "Page not found - Clojars")))))

(deftest respond-404-for-non-existent-group
  (-> (session (help/clojars-ui))
      (visit "/groups/nonexistent.group")
      (has (status? 404))
      (within [:title]
              (has (text? "Page not found - Clojars")))))

(deftest respond-405-for-puts
  (-> (session (web/main-routes nil help/database nil nil))
      (visit "/nonexistent-route" :request-method :put)
      (has (status? 405))))
