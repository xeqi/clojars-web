(ns clojars.test.test-helper
  (import java.io.File)
  (:require [clojars.component.lossy-handler :as lossy]
            [clojars
             [config :refer [config]]
             [system :as system]
             [web :refer [repo ui]]]
            [clojars.db.migrate :as migrate]
            [clojure.java.io :as io]
            [clucy.core :as clucy]
            [compojure.core :as compojure]
            [com.stuartsierra.component :as component]
            [duct.component.hikaricp :refer [hikaricp]])
  (:import com.google.common.jimfs.Configuration
           com.google.common.jimfs.Jimfs
           java.nio.file.Files
           java.nio.file.OpenOption
           java.nio.file.StandardOpenOption
           java.nio.file.attribute.FileAttribute))

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

(defn make-download-count! [fs m]
  (spit (Files/newOutputStream
         (.getPath fs (str (config :stats-dir)) (into-array String ["all.edn"]))
         (into-array OpenOption [StandardOpenOption/CREATE StandardOpenOption/WRITE]))
        (pr-str m)))

(defn default-fixture [f]
  (using-test-config
   (fn []
     (delete-file-recursively (io/file (config :repo)))
     (delete-file-recursively (io/file (config :stats-dir)))
     (.mkdirs (io/file (config :stats-dir)))
     (f))))


(declare thread-pool)
(declare database)

(defn with-clean-database [f]
  (with-redefs [thread-pool (component/start (hikaricp (:db config)))]
    (with-redefs [database (:spec thread-pool)]
      (with-out-str
        (migrate/migrate database))
      (try
        (f)
        (finally
          (component/stop thread-pool))))))

(declare index)

(defn with-index [f]
  (with-open [mem-index (clucy/memory-index)]
    (with-redefs [index {:index mem-index}]
      (f))))

(declare fs)

(defn with-memory-fs [f]
  (with-redefs [fs (Jimfs/newFileSystem (Configuration/unix))]
    (try
      (Files/createDirectories (.getPath fs (config :stats-dir) (into-array String []))
                               (into-array FileAttribute []))
      (f)
         (finally (.close fs)))))

(defn clojars-ui []
  (ui {:error-handler (lossy/->LossyHandler)
       :db database
       :index index
       :fs fs}))

(defn clojars-app []
  (let [error-handler (lossy/->LossyHandler)]
    (compojure/routes
     (repo {:error-handler error-handler
            :db database})
     (ui {:error-handler error-handler
          :db database
          :index index
          :fs fs}))))

(declare test-port)

(defn run-test-app
  [f]
  (let [system (component/start (assoc (system/new-system test-config)
                                       :index index
                                       :db thread-pool
                                       :fs fs
                                       :error-handler (lossy/->LossyHandler)))
        server (get-in system [:http :server])
        port (-> server .getConnectors first .getLocalPort)]
    (with-redefs [test-port port]
      (try
        (f)
        (finally
          (.stop server))))))
