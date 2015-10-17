(ns clojars.test.test-helper
  (import java.io.File)
  (:require [clojars
             [config :refer [config]]
             [search :as search]
             [system :as system]
             [web :refer [repo ui]]]
            [clojars.component.lossy-handler :as lossy]
            [clojars.db.migrate :as migrate]
            [clojure.java.io :as io]
            [clucy.core :as clucy]
            [com.stuartsierra.component :as component]
            [compojure.core :as compojure]
            [duct.component.hikaricp :refer [hikaricp]]))

(def local-repo (io/file (System/getProperty "java.io.tmpdir")
                         "clojars" "test" "local-repo"))
(def local-repo2 (io/file (System/getProperty "java.io.tmpdir")
                         "clojars" "test" "local-repo2"))

(def test-config
  (system/translate
   {:app {:middleware []}
    :port 0
    :db {:uri "jdbc:sqlite::memory:"}
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
     (delete-file-recursively (io/file (config :repo)))
     (delete-file-recursively (io/file (config :stats-dir)))
     (.mkdirs (io/file (config :stats-dir)))
     (make-download-count! {})
     (f))))

(defn index-fixture [f]
  (make-index! [])
  (f))

(declare thread-pool)
(declare database)

(defn with-clean-database [f]
  (with-redefs [thread-pool (component/start (hikaricp (:db config)))]
    (with-redefs [database (:spec thread-pool)]
      (with-out-str
        (migrate/migrate database))
      (f)
      (component/stop thread-pool))))

(declare test-port)

(defn clojars-ui []
  (ui {:error-handler (lossy/->LossyHandler)
       :db database}))

(defn clojars-app []
  (let [error-handler (lossy/->LossyHandler)]
    (compojure/routes
     (repo {:error-handler error-handler
            :db database})
     (ui {:error-handler error-handler
          :db database}))))

(defn run-test-app
  [f]
  (let [system (component/start (assoc (system/new-system test-config)
                                       :db thread-pool
                                       :error-handler (lossy/->LossyHandler)))
        server (get-in system [:http :server])
        port (-> server .getConnectors first .getLocalPort)]
    (with-redefs [test-port port]
      (try
        (f)
        (finally
          (.stop server))))))
