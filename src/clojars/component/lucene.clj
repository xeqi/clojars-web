(ns clojars.component.lucene
  (:require [clucy.core :as clucy]
            [com.stuartsierra.component :as component]))

(defrecord Lucene [path]
  component/Lifecycle
  (start [t]
    (if-not (:index t)
      (let [index (clucy/disk-index path)]
        (do (.open index)
            (assoc t :index index)))
      t))
  (stop [t]
    (when-let [index (:index t)]
      (.close index))
    (assoc t :index nil)))

(defn lucene-component [path]
  (map->Lucene {:path path}))
