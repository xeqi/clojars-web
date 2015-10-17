(ns clojars.main
  (:require [clojars
             [config :as config]
             [system :as system]]
            [clojars.component
             [nrepl :refer [nrepl-server-component]]
             [yeller :refer [yeller-component]]]
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
         :error-handler (yeller-component {:token (:yeller-token config)
                                           :environment (:yeller-environment config)})
         :nrepl-server
         (component/using (nrepl-server-component {:port (:nrepl-port config)})
                          [:db])))

(def system (prod-system config))

(defn -main [& args]
  (config/process-args args)
  (println "clojars-web: starting jetty on" (str "http://" (:bind config) ":" (:port config)))
  (alter-var-root #'system component/start)
  (Thread/setDefaultUncaughtExceptionHandler (get-in system [:error-handler :client])))
