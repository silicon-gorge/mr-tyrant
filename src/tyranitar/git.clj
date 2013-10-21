(ns tyranitar.git
  (:require [environ.core :refer [env]]
            [clojure.java.io :refer [as-file make-reader copy file resource]]
            [clojure.tools.logging :refer [info warn error]]
            [clj-http.client :as client]
            [clj-time.coerce :refer [from-long]]
            [clj-time.format :refer [unparse formatters]]
            [clojure.string :refer [upper-case split]]
            [cheshire.core :refer [parse-string]]
            [slingshot.slingshot :refer [try+ throw+]]
            [me.raynes.conch :as conch]
            [clostache.parser :as templates])
  (:import [org.eclipse.jgit.api Git MergeCommand MergeCommand$FastForwardMode]
           [org.eclipse.jgit.api.errors InvalidRemoteException]
           [org.eclipse.jgit.errors MissingObjectException NoRemoteRepositoryException]
           [org.eclipse.jgit.revwalk RevWalk]
           [org.eclipse.jgit.treewalk TreeWalk]
           [org.eclipse.jgit.transport JschConfigSessionFactory SshSessionFactory]
           [com.jcraft.jsch JSch]
           [java.io FileNotFoundException ByteArrayInputStream]))

;; (def ^{:dynamic true}
;;   ssh-log-levels
;;   (atom
;;    {com.jcraft.jsch.Logger/DEBUG :trace
;;     com.jcraft.jsch.Logger/INFO :debug
;;     com.jcraft.jsch.Logger/WARN :warn
;;     com.jcraft.jsch.Logger/ERROR :error
;;     com.jcraft.jsch.Logger/FATAL :fatal}))

;; (deftype SshLogger
;;    [log-level]
;;    com.jcraft.jsch.Logger
;;    (isEnabled
;;     [_ level]
;;     (>= level log-level))
;;    (log
;;     [_ level message]
;;     (logging/log "clj-ssh.ssh" (@ssh-log-levels level) nil message)))

;; (JSch/setLogger (SshLogger. com.jcraft.jsch.Logger/DEBUG))

