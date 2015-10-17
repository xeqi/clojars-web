(ns clojars.system
  (:require [clojars
             [config :refer [config]]
             [ring-servlet-patch :as patch]
             [web :as web]]
            [clojars.component.lucene :refer [lucene-component]]
            [com.stuartsierra.component :as component]
            [duct.component
             [endpoint :refer [endpoint-component]]
             [handler :refer [handler-component]]
             [hikaricp :refer [hikaricp]]]
            [meta-merge.core :refer [meta-merge]]
            [ring.component.jetty :refer [jetty-server]]))

(def base-env
  {:app {:middleware []}
   :http {:configurator patch/use-status-message-header}})

(defn translate [config]
  (let [{:keys [port bind]} config]
    (assoc config
           :http {:port port :host bind})))

(defn new-system [config]
  (let [config (meta-merge base-env (translate config))]
    (-> (component/system-map
         :app  (handler-component (:app config))
         :http (jetty-server (:http config))
         :ui   (endpoint-component web/ui)
         :repo (endpoint-component web/repo)
         :db   (hikaricp (:db config))
         :index (lucene-component (:index-path config)))
        (component/system-using
         {:http [:app]
          :app  [:repo :ui :error-handler]
          :ui   [:error-handler :db]
          :repo [:error-handler :db]}))))
