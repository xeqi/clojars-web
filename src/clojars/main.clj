(ns clojars.main
  (:require [clojars
             [admin :as admin]
             [config :as config]
             [errors :as errors]
             [system :as system]]
            [com.stuartsierra.component :as component]
            [meta-merge.core :refer [meta-merge]])
  (:gen-class))

(def prod-env
  {:app {:middleware []}})

(def config
  (meta-merge config/config
              prod-env))

(defn -main [& args]
  (config/process-args args)
  (errors/register-global-exception-handler!)
  (println "clojars-web: starting jetty on" (str "http://" (:bind config) ":" (:port config)))
  (component/start (system/new-system config))
  (admin/init))
