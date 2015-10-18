(ns clojars.test.unit.search
  (:require [clojars.search :as search]
            [clojure.test :refer :all]
            [clojars.test.test-helper :as help]
            [clucy.core :as clucy]))

(defn make-index! [index v]
  (binding [clucy/*analyzer* search/analyzer]
    (doseq [a v]
      (clucy/add index a))))

(use-fixtures :each
  help/with-index
  help/with-memory-fs)

(deftest weight-by-downloads
  (help/make-download-count! help/fs
                             {["lein-ring" "lein-ring"] {"0.0.1" 10000}
                              ["lein-modules" "lein-modules"] {"0.1.0" 200}
                              ["c" "c"] {"0.1.0" 100000}})
  (let [index (:index help/index)]
    (make-index! index
                 [{:artifact-id "lein-ring"
                   :group-id "lein-ring"}
                  {:artifact-id "lein-modules"
                   :group-id "lein-modules"}
                  {:artifact-id "c"
                   :group-id "c"}])
    (is (= (search/search index help/fs "lein-modules")
           [{:group-id "lein-modules", :artifact-id "lein-modules"}
            {:group-id "lein-ring", :artifact-id "lein-ring"}]))
    (is (= (search/search index help/fs "lein-ring")
           [{:group-id "lein-ring", :artifact-id "lein-ring"}
            {:group-id "lein-modules", :artifact-id "lein-modules"}]))
    (is (= (search/search index help/fs "lein")
           [{:group-id "lein-ring", :artifact-id "lein-ring"}
            {:group-id "lein-modules", :artifact-id "lein-modules"}]))
    (is (= (search/search index help/fs "ring")
           [{:group-id "lein-ring", :artifact-id "lein-ring"}]))))
