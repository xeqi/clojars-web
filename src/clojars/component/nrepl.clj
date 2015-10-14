(ns clojars.component.nrepl
  (:require [clojure.tools.nrepl.server :as nrepl-server]
            [com.stuartsierra.component :as component]))

(defrecord NreplServer [port]
  component/Lifecycle
  (start [t]
    (if-not (:server t)
      (do (printf "clojars-web: starting nrepl on localhost:%s\n" port)
          (assoc t :server
                 (nrepl-server/start-server :port port
                                            :bind "127.0.0.1")))
      t))
  (stop [t]
    (when-let [server (:server t)]
      (nrepl-server/stop-server server))
    (assoc t :server nil)))

(defn nrepl-server-component [options]
  (map->NreplServer options))
