(ns clojars.config
  (:require [clojure.tools.cli :refer [cli]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [ring.util.codec :as codec]))

(def default-config
  {:port 8080
   :bind "0.0.0.0"
   :nailgun-bind "127.0.0.1"
   :db {:classname "org.sqlite.JDBC"
        :subprotocol "sqlite"
        :subname "data/db"}
   :base-url "https://clojars.org"
   :stats-dir "data/stats"
   :index-path "data/index"
   :nrepl-port 7991
   :mail {:hostname "127.0.0.1"
          :ssl false
          :from "noreply@clojars.org"}
   :bcrypt-work-factor 12
   :yeller-environment "development"})

(defn parse-resource [f]
  (when-let [r (io/resource f)] (read-string (slurp r))))

(defn url-decode [s]
  (java.net.URLDecoder/decode s "UTF-8"))

(defn parse-query [query]
  (when query
   (reduce (fn [m entry]
             (let [[k v] (str/split entry #"=" 2)]
               (assoc m (keyword (url-decode k)) (url-decode v))))
           {} (str/split query #"&" 2))))

(defn parse-mail-uri [x]
  (let [uri (java.net.URI. x)]
    (merge
     {:ssl (= (.getScheme uri) "smtps")
      :hostname (.getHost uri)}
     (when (pos? (.getPort uri))
       {:port (.getPort uri)})
     (when-let [user-info (.getUserInfo uri)]
       (let [[user pass] (str/split user-info #":" 2)]
         {:username user
          :password pass}))
     (parse-query (.getQuery uri)))))

(defn parse-mail [x]
  (if (string? x)
   (if (= (first x) \{)
     (read-string x)
     (parse-mail-uri x))
   x))

(def env-vars
  [["CONFIG_FILE" :config-file]
   ["PORT" :port #(Integer/parseInt %)]
   ["BIND" :bind]
   ["DATABASE_URL" :db]
   ["MAIL_URL" :mail parse-mail-uri]
   ["REPO" :repo]
   ["DELETION_BACKUP_DIR" :deletion-backup-dir]
   ["NREPL_PORT" :nrepl-port #(Integer/parseInt %)]
   ["NAILGUN_BIND" :nailgun-bind]
   ["NAILGUN_PORT" :nailgun-port #(Integer/parseInt %)]
   ["RELEASES_URL" :releases-url]
   ["RELEASES_ACCESS_KEY" :releases-access-key]
   ["RELEASES_SECRET_KEY" :releases-secret-key]
   ["YELLER_ENV" :yeller-environment]
   ["YELLER_TOKEN" :yeller-token]])

(defn parse-env []
  (reduce
   (fn [m [var k & [f]]]
     (if-let [x (System/getenv var)]
       (assoc m k ((or f identity) x))
       m))
   {} env-vars))

;; we attempt to read a config.clj from the classpath at load time
;; this is handy for interactive development
(def config (merge default-config (parse-resource "config.clj") (parse-env)))

(defn parse-args [args]
  (cli args
       ["-h" "--help" "Show this help text and exit" :flag true]))

(defn process-args [args]
  (let [[options args banner] (parse-args args)]
    (when (:help options)
      (println "clojars-web: a jar repository webapp written in Clojure")
      (println "             https://github.com/ato/clojars-web")
      (println)
      (println banner)
      (println "The config file must be a Clojure map: {:port 8080 :repo \"/var/repo\"}")
      (println)
      (println "Some options can be set using these environment variables:")
      (println (str/join " " (map first env-vars)))
      (System/exit 0))))
