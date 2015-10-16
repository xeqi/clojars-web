(ns clojars.test.test-helper
  (import java.io.File)
  (:require [clojars
             [config :refer [config]]
             [db :as db]
             [ports :as ports]
             [search :as search]
             [system :as system]
             [web :as web :refer [repo ui]]]
            [clojars.db.migrate :as migrate]
            [clojure.java
             [io :as io]
             [jdbc :as jdbc]
             [shell :as sh]]
            [clucy.core :as clucy]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [defroutes]]))

(def local-repo (io/file (System/getProperty "java.io.tmpdir")
                         "clojars" "test" "local-repo"))
(def local-repo2 (io/file (System/getProperty "java.io.tmpdir")
                         "clojars" "test" "local-repo2"))

(def test-config
  (system/translate
   {:app {:middleware []}
    :port 0
    :db {:classname "org.sqlite.JDBC"
         :subprotocol "sqlite"
         :subname "data/test/db"}
    :repo "data/test/repo"
    :stats-dir "data/test/stats"
    :index-path "data/test/index"
    :bcrypt-work-factor 12
    :mail {:hostname "smtp.gmail.com"
           :from "noreply@clojars.org"
           :username "clojars@pupeno.com"
           :password "fuuuuuu"
           :port 465 ; If you change ssl to false, the port might not be effective, search for .setSSL and .setSslSmtpPort
           :ssl true}}))

(defn using-test-config [f]
  (with-redefs [config test-config]
    (f)))

(defn delete-file-recursively
  "Delete file f. If it's a directory, recursively delete all its contents."
  [f]
  (let [f (io/file f)]
    (when (.exists f)
      (when (.isDirectory f)
        (doseq [child (.listFiles f)]
          (delete-file-recursively child)))
      (io/delete-file f))))

(defonce migrate
  (delay
   (let [db (:subname (:db config))]
     (when-not (.exists (io/file db))
       (.mkdirs (.getParentFile (io/file db)))
       (sh/sh "sqlite3" db :in (slurp "clojars.sql"))))
   (migrate/-main)))

(defn make-download-count! [m]
  (spit (io/file (config :stats-dir) "all.edn")
        (pr-str m)))

(defn make-index! [v]
  (delete-file-recursively (io/file (config :index-path)))
  (with-open [index (clucy/disk-index (config :index-path))]
    (if (empty? v)
      (do (clucy/add index {:dummy true})
          (clucy/search-and-delete index "dummy:true"))
      (binding [clucy/*analyzer* search/analyzer]
        (doseq [a v]
          (clucy/add index a))))))

(defn default-fixture [f]
  (using-test-config
   (fn []
     (force migrate)
     (delete-file-recursively (io/file (config :repo)))
     (delete-file-recursively (io/file (config :stats-dir)))
     (.mkdirs (io/file (config :stats-dir)))
     (make-download-count! {})
     (jdbc/db-do-commands (:db config)
                          "delete from users;" "delete from jars;" "delete from groups;")
     (f))))

(defn index-fixture [f]
  (make-index! [])
  (f))

(declare test-port)

(defroutes clojars-app
  (repo {:error-handler (reify ports/ErrorHandler (-report [t e extra] "error-id"))})
  (ui {}))

(defn run-test-app
  [f]
  (let [system (component/start (system/new-system test-config))
        server (get-in system [:http :server])
        port (-> server .getConnectors first .getLocalPort)]
    (with-redefs [test-port port]
      (try
        (f)
        (finally
          (.stop server))))))
