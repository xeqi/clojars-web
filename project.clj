(defproject clojars-web "0.18.1-SNAPSHOT"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.cli "0.2.1"]
                 [yeller-clojure-client "1.2.1"]
                 [org.apache.maven/maven-model "3.0.4"
                  :exclusions
                  [org.codehaus.plexus/plexus-utils]]
                 [com.cemerick/pomegranate "0.0.13"
                  :exclusions
                  [org.apache.httpcomponents/httpcore
                   commons-logging]]
                 [s3-wagon-private "1.0.0"]
                 [compojure "1.3.3"]
                 [ring/ring-jetty-adapter "1.1.1"]
                 [ring-middleware-format "0.5.0"]
                 [hiccup "1.0.3"]
                 [cheshire "5.4.0"]
                 [org.xerial/sqlite-jdbc "3.8.11.2"]
                 [org.apache.commons/commons-email "1.2"]
                 [commons-codec "1.6"]
                 [net.cgrand/regex "1.0.1"
                  :exclusions [org.clojure/clojure]]
                 [clj-time "0.9.0"]
                 [com.cemerick/friend "0.2.1"
                  :exclusions [org.clojure/core.cache
                               org.apache.httpcomponents/httpclient
                               org.apache.httpcomponents/httpcore
                               commons-logging]]
                 [clj-stacktrace "0.2.6"]
                 [ring-anti-forgery "0.2.1"]
                 [valip "0.2.0" :exclusions [commons-logging]]
                 [clucy "0.3.0"]
                 [org.clojure/tools.nrepl "0.2.11"]
                 [org.bouncycastle/bcpg-jdk15on "1.47"]
                 [mvxcvi/clj-pgp "0.8.0"]
                 [yesql "0.5.1"]
                 [com.zaxxer/HikariCP "2.4.1"]
                 [org.slf4j/slf4j-nop "1.7.7"]]
  :main ^:skip-aot clojars.main
  :target-path "target/%s/"
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :aliases {"migrate" ["run" "-m" "clojars.db.migrate"]}
  :profiles
  {:dev  [:project/dev  :profiles/dev]
   :test [:project/test :profiles/test]
   :uberjar {:aot :all}
   :profiles/dev  {}
   :profiles/test {}
   :project/dev   {:dependencies [[kerodon "0.0.7"]
                                  [clj-http-lite "0.2.1"]]
                   :resource-paths ["local-resources"]}
   :project/test  {}})
