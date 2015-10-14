(ns clojars.routes.artifact
  (:require [clojars
             [auth :as auth]
             [db :as db]
             [maven :refer [jar-to-pom-map]]
             [ports :as ports]
             [promote :as promote]]
            [clojars.web
             [common :as common]
             [jar :as view]]
            [clojure.set :as set]
            [compojure.core :as compojure :refer [GET POST]]
            [ring.util.response :as response])
  (:import java.io.IOException))

(defn find-pom [error-handler artifact]
  (try
    (jar-to-pom-map artifact)
    (catch IOException e
      (ports/report error-handler (ex-info "Failed to create pom map" artifact e))
      nil)))

(defn show [error-handler group-id artifact-id]
  (if-let [artifact (db/find-jar group-id artifact-id)]
    (auth/try-account
     (view/show-jar account
                    artifact
                    (find-pom error-handler artifact)
                    (db/recent-versions group-id artifact-id 5)
                    (db/count-versions group-id artifact-id)))))

(defn list-versions [group-id artifact-id]
  (if-let [artifact (db/find-jar group-id artifact-id)]
    (auth/try-account
     (view/show-versions account
                         artifact
                         (db/recent-versions group-id artifact-id)))))

(defn show-version [error-handler group-id artifact-id version]
  (if-let [artifact (db/find-jar group-id artifact-id version)]
    (auth/try-account
     (view/show-jar account
                    artifact
                    (find-pom error-handler artifact)
                    (db/recent-versions group-id artifact-id 5)
                    (db/count-versions group-id artifact-id)))))

(defn response-based-on-format
  "render appropriate response based on the file type suffix provided:
  JSON or SVG"
  [file-format artifact-id & [group-id]]
  (let [group-id (or group-id artifact-id)]
  (cond
    (= file-format "json") (-> (response/response (view/make-latest-version-json group-id artifact-id))
                               (response/header "Cache-Control" "no-cache")
                               (response/content-type "application/json; charset=UTF-8"))
    (= file-format "svg") (-> (response/response (view/make-latest-version-svg group-id artifact-id))
                              (response/header "Cache-Control" "no-cache")
                              (response/content-type "image/svg+xml")))))

(defn routes [error-handler]
  (compojure/routes
   (GET ["/:artifact-id", :artifact-id #"[^/]+"] [artifact-id]
        (show error-handler artifact-id artifact-id))
   (GET ["/:group-id/:artifact-id", :group-id #"[^/]+" :artifact-id #"[^/]+"]
        [group-id artifact-id]
        (show error-handler group-id artifact-id))

   (GET ["/:artifact-id/versions" :artifact-id #"[^/]+"] [artifact-id]
        (list-versions artifact-id artifact-id))
   (GET ["/:group-id/:artifact-id/versions"
         :group-id #"[^/]+" :artifact-id #"[^/]+"]
        [group-id artifact-id]
        (list-versions group-id artifact-id))

   (GET ["/:artifact-id/versions/:version"
         :artifact-id #"[^/]+" :version #"[^/]+"]
        [artifact-id version]
        (show-version error-handler artifact-id artifact-id version))
   (GET ["/:group-id/:artifact-id/versions/:version"
         :group-id #"[^/]+" :artifact-id #"[^/]+" :version #"[^/]+"]
        [group-id artifact-id version]
        (show-version error-handler group-id artifact-id version))

   (GET ["/:artifact-id/latest-version.:file-format"
         :artifact-id #"[^/]+"
         :file-format #"(svg|json)$"]
        [artifact-id file-format]
        (response-based-on-format file-format artifact-id))

   (GET ["/:group-id/:artifact-id/latest-version.:file-format"
         :group-id #"[^/]+"
         :artifact-id #"[^/]+"
         :file-format #"(svg|json)$"]
        [group-id artifact-id file-format]
        (response-based-on-format file-format artifact-id group-id))

   (POST ["/:group-id/:artifact-id/promote/:version"
          :group-id #"[^/]+" :artifact-id #"[^/]+" :version #"[^/]+"]
         [group-id artifact-id version]
         (auth/with-account
           (auth/require-authorization
            group-id
            (if-let [jar (db/find-jar group-id artifact-id version)]
              (do (promote/promote (set/rename-keys jar {:jar_name :name
                                                         :group_name :group}))
                  (response/redirect
                   (common/jar-url {:group_name group-id
                                    :jar_name artifact-id})))))))))
