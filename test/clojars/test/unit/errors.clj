(ns clojars.test.unit.errors
  (:require [clojars
             [main :as main]
             [ports :as ports]]
            [clojars.middleware.errors :refer :all]
            [clojure.test :refer :all]
            [kerodon
             [core :refer :all]
             [test :refer :all]]))

(deftest prod-env-displays-error-page
  (is (-> main/prod-env
          (get-in [:app :middleware])
          set
          (contains? [wrap-exceptions :error-handler]))))

(deftest wrap-exceptions-returns-error-page-with-error-id
  (-> (session (wrap-exceptions (fn [req] (throw (Exception.)))
                                (reify ports/ErrorHandler
                                  (-report [t e extra] "ERROR"))))
      (visit "/error")
      (within [:div.small-section :> :h1]
              (has (text? "Oops!")))
      (within [:div.small-section :> :pre.error-id]
              (has (text? "error-id:\"ERROR\"")))))
