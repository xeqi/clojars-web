(ns  clojars.test.integration.sessions
  (:require [clojars.db :as db]
            [clojars.test.integration.steps :refer :all]
            [clojars.test.test-helper :as help]
            [clojure.test :refer :all]
            [kerodon
             [core :refer :all]
             [test :refer :all]]
            [korma.core :as korma]
            [net.cgrand.enlive-html :as enlive]))

(use-fixtures :each help/default-fixture)

(deftest user-cant-login-with-bad-user-pass-combo
  (-> (session help/clojars-app)
      (login-as "fixture@example.org" "password")
      (follow-redirect)
      (has (status? 200))
      (within [:div :p.error]
              (has (text? "Incorrect username and/or password.")))))

(deftest user-can-login-and-logout
  (-> (session help/clojars-app)
      (register-as "fixture" "fixture@example.org" "password"))
  (doseq [login ["fixture@example.org" "fixture"]]
    (-> (session help/clojars-app)
        (login-as login "password")
        (follow-redirect)
        (has (status? 200))
        (within [:.light-article :> :h1]
                (has (text? "Dashboard (fixture)")))
        (follow "logout")
        (follow-redirect)
        (has (status? 200))
        (within [:nav [:li enlive/first-child] :a]
                (has (text? "login"))))))

(deftest user-with-password-wipe-gets-message
  (-> (session help/clojars-app)
      (register-as "fixture" "fixture@example.org" "password"))
  (korma/update db/users
                (korma/set-fields {:password ""})
                (korma/where {:user "fixture"}))
  (-> (session help/clojars-app)
      (login-as "fixture" "password")
      (follow-redirect)
      (has (status? 200))
      (within [:div :p.error]
              (has (text? "Incorrect username and/or password.")))))
