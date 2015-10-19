(ns user
  (:require [clojars
             [config :as config]
             [system :as system]]
            [clojars.component.lossy-handler :as lossy]
            [clojars.web.safe-hiccup :as safe-hiccup]
            [clojure.java.io :as io]
            [clojure.tools.namespace.repl :refer [refresh]]
            [eftest.runner :as eftest]
            [hiccup.page :as page]
            [meta-merge.core :refer [meta-merge]]
            [reloaded.repl :refer [system init start stop go reset]]
            [ring.middleware.stacktrace :as stacktrace]))

(def doctype
  {:html5 (safe-hiccup/raw (page/doctype :html5))})

(def style (comp safe-hiccup/raw @#'stacktrace/style-resource))

(defn wrap-stacktrace [h]
  "this is a hack to allow wrap-stacktrace to work with our custom hiccup escaping
by default. If the view system is ever changed, remove this"
  (fn [r]
    (with-redefs [page/doctype doctype
                  stacktrace/style-resource style]
      ((stacktrace/wrap-stacktrace h) r))))

(def dev-env
  {:app {:middleware [wrap-stacktrace]}})

(def config
  (meta-merge config/config
              dev-env))

(defn new-system []
  (assoc (system/new-system (system/translate config))
         :error-handler (lossy/->LossyHandler)))

(ns-unmap *ns* 'test)

(defn test [& tests]
  (let [tests (if (empty? tests)
                (eftest/find-tests "test")
                tests)]
    (eftest/run-tests tests {:report eftest.report.pretty/report
  ;                           :multithread? false
                             })))

(when (io/resource "local.clj")
  (load "local"))

;; TODO: function to setup fake data (from clojars.dev.setup?)

(reloaded.repl/set-init! new-system)
