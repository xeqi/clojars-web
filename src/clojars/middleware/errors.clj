(ns clojars.middleware.errors
  (:require [clojars.ports :as ports]
            [clojars.web.error-page :as error-page]
            [yeller.clojure.ring :as yeller-ring]))

(defn wrap-exceptions [app report]
  (fn [req]
    (try
      (app req)
      (catch Throwable t
        (->> (ports/report report t (yeller-ring/format-extra nil req))
             (error-page/error-page-response))))))
