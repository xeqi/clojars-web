(ns clojars.test.integration.steps
  (:require [cemerick.pomegranate.aether :as aether]
            [clojars.config :refer [config]]
            [clojars.db :as db]
            [clojars.maven :as maven]
            [clojure.java.io :as io]
            [clojure.test :as test]
            [clojars.test.test-helper :as help]
            [kerodon.core :refer :all])
  (:import java.nio.file.Files
           java.nio.file.LinkOption
           java.nio.file.OpenOption
           java.nio.file.StandardOpenOption
           java.nio.file.attribute.FileAttribute))

(defn login-as [state user password]
  (-> state
      (visit "/")
      (follow "login")
      (fill-in "Username" user)
      (fill-in "Password" password)
      (press "Login")))

(defn register-as
  ([state user email password]
     (register-as state user email password password))
  ([state user email password confirm]
     (-> state
         (visit "/")
         (follow "register")
         (fill-in "Email" email)
         (fill-in "Username" user)
         (fill-in "Password" password)
         (fill-in "Confirm password" confirm)
         (press "Register"))))

(defn add-nio-repo [id fs]
  (with-out-str
    (aether/register-wagon-factory!
     id
     #(reify org.apache.maven.wagon.Wagon
        (getRepository [_]
          (proxy [org.apache.maven.wagon.repository.Repository] []))
        (^void connect [_
                        ^org.apache.maven.wagon.repository.Repository _
                        ^org.apache.maven.wagon.authentication.AuthenticationInfo _
                        ^org.apache.maven.wagon.proxy.ProxyInfoProvider _])
        (disconnect [_])
        (removeTransferListener [_ _])
        (addTransferListener [_ _])
        (setTimeout [_ _])
        (setInteractive [_ _])
        (get [_ name file]
          (let [path (.getPath fs (:repo config) (into-array String [name]))]
            (if (Files/exists path (into-array LinkOption []))
              (with-open [r (maven/path-to-reader path)]
                (io/copy r file))
              (throw (org.apache.maven.wagon.ResourceDoesNotExistException. "")))))
        (put [_ file destination]
          (let [path (.getPath fs (:repo config) (into-array String [destination]))]
            (Files/createDirectories (.getParent path) (into-array FileAttribute []))
            (with-open [s (Files/newOutputStream path
                                                 (into-array OpenOption
                                                             [StandardOpenOption/CREATE
                                                              StandardOpenOption/WRITE]))]
              (io/copy file s))))))))

(defn inject-artifacts-into-repo! [db fs user jar pom]
  (let [pom-file (io/resource pom)
        jarmap (maven/reader-to-map (io/reader pom-file))]
    (db/add-jar db user jarmap)
    (let [id (str (java.util.UUID/randomUUID))]
      (add-nio-repo id fs)
      (try
         (aether/deploy :coordinates [(keyword (:group jarmap)
                                               (:name jarmap))
                                      (:version jarmap)]
                        :jar-file (io/resource jar)
                        :pom-file pom-file
                        :repository {"nio" {:url (str id ":")
                                            :checksum false}}
                        :local-repo (str (.toURI (help/make-tmp-dir))))
         (finally
           (swap! @#'aether/wagon-factories dissoc id))))))
