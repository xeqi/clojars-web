(ns clojars.component.yeller
  (:require [clojars.ports :as ports]
            [clojure.repl :refer [pst]]
            [com.stuartsierra.component :as component]
            [yeller.clojure.client :as yeller])
    (:import java.util.UUID))

(defn error-id []
  (str (UUID/randomUUID)))

(defrecord Yeller [token environment]
  component/Lifecycle
  (start [t]
    (if-not (:client t)
      (do (println "clojars-web: enabling yeller client")
          (assoc t :client (yeller/client {:token token})))
      t))
  (stop [t]
    (assoc t :client nil))
  ports/ErrorHandler
  (-report [t e extra]
    (let [id (error-id)]
      (println "ERROR ID:" id)
      (pst e)
      (yeller/report (:client t) e
                     (-> extra
                         (assoc :environment environment)
                         (assoc-in [:custom-data :error-id] id)))
      id)))

(defn yeller-component [options]
  (map->Yeller options))
