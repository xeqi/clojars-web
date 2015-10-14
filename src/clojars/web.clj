(ns clojars.web
  (:require [cemerick.friend :as friend]
            [cemerick.friend
             [credentials :as creds]
             [workflows :as workflows]]
            [clojars
             [auth :refer [try-account]]
             [config :refer [config]]
             [db :as db]
             [ports :as ports]]
            [clojars.friend.registration :as registration]
            [clojars.routes
             [api :as api]
             [artifact :as artifact]
             [group :as group]
             [repo :as repo]
             [session :as session]
             [user :as user]]
            [clojars.web
             [browse :refer [browse]]
             [common :refer [html-doc]]
             [dashboard :refer [dashboard index-page]]
             [safe-hiccup :refer [raw]]
             [search :refer [search]]]
            [clojure.java.io :as io]
            [compojure
             [core :refer [ANY context defroutes GET PUT routes]]
             [route :refer [not-found]]]
            [ring.middleware
             [anti-forgery :refer [wrap-anti-forgery]]
             [file-info :refer [wrap-file-info]]
             [flash :refer [wrap-flash]]
             [keyword-params :refer [wrap-keyword-params]]
             [multipart-params :refer [wrap-multipart-params]]
             [params :refer [wrap-params]]
             [resource :refer [wrap-resource]]
             [session :refer [wrap-session]]]))

(defn main-routes [error-handler]
  (routes
   (GET "/" _
        (try-account
         (if account
           (dashboard account)
           (index-page account))))
   (GET "/search" {:keys [params]}
        (try-account
         (let [validated-params (if (:page params)
                                  (assoc params :page (Integer. (:page params)))
                                  params)]
           (search account validated-params))))
   (GET "/projects" {:keys [params]}
        (try-account
         (browse account params)))
   (GET "/security" []
        (try-account
         (html-doc account "Security"
                   (raw (slurp (io/resource "security.html"))))))
   session/routes
   group/routes
   (artifact/routes error-handler)
   ;; user routes must go after artifact routes
   ;; since they both catch /:identifier
   user/routes
   api/routes
   (GET "/error" _ (throw (Exception. "What!? You really want an error?")))
   (PUT "*" _ {:status 405 :headers {} :body "Did you mean to use /repo?"})
   (ANY "*" _
        (try-account
         (not-found
          (html-doc account
                    "Page not found"
                    [:div.small-section
                     [:h1 "Page not found"]
                     [:p "Thundering typhoons!  I think we lost it.  Sorry!"]]))))))

(defn bad-attempt [attempts user]
  (let [failures (or (attempts user) 0)]
    (Thread/sleep (* failures failures)))
  (update-in attempts [user] (fnil inc 0)))

(def credential-fn
  (let [attempts (atom {})]
    (partial creds/bcrypt-credential-fn
             (fn [id]
               (if-let [{:keys [user password]}
                        (db/find-user-by-user-or-email id)]
                 (when-not (empty? password)
                   (swap! attempts dissoc user)
                   {:username user :password password})
                 (do (swap! attempts bad-attempt id) nil))))))

(defn wrap-x-frame-options [f]
  (fn [req] (update-in (f req) [:headers] assoc "X-Frame-Options" "DENY")))

(defn https-request? [req]
  (or (= (:scheme req) :https)
      (= (get-in req [:headers "x-forwarded-proto"]) "https")))

(defn wrap-secure-session [f]
  (let [secure-session (wrap-session f {:cookie-attrs {:secure true
                                                      :http-only true}})
        regular-session (wrap-session f {:cookie-attrs {:http-only true}})]
    (fn [req]
      (if (https-request? req)
        (secure-session req)
        (regular-session req)))))

(defn repo [{:keys [error-handler]}]
  (context "/repo" _
           (-> (repo/routes error-handler)
               (friend/authenticate
                {:credential-fn credential-fn
                 :workflows [(workflows/http-basic :realm "clojars")]
                 :allow-anon? false
                 :unauthenticated-handler
                 (partial workflows/http-basic-deny "clojars")})
               (repo/wrap-file (:repo config))
               (repo/wrap-reject-double-dot))))

(defn ui [{:keys [error-handler]}]
  (-> (main-routes error-handler)
      (friend/authenticate
       {:credential-fn credential-fn
        :workflows [(workflows/interactive-form)
                    registration/workflow]})
      (wrap-anti-forgery)
      (wrap-x-frame-options)
      (wrap-keyword-params)
      (wrap-params)
      (wrap-multipart-params)
      (wrap-flash)
      (wrap-secure-session)
      (wrap-resource "public")
      (wrap-file-info)))

(defroutes clojars-app
  (repo {:error-handler (reify ports/ErrorHandler (-report [t e extra] "error-id"))})
  (ui {}))
