(ns clojars.system
  (:require [clojars
             [ring-servlet-patch :as patch]
             [web :as web]]
            [com.stuartsierra.component :as component]
            [duct.component
             [endpoint :refer [endpoint-component]]
             [handler :refer [handler-component]]]
            [meta-merge.core :refer [meta-merge]]
            [ring.component.jetty :refer [jetty-server]]))

(def base-env
  {:app {:middleware []}
   :http {:configurator patch/use-status-message-header}})

(defn translate [config]
  (let [{:keys [port bind]} config]
    (assoc config :http {:port port :host bind})))

(defn new-system [config]
  (let [config (meta-merge base-env (translate config))]
    (-> (component/system-map
         :app  (handler-component (:app config))
         :http (jetty-server (:http config))
         :ui   (endpoint-component web/ui)
         :repo (endpoint-component web/repo))
        (component/system-using
         {:http [:app]
          :app  [:repo :ui]
          :ui   []
          :repo []}))))
