(ns clojars.ring-servlet-patch
  (:require [ring.util.servlet :as ring-servlet])
  (:import [javax.servlet.http
            HttpServlet
            HttpServletRequest
            HttpServletResponse
            HttpServletResponseWrapper]
           [org.eclipse.jetty.server Request]
           [org.eclipse.jetty.server.handler AbstractHandler]))


(defn response-wrapper [response]
  (proxy [HttpServletResponseWrapper] [response]
    (setHeader [name value]
      (if (= name "status-message")
        (.setStatus this (.getStatus this) value)
        (proxy-super setHeader name value)))))

(defn ^AbstractHandler handler-wrapper [handler]
  (proxy [AbstractHandler] []
    (handle [target ^Request base-request request response]
      (let [response (response-wrapper response)]
        (.handle handler target base-request request response)))))

(defn use-status-message-header [server]
  (let [handler (.getHandler server)]
    (.setHandler server (handler-wrapper handler))))
