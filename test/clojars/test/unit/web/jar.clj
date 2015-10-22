(ns clojars.test.unit.web.jar
  (:require [clojars.web.jar :as jar]
            [clojure.test :refer :all]
            [clojars.test.test-helper :as help]))

(use-fixtures :each
  help/default-fixture
  help/with-clean-database)

(deftest bad-homepage-url-shows-as-text
  (let [html (jar/show-jar help/database
                           nil {:homepage "something thats not a url"
                                   :created 3
                                   :version "1"
                                   :group_name "test"
                                   :jar_name "test"}
                           nil [] 0)]
    (is (re-find #"something thats not a url" html))))

(deftest pages-are-escaped
  (let [html (jar/show-jar help/database
                           nil {:homepage nil
                                   :created 3
                                   :version "<script>alert('hi')</script>"
                                   :group_name "test"
                                   :jar_name "test"}
                           nil [] 0)]
    (is (not (.contains html "<script>alert('hi')</script>")))))
