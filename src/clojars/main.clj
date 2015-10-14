(ns clojars.main
  (:require [clojars
             [admin :as admin]
             [config :as config]
             [system :as system]]
            [clojars.component.yeller :refer [yeller-component]]
            [clojars.middleware.errors :as errors]
            [com.stuartsierra.component :as component]
            [meta-merge.core :refer [meta-merge]])
  (:gen-class))

(def prod-env
  {:app {:middleware [[errors/wrap-exceptions :error-handler]]}})

(def config
  (meta-merge config/config
              prod-env))

(defn prod-system [config]
  (assoc (system/new-system config)
         :error-handler (yeller-component (:error-handler config))))

(defn -main [& args]
  (config/process-args args)
  (println "clojars-web: starting jetty on" (str "http://" (:bind config) ":" (:port config)))
  (let [system (component/start (prod-system (system/translate config)))]
    (Thread/setDefaultUncaughtExceptionHandler (get-in system [:error-handler :client])))
  (admin/init))
