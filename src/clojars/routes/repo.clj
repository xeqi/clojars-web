(ns clojars.routes.repo
  (:require [clojars
             [auth :refer [require-authorization with-account]]
             [config :refer [config]]
             [db :as db]
             [maven :as maven]
             [ports :as ports]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [compojure
             [core :as compojure :refer [PUT]]
             [route :refer [not-found]]]
            [ring.util
             [codec :as codec]
             [response :as response]]
            [ring.util.time :as ring.time])
  (:import java.io.StringReader
           [java.nio.file Files LinkOption OpenOption StandardOpenOption]
           java.nio.file.attribute.FileAttribute
           java.util.Date))

(defn save-to-file [path input]
  (Files/createDirectories (.getParent path)
                           (into-array FileAttribute []))
  (with-open [o (Files/newOutputStream
                 path
                 (into-array OpenOption [StandardOpenOption/CREATE StandardOpenOption/WRITE]))]
    (io/copy input o)))

(defn- try-save-to-file [path input]
  (try
    (save-to-file path input)
    (catch java.io.IOException e
      (Files/delete path)
      (throw e))))

(defn- pom? [filename]
  (.endsWith filename ".pom"))

(defn- get-pom-info [contents info]
  (-> contents
      StringReader.
      maven/reader-to-map
      (merge info)))

(defn- body-and-add-pom [db body filename info account]
  (if (pom? filename)
    (let [contents (slurp body)]
      (db/add-jar db account (get-pom-info contents info))
      contents)
    body))

(defmacro with-error-handling [error-handler & body]
  `(try
     ~@body
     ;; should we only do 201 if the file didn't already exist?
     {:status 201 :headers {} :body nil}
     (catch Exception e#
       (ports/report ~error-handler e#)
       (let [data# (ex-data e#)]
         {:status (or (:status data#) 403)
          :headers {"status-message" (:status-message data#)}
          :body (.getMessage e#)}))))

(defmacro put-req [db error-handler groupname & body]
  `(with-account
     (require-authorization
      ~db
      ~groupname
      (with-error-handling ~error-handler
        ~@body))))

(defn- validate-regex [x re message]
  (when-not (re-matches re x)
    (throw (ex-info message {:value x
                             :regex re}))))

(defn snapshot-version? [version]
  (.endsWith version "-SNAPSHOT"))

(defn assert-non-redeploy [path version]
 (when (and (not (snapshot-version? version))
         (Files/exists path (into-array LinkOption [])))
   (throw (ex-info "redeploying non-snapshots is not allowed (see http://git.io/vO2Tg)"
            {}))))

(defn validate-deploy [path group-id artifact-id version filename]
  (try
    ;; We're on purpose *at least* as restrictive as the recommendations on
    ;; https://maven.apache.org/guides/mini/guide-naming-conventions.html
    ;; If you want loosen these please include in your proposal the
    ;; ramifications on usability, security and compatiblity with filesystems,
    ;; OSes, URLs and tools.
    (validate-regex artifact-id #"^[a-z0-9_.-]+$"
      (str "project names must consist solely of lowercase "
        "letters, numbers, hyphens and underscores (see http://git.io/vO2Uy)"))
    (validate-regex group-id #"^[a-z0-9_.-]+$"
      (str "group names must consist solely of lowercase "
        "letters, numbers, hyphens and underscores (see http://git.io/vO2Uy)"))
    ;; Maven's pretty accepting of version numbers, but so far in 2.5 years
    ;; bar one broken non-ascii exception only these characters have been used.
    ;; Even if we manage to support obscure characters some filesystems do not
    ;; and some tools fail to escape URLs properly.  So to keep things nice and
    ;; compatible for everyone let's lock it down.
    (validate-regex version #"^[a-zA-Z0-9_.+-]+$"
      (str "version strings must consist solely of letters, "
        "numbers, dots, pluses, hyphens and underscores (see http://git.io/vO2TO)"))
    (assert-non-redeploy path version)
    (catch Exception e
      (throw (ex-info (.getMessage e)
               (merge
                 {:status 403
                  :status-message (str "Forbidden - " (.getMessage e))
                  :group-id group-id
                  :artifact-id artifact-id
                  :version version
                  :file filename}
                 (ex-data e)))))))

(defn- handle-versioned-upload [error-handler db fs body group artifact version filename]
  (let [groupname (string/replace group "/" ".")]
    (put-req
      db
      error-handler
      groupname
      (let [path (.getPath fs (config :repo) (into-array String [group artifact version filename]))
            info {:group groupname
                  :name  artifact
                  :version version}]
        (validate-deploy path groupname artifact version filename)
        (db/check-and-add-group db account groupname)
        (try-save-to-file path
                          (body-and-add-pom db body filename info account))))))

;; web handlers
(defn routes [error-handler db fs]
  (compojure/routes
   (PUT ["/:group/:artifact/:file"
         :group #".+" :artifact #"[^/]+" :file #"maven-metadata\.xml[^/]*"]
        {body :body {:keys [group artifact file]} :params}
        (if (snapshot-version? artifact)
          ;; SNAPSHOT metadata will hit this route, but should be
          ;; treated as a versioned file upload.
          ;; See: https://github.com/ato/clojars-web/issues/319
          (let [version artifact
                group-parts (string/split group #"/")
                group (string/join "/" (butlast group-parts))
                artifact (last group-parts)]
            (handle-versioned-upload error-handler db fs body group artifact version file))
          (let [groupname (string/replace group "/" ".")]
            (put-req
             db
             error-handler
             groupname
             (let [path (.getPath fs (config :repo) (into-array String [group artifact file]))]
               (db/check-and-add-group db account groupname)
               (try-save-to-file path body))))))
   (PUT ["/:group/:artifact/:version/:filename"
         :group #"[^\.]+" :artifact #"[^/]+" :version #"[^/]+"
         :filename #"[^/]+(\.pom|\.jar|\.sha1|\.md5|\.asc)$"]
        {body :body {:keys [group artifact version filename]} :params}
        (handle-versioned-upload error-handler db fs body group artifact version filename))
   (PUT "*" _ {:status 400 :headers {}})
   (not-found "Page not found")))


(defn meta-data [path]
  {:content (Files/newInputStream
             path
             (into-array OpenOption []))
   :content-length (Files/size path)
   :last-modified  (-> path
                       (Files/getLastModifiedTime (into-array LinkOption [LinkOption/NOFOLLOW_LINKS]))
                       .toMillis
                       Date.)})

(defn content-length [resp len]
  (if len
    (response/header resp "Content-Length" len)
    resp))

(defn last-modified [resp last-mod]
  (if last-mod
    (response/header resp "Last-Modified" (ring.time/format-date last-mod))
    resp))

(defn stream-response
  [fs dir path]
  (let [path (.getPath fs dir (into-array String [path]))]
    (if (Files/exists path (into-array LinkOption [LinkOption/NOFOLLOW_LINKS]))
      (let [data (meta-data path)]
        (-> (response/response (:content data))
            (content-length (:content-length data))
            (last-modified (:last-modified data)))))))

(defn wrap-file [app fs dir]
  (fn [req]
    (if-not (= :get (:request-method req))
      (app req)
      (let [path (codec/url-decode (:path-info req))]
        (or (stream-response fs dir path)
            (app req))))))

(defn wrap-reject-double-dot [f]
  (fn [req]
    (if (re-find #"\.\." (:uri req))
      {:status 400 :headers {}}
      (f req))))
