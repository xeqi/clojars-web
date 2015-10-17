(ns clojars.component.lossy-handler
  (:require [clojars.ports :as ports]))

(defrecord LossyHandler []
  ports/ErrorHandler
  (-report [t e extra] "error-id"))
