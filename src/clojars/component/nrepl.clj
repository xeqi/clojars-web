(ns clojars.component.nrepl
  (:require [clojars.admin :as admin]
            [clojure.tools.nrepl
             [server :refer [default-handler start-server stop-server]]]
            [com.stuartsierra.component :as component]))

(defn bind-components-for-global-admin-functions [db index]
  (with-meta
    (fn [h]
      (fn [{:keys [session] :as msg}]
        (swap! session
               assoc
               #'admin/*db* (:spec db)
               #'admin/*index* (:index index))
        (h msg)))
    {:clojure.tools.nrepl.middleware/descriptor {:requires #{"clone"}
                                                 :expects #{"eval"}}}))

(defrecord NreplServer [port db index]
  component/Lifecycle
  (start [t]
    (if-not (:server t)
      (do (printf "clojars-web: starting nrepl on localhost:%s\n" port)
          (assoc t :server
                 (start-server :port port
                               :bind "127.0.0.1"
                               :handler (default-handler
                                          (bind-components-for-global-admin-functions
                                           db
                                           index)))))
      t))
  (stop [t]
    (when-let [server (:server t)]
      (stop-server server))
    (assoc t :server nil)))

(defn nrepl-server-component [options]
  (map->NreplServer options))