;; The private key of the tyranitar-bot installed in SNC. Shouldn't change.
(def tyranitar-private-key
  "-----BEGIN RSA PRIVATE KEY-----
MIIEoQIBAAKCAQEA0URSQjQT7uYXG42k3X9hvKPvD1SQQBRQImmoGRh2xBL8k7In
7IRMpl1SBXIPB9dlboOfbPwGqswlr+GtD1Q7+K3hAWC1BrjuYw/khhGC18Gc5rq3
dKc0ndfvJrzpuEoMBGgG6rUQXSo/Ll5QeYMZ8a9kTY51QgsxBah2eT45H+LW4gXt
qHEgrgI0qdWXRr2wPREg/3oxLYX03kagGDO/AdQTKcMqndBSs3PdaNOcn4w9uLnC
ZjYI9Msj6G4bKSJMs1kw6tLCzfX95EKIWYiEWBmHSpDWlHx1mc+ZAosKh/Jgh5UU
rO5epG+iix0cKGj4VsPXs8S6zU0vJ80JvQ3z9wIBIwKCAQEAy0mu/nu7l52wDCMy
cMTimf5V8aKawefY0PjscCZkvnjYGnKNLubrX8heTm7MxcntgUypf9BA/bBfH+KK
27DpzQCusOGZ6UXu4+ONiY1aiHLwMJgf7am+Fa0xdhaZ5jlNgJ+RsM0l1tiVJbq0
k0wn1NZELhVb9wOOtQoMsEsS68Sopt2fKeVfCbVy09F6gWv/cb4rWBhEhswLprxc
pcHR6ABq/B9vGy45Fos7hro910rOkuEgtY28RfvGlsSjdkYDy9aXmgiKd6lvhAEX
ZAVTUe17GsssMvHSNukjs6f4OFUsra2YoTghFQhm8Y3iBUSQYLcsqPAAoD3q4WI3
+SQlOwKBgQDoaFPs/llg3308uCf/iHco+Cp3nUheh3J/AUtPQKWE6kABR7V7SUfE
f7zJaqhXPxkeYERhb5Cs2rQF4+ov2ynnTWbb4wQc3u7+nd5hZC6S+0hbA/E+J+1N
XBO0cMl5wB9+lwwP4fxg8aaAUb7zXZjUuNvgv5VI6K7xf77zlSrHFQKBgQDmgqCv
pJySre/UkBm3wJOW5map8OuqOW6XC7HiaSiWt8/f/KIIQBU1UTbPL7bYilrSObrO
Wd6/8CqGSzH3vaFDX2hAf9TqN5xXxgis/1R18eQ8m/ufoYcqMmJ8DYWYyhHKiQsF
yqGfaG2pMaZ01HL4Z4XBc3SebNb5C1Yn1JhR2wKBgE+uvbBXNJY9/w2A98VTXA4L
8U2VAt6U1sx1eOf46ErT+K/7VCpFAqnFZUxfFSU6NH9xdofOer7r5fNkFcdD09pG
bGilmwKVk8UC7Si0okEFsmhZH4MGX/1EJANZ3q1mcTK3cdmPTzcuR7b3dKs2CINy
lIeSJI4GomFtoI4HQd3bAoGAE8INxemJw23+nTDsQvM/2bv6K9LSXww8rdxuVTw2
rdU9tBz38hQfEzLYz+4lnYgzq56MWtSAzp+OncvuVxBBBcZZZJ0+IrRPOrkz8ZI6
cIJyphv/n/fnA53rLzRb6IZZS9/cX5UGigGi/9+vLpXd+AjflD0Yn91xgw+Zq6SC
FaUCgYBU1g2ELThjbyh+aOEfkRktud1NVZgcxX02nPW8php0B1+cb7o5gq5I8Kd8
0aWoUVpKvv9gz1M0jfgQaL2nXGY1bNiq7uocGbVDDzd8c5Gj7gHKWdObDbgoEcL/
0cxpDojs3XizG/0FEd4J9UscdrrqHWFZ1grLNYd3orcxhDcpcg==
-----END RSA PRIVATE KEY-----")

;; The known-hosts, i.e. source.nokia.com
(def known-hosts
  "|1|UoVqPabY168wScQJfyEUyDX35Xk=|DTUa0H6lR05jNuvHIMl4ReJLqXM= ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEAuK96oIAr4mPDxbiJqlSi7KFM9GY1jnzb+LhZlJyvJRqK925hgEdTS/QG4uoH4VI0NqMWiCLn8LiPLyj2+WLnYBWpaPIsp728ighAahYY1TsZiUiP4EqpRd093Ur+EE+de7cjfuNy5iJfkU092SqLUJwQCMA05N9vvkSc0lR/hOR77bs/YLucaGyZfXGfHFbosd4+sm82hcqLJKIdQ0+ChEp3ROyZnzferlKqJbFFjJdN4TTq3ITPNjmQ1Hqmmb0kjBJ6M8W11SgqANjdzfnkXHhV46rYrjXesxoPxw3jS1BPEjbLljrY1NMBMhFOLI6tlvFTJc5Jk7c7ytmtG5+sCQ==
|1|xtbIYF+FIx2dSIOML++8N0Ohwuw=|f11MX7uxFmdYTaPNxh961FunJI0= ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEAuK96oIAr4mPDxbiJqlSi7KFM9GY1jnzb+LhZlJyvJRqK925hgEdTS/QG4uoH4VI0NqMWiCLn8LiPLyj2+WLnYBWpaPIsp728ighAahYY1TsZiUiP4EqpRd093Ur+EE+de7cjfuNy5iJfkU092SqLUJwQCMA05N9vvkSc0lR/hOR77bs/YLucaGyZfXGfHFbosd4+sm82hcqLJKIdQ0+ChEp3ROyZnzferlKqJbFFjJdN4TTq3ITPNjmQ1Hqmmb0kjBJ6M8W11SgqANjdzfnkXHhV46rYrjXesxoPxw3jS1BPEjbLljrY1NMBMhFOLI6tlvFTJc5Jk7c7ytmtG5+sCQ==
")

(def base-git-url (env :service-base-git-repository-url))
(def base-git-path (env :service-base-git-repository-path))

(def my-jcs-factory
  (proxy [JschConfigSessionFactory] []
    (configure [host session]
      (info "Configuring JschConfigSessionFactory.")
      (.setConfig session "StrictHostChecking" "yes"))
    (createDefaultJSch [fs]
      (let [jsch (JSch.)]
        (info "Creating default JSch using tyranitar private key and known-hosts.")
        (.addIdentity jsch "tyranitar" (.getBytes tyranitar-private-key) nil nil)
        (.setKnownHosts jsch (ByteArrayInputStream. (.getBytes known-hosts)))
        jsch))))

(SshSessionFactory/setInstance my-jcs-factory)

(conch/programs rm)

(defn- repo-name
  [application env]
  (str application "-" env))

(defn- repo-url
  [repo-name]
  (str base-git-url repo-name))

(defn repo-path
  [name]
  (str base-git-path name))

(defn clone-repo
  "Clones the latest version of the specified repo from GIT."
  [repo-name]
  (info "First ensuring that repository directory does not exist")
  (rm "-rf" (repo-path repo-name))
  (info "Cloning repository to" (repo-path repo-name))
  (->
   (Git/cloneRepository)
   (.setURI (repo-url repo-name))
   (.setDirectory (as-file (repo-path repo-name)))
   (.setRemote "origin")
   (.setBranch "master")
   (.setBare false)
   (.call))
  (info "Cloning completed."))

(defn- pull-repo
  "Pull a repository by fetching then merging. Assumes no merge conflicts which should be OK as the repository will only ever be touched via this route."
  [repo-name]
  (let [git (Git/open (as-file (repo-path repo-name)))]
    (info "Fetching repository to" (repo-path repo-name))
    (->
     (.fetch git)
     (.call))
    (info "Fetch completed.")
    (let [repo (.getRepository git)
          origin-master (.resolve repo "origin/master")]
      (info "Merging origin/master.")
      (->
       (.merge git)
       (.include origin-master)
       (.call))
      (info "Merge completed."))))

(defn get-exact-commit
  "Get the hash and the data contained in the file for the category file in the chosen repository at the specified
   commit level from GIT. Will accept the same commit identifiers as GIT."
  [repo-name category commit]
  (info "Attempting to get exact commit" commit "in" repo-name "for category" category)
  (let [git (Git/open (as-file (repo-path repo-name)))
        repo (.getRepository git)
        commit-id (.resolve repo commit)
        rwalk (RevWalk. repo)
        commit (.parseCommit rwalk commit-id)
        tree (.getTree commit)
        twalk (TreeWalk/forPath repo (str category ".json") tree)
        loader (.open repo (.getObjectId twalk 0))
        text-result (slurp (.openStream loader))]
    (info "Commit with hash" (.getName commit-id) "obtained")
    {:hash (.getName commit-id)
     :data (parse-string text-result)}))

(defn- commit-to-map
  "Turns a commit object into a useful map."
  [commit]
  {:hash (.getName commit)
   :committer (.getName (.getCommitterIdent commit))
   :email (.getEmailAddress (.getCommitterIdent commit))
   :message (.getShortMessage commit)
   :date (unparse (:date-time-no-ms formatters) (from-long (* (.getCommitTime commit) 1000)))})

(defn- get-recent-commits-list
  "Get the 20 most recent commits to the repository."
  [repo-name]
  (info "Attempting to get recent commits for repository" repo-name)
  (let [git (Git/open (as-file (repo-path repo-name)))
        commits (->
                 (.log git)
                 (.setMaxCount 20)
                 (.call))]
    {:commits (reduce (fn [v i] (conj v (commit-to-map i))) [] commits)}))

(defn- repo-exists?
  [repo-name]
  (.exists (as-file (repo-path repo-name))))

(defn- ensure-repo-up-to-date
  "Gets or updates the specified repo from GIT"
  [repo-name]
  (if (repo-exists? repo-name)
    (pull-repo repo-name)
    (do
      (info (str "Repo '" repo-name "' not found - attempting to clone"))
      (clone-repo repo-name))))

(defn get-data
   "Fetches the data corresponding to the given params from GIT"
  [env app commit category]
  (let [repo-name (repo-name app env)]
    (try
      (ensure-repo-up-to-date repo-name)
      (get-exact-commit repo-name category (upper-case commit))
      (catch InvalidRemoteException e
        (info (str "Can't communicate with remote repo '" repo-name "': " e))
        nil)
      (catch NullPointerException e
        (info (str "Revision '" commit "' not found in repo '" repo-name "': " e))
        nil)
      (catch MissingObjectException e
        (info (str "Missing object for revision '" commit "' in repo '" repo-name "': " e))
        nil))))

(defn get-list
  "Get a list of the 20 most recent commits to the repository in most recent first order."
  [env app]
  (let [repo-name (repo-name app env)]
    (try
      (ensure-repo-up-to-date repo-name)
      (get-recent-commits-list repo-name)
      (catch InvalidRemoteException e
        (info (str "Can't communicate with remote repo '" repo-name "': " e))
        nil))))

(defn git-connection-working
  "Returns true if the remote repository is available and behaving as expected"
  []
  (try
    (get-list "dev" "skeleton")
    true
    (catch Exception e
      false)))

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
  (let [response (client/get snc-url {:as :json :throw-exceptions false})
        body (:body response)]
    body))

(defn- process-repository-list
  "Process the list of data returned from SNC into the form that we want."
  [list]
  (let [data (map data-from-repo-item list)
        grouped-app-list (group-by :app data)
        result (into {} (map (fn [[k v]] {(keyword k) {:repositories (mapv #(dissoc % :app :env) v)}}) grouped-app-list))]
    result))

(defn get-repository-list
  "Returns a list of all repositories that exist in the tyranitar git SNC project."
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
  (let [response (client/post snc-url {:body (repo-create-body name env)
                                       :content-type "application/x-www-form-urlencoded"
                                       :throw-exceptions false})
        status (:status response)]
    (when (not= status 200)
      (throw+ {:status status :message (:message (parse-string (:body response) true))}))))

(defn write-templated-properties
  "Substitutes the application name for placeholders in the given template and writes the file."
  [app-name template env]
  (let [repo-path (repo-path (repo-name app-name env))
        data {:app-name app-name :env-name env}
        dest-path (str repo-path "/" template)]
    (->>
     (templates/render-resource template data)
     (spit dest-path))))

(defn- write-default-properties
  "Writes a default set of service properties to the GIT repo file location."
  [app-name env]
  (let [repo-path (repo-path (repo-name app-name env))]
    (write-templated-properties app-name "application-properties.json" env)
    (write-templated-properties app-name "deployment-params.json" env)
    (write-templated-properties app-name "launch-data.json" env)))

(defn- commit-and-push
  [repo-name]
  (let [git (Git/open (as-file (repo-path repo-name)))
        add (.add git)
        commit (.commit git)
        push (.push git)]
    (->
     add
     (.addFilepattern ".")
     (.call))
    (->
     commit
     (.setAuthor "tyranitar" "noreply@nokia.com")
     (.setMessage "Initial properties files.")
     (.call))
    (->
     push
     (.call))
    ))

(defn- create-application-env
  [name env]
  (let [repo-name (repo-name name env)]
    (create-repository name env)
    (clone-repo repo-name)
    (write-default-properties name env)
    (commit-and-push repo-name)
    {:name repo-name :path (repo-url repo-name)}))

(defn create-application
  [name]
  {:repositories [(create-application-env name "dev")
                  (create-application-env name "prod")]})
