(ns tyranitar.git
  (:require [environ.core :refer [env]]
            [clojure.java.io :refer [as-file]]
            [cheshire.core :refer [parse-string]])
  (:import [org.eclipse.jgit.api Git MergeCommand MergeCommand$FastForwardMode]
           [java.io FileNotFoundException]))

(def base-git-url (env :service-base-git-repository-url))
(def base-git-path (env :service-base-git-repository-path))

(defn application-repository-url
  [name]
  (str base-git-url name))

(defn application-repository-path
  [name]
  (str base-git-path name))

(defn clone-application-repository
  [name]
  (->
   (Git/cloneRepository)
   (.setURI (application-repository-url name))
   (.setDirectory (as-file (application-repository-path name)))
   (.setRemote "origin")
   (.setBranch "master")
   (.setBare false)
   (.call)))

(defn pull-application-repository
  [name]
  (let [git (Git/open (as-file (application-repository-path name)))]
    (->
     (.fetch git)
     (.call))
    (let [repo (.getRepository git)
          origin-master (.resolve repo "origin/master")]
      (->
       (.merge git)
       (.include origin-master)
       (.call))))
  )

(defn- read-application-json-file
  [application-name category]
  (try
    (parse-string (slurp (as-file (str (application-repository-path application-name) "/" category ".json"))))
    (catch FileNotFoundException e nil)))

(defn- read-service-properties
  [name]
  (parse-string (slurp (as-file (str (application-repository-path name) "/service-properties.json"))) ))

(defn application-repository-exists?
  [name]
  (.exists (as-file (application-repository-path name))))

(defn- update-application-repository
  [name]
  (if (application-repository-exists? name)
    (pull-application-repository name)
    (clone-application-repository name)))

(defn current-application-properties
  [name]
  (update-application-repository name)
  (read-service-properties name))

(defn get-data
  [name category & commit]
  (update-application-repository name)
  (read-application-json-file name category))
