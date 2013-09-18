(ns tyranitar.git
  (:require [environ.core :refer [env]]
            [clojure.java.io :refer [as-file]]
            [cheshire.core :refer [parse-string]])
  (:import [org.eclipse.jgit.api Git MergeCommand MergeCommand$FastForwardMode]
           [org.eclipse.jgit.revwalk RevWalk]
           [org.eclipse.jgit.treewalk TreeWalk]
           [java.io FileNotFoundException]))

(def base-git-url (env :service-base-git-repository-url))
(def base-git-path (env :service-base-git-repository-path))

(defn repo-url
  [repo-name]
  (str base-git-url repo-name))

(defn repo-path
  [name]
  (str base-git-path name))

(defn clone-repo
  [repo-name]
  (->
   (Git/cloneRepository)
   (.setURI (repo-url repo-name))
   (.setDirectory (as-file (repo-path repo-name)))
   (.setRemote "origin")
   (.setBranch "master")
   (.setBare false)
   (.call)))

(defn pull-repo
  [repo-name]
  (let [git (Git/open (as-file (repo-path repo-name)))]
    (->
     (.fetch git)
     (.call))
    (let [repo (.getRepository git)
          origin-master (.resolve repo "origin/master")]
      (->
       (.merge git)
       (.include origin-master)
       (.call)))))

(defn get-exact-commit
  [repo-name category commit]
  (let [git (Git/open (as-file (repo-path repo-name)))
        repo (.getRepository git)
        commit-id (.resolve repo commit)
        rwalk (RevWalk. repo)
        commit (.parseCommit rwalk commit-id)
        tree (.getTree commit)
        twalk (TreeWalk/forPath repo (str category ".json") tree)
        loader (.open repo (.getObjectId twalk 0))
        text-result (slurp (.openStream loader))]
    {:hash (.getName commit-id)
     :data (parse-string text-result)}))

(defn- read-application-json-file
  [repo-name category]
  (get-exact-commit repo-name category "HEAD")
  )

(defn repo-exists?
  [repo-name]
  (.exists (as-file (repo-path repo-name))))

(defn- ensure-repo-up-to-date
  [repo-name]
  (if (repo-exists? repo-name)
    (pull-repo repo-name)
    (clone-repo repo-name)))

(defn get-data
  [env app commit category]
  (let [repo-name (str app "-" env)]
    (ensure-repo-up-to-date repo-name)
    (if (= commit "latest")
      (read-application-json-file repo-name category)
      (get-exact-commit repo-name category commit))))
