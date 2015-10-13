(defproject clojars-web "0.18.1-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.cli "0.2.1"]
                 [yeller-clojure-client "1.2.1"]
                 [org.apache.maven/maven-model "3.0.4"
                  :exclusions
                  [org.codehaus.plexus/plexus-utils]]
                 [com.cemerick/pomegranate "0.3.0"
                  :exclusions [org.apache.httpcomponents/httpcore]]
                 [s3-wagon-private "1.0.0"]
                 [compojure "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [ring-middleware-format "0.5.0" :exclusions [org.clojure/tools.reader]]
                 [hiccup "1.0.5"]
                 [cheshire "5.4.0"]
                 [korma "0.3.0-beta10"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [org.apache.commons/commons-email "1.2"]
                 [commons-codec "1.8"]
                 [net.cgrand/regex "1.0.1"
                  :exclusions [org.clojure/clojure]]
                 [clj-time "0.9.0"]
                 [com.cemerick/friend "0.2.1"
                  :exclusions [org.clojure/core.cache
                               org.apache.httpcomponents/httpclient
                               org.apache.httpcomponents/httpcore
                               commons-logging
                               slingshot]]
                 [clj-stacktrace "0.2.7"]
                 [ring/ring-anti-forgery "1.0.0"]
                 [valip "0.2.0" :exclusions [commons-logging]]
                 [clucy "0.3.0"]
                 [org.clojure/tools.nrepl "0.2.11"]
                 [mvxcvi/clj-pgp "0.8.0"]
                 [com.stuartsierra/component "0.2.3"]]
  :profiles {:dev {:dependencies [[kerodon "0.0.7"
                                   :exclusions [org.apache.httpcomponents/httpcore]]
                                  [clj-http-lite "0.2.1"]]
                   :resource-paths ["local-resources"]}}
  :plugins [[lein-ring "0.8.5"]]
  :aliases {"migrate" ["run" "-m" "clojars.db.migrate"]}
  :ring {:handler clojars.web/clojars-app}
  :main clojars.main
  :min-lein-version "2.0.0"
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
