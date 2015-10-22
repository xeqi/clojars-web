(ns clojars.routes.api
  (:require [clojure.set :refer [rename-keys]]
            [compojure.core :refer [GET ANY defroutes context]]
            [compojure.route :refer [not-found]]
            [ring.middleware.format-response :refer [wrap-restful-response]]
            [ring.util.response :refer [response]]
            [clojars.db :as db]
            [clojars.stats :as stats]))

(defn get-artifact [group-id artifact-id]
  (if-let [artifact (first (db/find-jars-information group-id artifact-id))]
    (let [stats (stats/all)]
      (-> artifact
        (assoc
          :recent_versions (db/recent-versions group-id artifact-id)
          :downloads (stats/download-count stats group-id artifact-id))
        (update-in [:recent_versions]
          (fn [versions]
            (map (fn [version]
                   (assoc version
                     :downloads (stats/download-count stats group-id artifact-id (:version version))))
              versions)))
        response))
    (not-found nil)))

(defroutes handler
  (context "/api" []
    (GET ["/groups/:group-id", :group-id #"[^/]+"] [group-id]
      (if-let [jars (seq (db/find-jars-information group-id))]
        (let [stats (stats/all)]
          (response
            (map (fn [jar]
                   (assoc jar
                     :downloads (stats/download-count stats group-id (:jar_name jar))))
              jars)))
        (not-found nil)))
    (GET ["/artifacts/:artifact-id", :artifact-id #"[^/]+"] [artifact-id]
      (get-artifact artifact-id artifact-id))
    (GET ["/artifacts/:group-id/:artifact-id", :group-id #"[^/]+", :artifact-id #"[^/]+"] [group-id artifact-id]
      (get-artifact group-id artifact-id))
    (GET "/users/:username" [username]
      (if-let [groups (seq (db/find-groupnames username))]
        (response {:groups groups})
        (not-found nil)))
    (ANY "*" _
      (not-found nil))))

(def routes
  (-> handler
      (wrap-restful-response :formats [:json :edn :yaml :transit-json])))
