(ns clojars.routes.user
  (:require [clojars.db :as db]
            [clojars.auth :as auth]
            [clojars.web.user :as view]
            [compojure.core :as compojure :refer [GET POST]]))

(defn show [db username]
  (if-let [user (db/find-user db username)]
    (auth/try-account
     (view/show-user db account user))))

(defn routes [db]
  (compojure/routes
   (GET "/profile" {:keys [flash]}
        (auth/with-account
          (view/profile-form account (db/find-user db account) flash)))
   (POST "/profile" {:keys [params]}
         (auth/with-account
           (view/update-profile db account params)))

   (GET "/register" _
        (view/register-form))

   (GET "/forgot-password" _
        (view/forgot-password-form))
   (POST "/forgot-password" {:keys [params]}
         (view/forgot-password db params))

   (GET "/password-resets/:reset-code" [reset-code]
        (view/edit-password-form db reset-code))

   (POST "/password-resets/:reset-code" {{:keys [reset-code password confirm]} :params}
         (view/edit-password db reset-code {:password password :confirm confirm}))

   (GET "/users/:username" [username]
        (show db username))
   (GET "/:username" [username]
        (show db username))))
