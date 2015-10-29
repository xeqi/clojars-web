(ns clojars.db
  (:require [clojars.db.sql :as sql]
            [clojure.string :as str]
            [clj-time.jdbc])
  (:import java.util.concurrent.Executors))

(defn find-user [db username]
  (sql/find-user {:username username}
                 {:connection db
                  :result-set-fn first}))

(defn find-user-by-user-or-email [db username-or-email]
  (sql/find-user-by-user-or-email {:username_or_email username-or-email}
                                  {:connection db
                                   :result-set-fn first}))

(defn find-user-by-password-reset-code [db reset-code time]
  (sql/find-user-by-password-reset-code {:reset_code reset-code
                                         :reset_code_created_at time}
                                        {:connection db
                                         :result-set-fn first}))

(defn find-groupnames [db username]
  (sql/find-groupnames {:username username}
                       {:connection db
                        :row-fn :name}))

(defn group-membernames [db groupname]
  (sql/group-membernames {:groupname groupname}
                         {:connection db
                          :row-fn :user}))

(defn group-keys [db groupname]
  (sql/group-keys {:groupname groupname}
                  {:connection db
                   :row-fn :pgp_key}))

(defn jars-by-username [db username]
  (sql/jars-by-username {:username username}
                        {:connection db}))

(defn jars-by-groupname [db groupname]
  (sql/jars-by-groupname {:groupname groupname}
                         {:connection db}))

(defn recent-versions
  ([db groupname jarname]
   (sql/recent-versions {:groupname groupname
                         :jarname jarname}
                        {:connection db}))
  ([db groupname jarname num]
   (sql/recent-versions-limit {:groupname groupname
                               :jarname jarname
                               :num num}
                              {:connection db})))

(defn count-versions [db groupname jarname]
  (sql/count-versions {:groupname groupname
                       :jarname jarname}
                      {:connection db
                       :result-set-fn first
                       :row-fn :count}))

(defn recent-jars [db]
  (sql/recent-jars {} {:connection db}))

(defn jar-exists [db groupname jarname]
  (sql/jar-exists {:groupname groupname
                   :jarname jarname}
                  {:connection db
                   :result-set-fn first
                   :row-fn #(= % 1)}))

(defn find-jar
  ([db groupname jarname]
   (sql/find-jar {:groupname groupname
                  :jarname jarname}
                 {:connection db
                  :result-set-fn first}))
  ([db groupname jarname version]
   (sql/find-jar-versioned {:groupname groupname
                            :jarname jarname
                            :version version}
                           {:connection db
                            :result-set-fn first})))

(defn all-projects [db offset-num limit-num]
  (sql/all-projects {:num limit-num
                     :offset offset-num}
                    {:connection db}))

(defn count-all-projects [db]
  (sql/count-all-projects {}
                          {:connection db
                           :result-set-fn first
                           :row-fn :count}))

(defn count-projects-before [db s]
  (sql/count-projects-before {:s s}
                             {:connection db
                              :result-set-fn first
                              :row-fn :count}))

(def write-executor (memoize #(Executors/newSingleThreadExecutor)))

(def ^:private ^:dynamic *in-executor* nil)

(defn serialize-task* [task-name task]
  (if *in-executor*
    (task)
    (binding [*in-executor* true]
      (let [bound-f (bound-fn []
                      (try
                        (task)
                        (catch Throwable e
                          e)))
            response (deref
                      (.submit (write-executor) bound-f)
                      10000 ::timeout)]
        (cond
          (= response ::timeout) (throw
                                  (ex-info
                                   "Timed out waiting for serialized task to run"
                                   {:name task-name}))
          (instance? Throwable response) (throw
                                          (ex-info "Serialized task failed"
                                                   {:name task-name}
                                                   response))
          :default response)))))

(defmacro serialize-task [name & body]
  `(serialize-task* ~name
                    (fn [] ~@body)))

(defn add-user [db email username password pgp-key time]
  (let [record {:email email, :username username, :password password,
                :pgp_key pgp-key :created time}
        groupname (str "org.clojars." username)]
    (serialize-task :add-user
                    (sql/insert-user! record
                                      {:connection db})
                    (sql/insert-group! {:groupname groupname :username username}
                                       {:connection db}))
    record))

(defn update-user [db account email username password pgp-key]
  (let [fields {:email email
                :username username
                :pgp_key pgp-key
                :account account
                :password password}]
    (serialize-task :update-user
                    (sql/update-user! fields
                                      {:connection db}))
    fields))

(defn update-user-password [db reset-code password]
  (assert (not (str/blank? reset-code)))
  (serialize-task :update-user-password
                    (sql/update-user-password! {:password password
                                                :reset_code reset-code}
                                               {:connection db})))


(defn set-password-reset-code! [db username-or-email reset-code time]
  (serialize-task :set-password-reset-code
                  (sql/set-password-reset-code! {:reset_code reset-code
                                                 :reset_code_created_at time
                                                 :username_or_email username-or-email}
                                                {:connection db})))

(defn add-member [db groupname username added-by]
  (serialize-task :add-member
                  (sql/add-member! {:groupname groupname
                                    :username username
                                    :added_by added-by}
                                   {:connection db})))

(defn add-jar [db account {:keys [group name version
                               description homepage authors]} time]
  (serialize-task :add-jar
                  (sql/add-jar! {:groupname group
                                 :jarname   name
                                 :version   version
                                 :user      account
                                 :created    time
                                 :description description
                                 :homepage   homepage
                                 :authors    authors}
                                {:connection db})))

(defn delete-jars [db group-id & [jar-id version]]
  (serialize-task :delete-jars
                  (let [coords {:group_id group-id}]
                    (if jar-id
                      (let [coords (assoc coords :jar_id jar-id)]
                        (if version
                          (sql/delete-jar-version! (assoc coords :version version)
                                                   {:connection db})
                          (sql/delete-jars! coords
                                            {:connection db})))
                      (sql/delete-groups-jars! coords
                                        {:connection db})))))

;; does not delete jars in the group. should it?
(defn delete-groups [db group-id]
  (serialize-task :delete-groups
                  (sql/delete-group! {:group_id group-id}
                                     {:connection db})))

(defn find-jars-information
  ([db group-id]
   (find-jars-information db group-id nil))
  ([db group-id artifact-id]
   (if artifact-id
     (sql/find-jars-information {:group_id group-id
                                 :artifact_id artifact-id}
                                {:connection db})
     (sql/find-groups-jars-information {:group_id group-id}
                                       {:connection db}))))

(defn promote [db group name version time]
  (serialize-task :promote
                  (sql/promote! {:group_id group
                                 :artifact_id name
                                 :version version
                                 :promoted_at time}
                                {:connection db})))

(defn promoted? [db group-id artifact-id version]
  (sql/promoted {:group_id group-id
                 :artifact_id artifact-id
                 :version version}
                {:connection db
                 :result-set-fn first
                 :row-fn :promoted_at}))
