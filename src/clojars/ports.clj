(ns clojars.ports)

(defprotocol ErrorHandler
  (-report [t e extra]))

(defn report
  ([error-handler error] (report error-handler error nil))
  ([error-handler error extra]
   (-report error-handler error extra)))
