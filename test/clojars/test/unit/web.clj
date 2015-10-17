(ns clojars.test.unit.web
  (:require [clojars.test.test-helper :as help]
            [clojure.test :refer :all]
            [ring.mock.request :refer [header request]]))

(use-fixtures :each
  help/default-fixture
  help/with-clean-database)

(defn cookies [res]
  (flatten [(get-in res [:headers "Set-Cookie"])]))

(defn cookies-http-only? [res]  
  (every? #(.contains % "HttpOnly") (cookies res)))

(defn cookies-secure? [res]
  (every? #(.contains % "HttpOnly") (res cookies)))

(deftest https-cookies-are-secure
  (let [res ((help/clojars-ui) (assoc (request :get "/") :scheme :https))]
    (is (cookies-secure? res))
    (is (cookies-http-only? res))))

(deftest forwarded-https-cookies-are-secure
  (let [res ((help/clojars-ui) (-> (request :get "/")
                                    (header "x-forward-proto" "https")))]
    (is (cookies-secure? res))
    (is (cookies-http-only? res))))

(deftest regular-cookies-are-http-only
  (let [res ((help/clojars-ui) (request :get "/"))]
    (is (cookies-http-only? res))))
