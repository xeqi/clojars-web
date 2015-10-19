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
           java.nio.file.FileSystems
           java.nio.file.OpenOption
           java.nio.file.StandardOpenOption
           java.nio.file.attribute.FileAttribute
           org.joda.time.DateTimeUtils$MillisProvider
           org.joda.time.DateTimeUtils))

(defn make-tmp-dir []
  (doto (-> (FileSystems/getDefault)
            (.getPath (System/getProperty "java.io.tmpdir") (into-array String []))
            (Files/createTempDirectory "clojars" (into-array FileAttribute []))
            .toFile)
    .deleteOnExit))

(def test-config
  {:app {:middleware []}
    :port 0
    :db {:uri "jdbc:sqlite::memory:"}
    :repo "data/test/repo"
    :bcrypt-work-factor 12
    :stats-dir "data/test/stats"
    :mail {:hostname "smtp.gmail.com"
           :from "noreply@clojars.org"
           :username "clojars@pupeno.com"
           :password "fuuuuuu"
           :port 465 ; If you change ssl to false, the port might not be effective, search for .setSSL and .setSslSmtpPort
           :ssl true}})

(def ^:dynamic current-time nil)

(DateTimeUtils/setCurrentMillisProvider
 (reify DateTimeUtils$MillisProvider
   (getMillis [t] (or current-time (System/currentTimeMillis)))))

(defn do-at* [base-date-time body-fn]
  (binding [current-time (.getMillis base-date-time)]
    (body-fn)))

(defmacro do-at
  "Like clojure.core/do except evalautes the expression at the given date-time"
  [base-date-time & body]
  `(do-at* ~base-date-time
    (fn [] ~@body)))

(defn using-test-config [f]
  (with-redefs [config test-config]
    (f)))

(defn make-download-count! [fs m]
  (with-open [s (Files/newOutputStream
           (.getPath fs (str (test-config :stats-dir)) (into-array String ["all.edn"]))
           (into-array OpenOption [StandardOpenOption/CREATE StandardOpenOption/WRITE]))]
    (spit s (pr-str m))))

(defn default-fixture [f]
  (using-test-config
   (fn [] (f))))


(declare ^:dynamic thread-pool)
(declare ^:dynamic database)

(defn with-clean-database [f]
  (binding [thread-pool (component/start (hikaricp (:db test-config)))]
    (binding [database (:spec thread-pool)]
      (with-out-str
        (migrate/migrate database))
      (try
        (f)
        (finally
          (component/stop thread-pool))))))

(declare ^:dynamic index)

(defn with-index [f]
  (with-open [mem-index (clucy/memory-index)]
    (binding [index {:index mem-index}]
      (f))))

(declare ^:dynamic fs)

(defn with-memory-fs [f]
  (binding [fs (Jimfs/newFileSystem (Configuration/unix))]
    (try
      (Files/createDirectories (.getPath fs (:stats-dir test-config) (into-array String []))
                               (into-array FileAttribute []))
      (f)
      (finally (.close fs)))))

(defn clojars-ui
  ([] (clojars-ui (lossy/->LossyHandler)))
  ([error-handler]
   (ui {:error-handler error-handler
        :db database
        :index index
        :fs fs
        :bcrypt-work-factor (:bcrypt-work-factor test-config)
        :mailer (:mailer test-config)})))

(defn clojars-app []
  (let [error-handler (lossy/->LossyHandler)]
    (compojure/routes
     (repo {:error-handler error-handler
            :db database
            :fs fs
            :base-directory (:repo test-config)})
     (clojars-ui error-handler))))

(declare ^:dynamic test-port)

(defn run-test-app
  [f]
  (let [system (component/start (assoc (system/new-system test-config)
                                       :index index
                                       :db thread-pool
                                       :fs fs
                                       :error-handler (lossy/->LossyHandler)))
        server (get-in system [:http :server])
        port (-> server .getConnectors first .getLocalPort)]
    (binding [test-port port]
      (try
        (f)
        (finally
          (.stop server))))))
