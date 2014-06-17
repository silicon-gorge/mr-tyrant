(ns tyranitar.store
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.java.io :refer [as-file]]
            [clojure.string :refer [upper-case split]]
            [clojure.tools.logging :as log]
            [clostache.parser :as templates]
            [environ.core :refer [env]]
            [overtone.at-at :as at]
            [slingshot.slingshot :refer [throw+]]
            [tyranitar
             [environments :as environments]
             [git :as git]])
  (:import [org.eclipse.jgit.api.errors InvalidRemoteException]
           [org.eclipse.jgit.errors MissingObjectException]))

(def ^:private pool
  (atom nil))

(def ^:private git-health-atom
  (atom false))

(defn- poke-properties
  "Default properties for poke environment"
  [app-name env]
  {:app-name app-name
   :env-name env
   :error-logging-url "http://errorlogging.music.cq3.brislabs.com:8080/ErrorLogging/1.x"
   :instance-type "m1.small"
   :graphite-host "carbon.brislabs.com"
   :is-prod false
   :ssh-security-group "Brislabs-SSH"
   :web-security-group "Brislabs-8080"})

(defn- prod-properties
  "Default properties for prod environment"
  [app-name env]
  {:app-name app-name
   :env-name env
   :error-logging-url "http://errorlogging.ent.nokia.com:8080/ErrorLogging/1.x"
   :instance-type "m1.small"
   :graphite-host "carbon.ent.nokia.com"
   :is-prod true
   :ssh-security-group "AppGate"
   :web-security-group "internal-8080"
   :scanner-security-group "ICM Scanning"})

(defn- repo-name
  [application env]
  (str application "-" env))

(defn- repo-exists?
  [repo-name]
  (.exists (as-file (git/repo-path repo-name))))

(defn- ensure-repo-up-to-date
  "Gets or updates the specified repo from Git"
  [repo-name]
  (if (repo-exists? repo-name)
    (git/pull-repo repo-name)
    (do
      (log/debug (str "Repo '" repo-name "' not found - attempting to clone"))
      (git/clone-repo repo-name))))

(defn get-data
   "Fetches the data corresponding to the given params from Git"
  [env app commit category]
  (let [repo-name (repo-name app env)]
    (try
      (ensure-repo-up-to-date repo-name)
      (git/get-exact-commit repo-name category (upper-case commit))
      (catch InvalidRemoteException e
        (log/warn e (str "Can't communicate with remote repo '" repo-name "'"))
        nil)
      (catch NullPointerException e
        (log/warn e (str "Revision '" commit "' not found in repo '" repo-name "'"))
        nil)
      (catch MissingObjectException e
        (log/warn e (str "Missing object for revision '" commit "' in repo '" repo-name "'"))
        nil))))

(defn get-commits
  "Get a list of the 20 most recent commits to the repository in most recent first order."
  [env app]
  (let [repo-name (repo-name app env)]
    (try
      (ensure-repo-up-to-date repo-name)
      (git/fetch-recent-commits repo-name)
      (catch InvalidRemoteException e
        (log/warn e (str "Can't communicate with remote repo '" repo-name "'"))
        nil))))

(defn- git-connection-working?
  "Returns true if the remote repository is available and behaving as expected"
  []
  (let [repo-name (repo-name "tyranitar" "poke")]
    (try
      (when-not (repo-exists? repo-name)
        (git/clone-repo repo-name))
      (some? (git/can-connect repo-name))
      (catch Exception e
        (log/warn e "Cannot connect to repository!")
        false))))

(def snc-url
  (str (env :service-snc-api-base-url)
       "projects/tyranitar/repositories?api_username="
       (env :service-snc-api-username)
       "&api_secret="
       (env :service-snc-api-secret)))

(defn- data-from-repo-item
  [item]
  (let [name (:name item)
        name-parts (split name #"-")
        app-name (first name-parts)
        env (second name-parts)
        access-methods (:access_methods item)
        ssh-repo-path (:uri (first (filterv #(= (:method %) "ssh") access-methods)))]
    {:app app-name :env env :name name :path ssh-repo-path}))

(defn- get-repo-list-from-snc
  []
  (let [response (http/get snc-url {:as :json :throw-exceptions false})
        body (:body response)]
    body))

(defn- process-repository-list
  "Process the list of data returned from SNC into the form that we want."
  [list]
  (let [data (map data-from-repo-item list)
        grouped-app-list (group-by :app data)
        result (into (sorted-map) (map (fn [[k v]] {(keyword k) {:repositories (mapv #(dissoc % :app :env) v)}}) grouped-app-list))]
    result))

(defn get-repository-list
  "Returns a list of all repositories that exist in the tyranitar Git SNC project."
  ([]
     (process-repository-list (get-repo-list-from-snc)))
  ([env]
     (process-repository-list (filter #(.endsWith (:name %) env) (get-repo-list-from-snc)))))

(defn- repo-create-body
  [name env]
  (let [repo-name (repo-name name env)]
      (str "repository[name]=" repo-name "&repository[kind]=Git")))

(defn- create-repository
  [name env]
  (let [response (http/post snc-url {:body (repo-create-body name env)
                                     :content-type "application/x-www-form-urlencoded"
                                     :throw-exceptions false})
        status (:status response)]
    (when (not= status 200)
      (throw+ {:status status :message (:message (json/parse-string (:body response) true))}))))

(defn dest-path
  "Gets the file path in the repo to write to for the given params."
  [app-name env category]
  (let [repo-path (git/repo-path (repo-name app-name env))]
    (str repo-path "/" category ".json")))

(defn write-templated-properties
  "Substitutes the application name for placeholders in the given template and writes the file."
  [app-name template env]
  (let [data (if (= "prod" env) (prod-properties app-name env) (poke-properties app-name env))
        dest-path (dest-path app-name env template)]
    (spit dest-path (templates/render-resource (str template ".json") data))))

(defn- write-default-properties
  "Writes a default set of service properties to the Git repo file location."
  [app-name env]
  (let [repo-path (git/repo-path (repo-name app-name env))]
    (write-templated-properties app-name "application-properties" env)
    (write-templated-properties app-name "deployment-params" env)
    (write-templated-properties app-name "launch-data" env)))

(defn create-application-env
  [application environment]
  (let [repo-name (repo-name application environment)]
    (create-repository application environment)
    (git/clone-repo repo-name)
    (write-default-properties application environment)
    (git/commit-and-push repo-name "Initial properties files.")
    {:name repo-name :path (git/repo-url repo-name)}))

(defn create-application
  [application]
  (let [default-environments (environments/default-environments)
        repos (map (fn [[k _]] (create-application-env application (name k))) default-environments)]
    {:repositories repos}))

(defn write-properties-file
  "Writes the given properties to the appropriate local Git file."
  [app-name env category props]
  (let [dest-path (dest-path app-name env category)]
    (spit dest-path (json/generate-string props {:pretty true}))))

(defn update-properties
  "Adds or updates the given properties in the given app, env and category."
  [app-name env category tokens]
  (let [orig (get-data env app-name "head" category)
        updated (into (sorted-map) (merge (:data orig) tokens))]
    (write-properties-file app-name env category updated)
    (git/commit-and-push (repo-name app-name env) "Updated properties")
    (get-data env app-name "head" category)))

(defn update-git-health
  []
  (reset! git-health-atom (git-connection-working?)))

(defn git-healthy?
  []
  @git-health-atom)

(defn create-pool
  []
  (when-not @pool
    (reset! pool (at/mk-pool :cpu-count 1))))

(defn init
  []
  (create-pool)
  (at/interspaced (* 1000 12) update-git-health @pool :initial-delay 0))
