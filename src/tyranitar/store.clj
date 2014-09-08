(ns tyranitar.store
  (:require [cheshire.core :as json]
            [clj-time.core :as time]
            [clojure.java.io :refer [as-file]]
            [clojure.string :as str]
            [clojure.tools.logging :refer [debug error warn]]
            [clostache.parser :as templates]
            [environ.core :refer [env]]
            [io.clj.logging :refer [with-logging-context]]
            [overtone.at-at :as at]
            [slingshot.slingshot :refer [throw+]]
            [tentacles
             [data :as data]
             [repos :as repos]]
            [tyranitar.environments :as environments]))

(def ^:private pool
  (atom nil))

(def ^:private github-health-atom
  (atom false))

(def ^:private repos-atom
  (atom nil))

(def ^:private organisation
  (env :github-organisation))

(defn- busted?
  [response]
  (if-let [status (:status response)]
    (or (< status 200) (>= status 400))
    false))

(defn- poke-properties
  "Default properties for poke environment"
  [application environment]
  {:app-name application
   :env-name environment
   :error-logging-url "http://errorlogging.music.cq3.brislabs.com:8080/ErrorLogging/1.x"
   :instance-type "m1.small"
   :graphite-host "carbon.brislabs.com"
   :is-prod false
   :ssh-security-group "Brislabs-SSH"
   :web-security-group "Brislabs-8080"})

(defn- prod-properties
  "Default properties for prod environment"
  [application environment]
  {:app-name application
   :env-name environment
   :error-logging-url "http://errorlogging.ent.nokia.com:8080/ErrorLogging/1.x"
   :instance-type "m1.small"
   :graphite-host "carbon.ent.nokia.com"
   :is-prod true
   :ssh-security-group "AppGate"
   :web-security-group "internal-8080"
   :scanner-security-group "ICM Scanning"
   :scanner-security-group-present true})

(defn- repo-name
  [application environment]
  (str application "-" environment))

(defn- extract-commit-info
  [commit]
  {:committer (get-in commit [:commit :committer :name])
   :date (get-in commit [:commit :committer :date])
   :email (get-in commit [:commit :committer :email])
   :hash (:sha commit)
   :message (get-in commit [:commit :message])})

(defn get-commits
  "Get a list of the 20 most recent commits to the repository in most recent first order."
  [environment application]
  (try
    (let [repo-name (repo-name application environment)
          commits (repos/commits organisation repo-name)]
      (map extract-commit-info (remove empty? commits)))
    (catch Exception e
      (with-logging-context {:application application
                             :environment environment}
        (error e "Failed to retrieve commits"))
      (throw+ {:status 500
               :message "Failed to retrieve commits"}))))

(defn- is-sha?
  [commit]
  (re-matches #"[0-9a-fA-F]{40}" commit))

(defn- generation-from
  [generation]
  (cond (= "" generation) 1
        (nil? generation) 0
        :else (Integer/valueOf generation)))

(defn- hash-from
  [url]
  (second (re-find #"\?ref=(.+)$" url)))

(defn- create-ref-map
  [environment application commit]
  (if (is-sha? commit)
    {:ref commit}
    (let [generation (generation-from (second (re-matches #"(?:HEAD|head)(?:~(?<generation>[0-9]*))?" commit)))
          commits (get-commits environment application)]
      {:ref (:hash (nth commits generation))})))

(defn get-data
  "Fetches the data corresponding to the given params from Git."
  [environment application commit category]
  (let [repo-name (repo-name application environment)
        ref-map (create-ref-map environment application commit)
        options (merge ref-map {:str? true})
        response (repos/contents organisation repo-name (str category ".json") options)]
    (if-let [data (:content response)]
      (let [hash (hash-from (:url response))]
        {:hash hash
         :data (json/parse-string data true)})
      nil)))

(defn- extract-repo-info
  "Pulls the necessary data out of the full Github repo."
  [repo]
  (let [parts (str/split (:name repo) #"-")
        application (first parts)
        environment (second parts)]
    (into (sorted-map) (merge (clojure.set/rename-keys (select-keys repo [:name :ssh_url]) {:ssh_url :path})
                              {:application application
                               :environment environment}))))

(defn all-repositories
  "Gets all repositories in the organisation in Github."
  []
  (let [response (repos/org-repos organisation {:all_pages true})]
    (when-not (busted? response)
      (doall (map extract-repo-info (remove empty? response))))))

(defn process-repositories
  [repositories]
  (let [grouped (group-by :application repositories)]
    (into (sorted-map) (map (fn [[k v]] {(keyword k) {:repositories (map #(dissoc % :application :environment) v)}}) grouped))))

(defn get-repository-list
  "Returns a list of all repositories that exist in the organisation in Github."
  ([] (process-repositories @repos-atom))
  ([environment] (process-repositories (filter #(= (name environment) (:environment %)) @repos-atom))))

(defn- create-repository
  [application environment]
  (try
    (let [repo-name (repo-name application environment)
          options {:auto_init true :has-downloads false :has-issues false :has-wiki false :public false}
          response (repos/create-org-repo organisation repo-name options)]
      (if (busted? response)
        (do
          (with-logging-context {:response response}
            (warn "Failed to create repository" repo-name))
          (throw+ {:status 500
                   :message (format "Failed to create repository %s" repo-name)}))
        response))
    (catch Exception e
      (with-logging-context {:application application
                             :environment environment}
        (error e "Failed to create repository"))
      (throw+ {:status 500
               :message "Failed to create repository"}))))

(defn- github-working?
  []
  (try
    (let [response (repos/specific-repo organisation "tyranitar-poke")]
      (not (busted? response)))
    (catch Exception e
      false)))

(defn- render-and-format
  [resource data]
  (-> (templates/render-resource resource data)
      json/parse-string
      (json/generate-string {:pretty true})))

(defn- properties-tree
  [application environment]
  (let [data (if (= "prod" environment) (prod-properties application environment) (poke-properties application environment))
        application-properties (render-and-format "application-properties.json" data)
        deployment-params (render-and-format "deployment-params.json" data)
        launch-data (render-and-format "launch-data.json" data)]
    [{:content application-properties
      :mode "100644"
      :path "application-properties.json"
      :type "blob"}
     {:content deployment-params
      :mode "100644"
      :path "deployment-params.json"
      :type "blob"}
     {:content launch-data
      :mode "100644"
      :path "launch-data.json"
      :type "blob"}]))

(defn- create-tree
  [application environment]
  (try
    (let [repo-name (repo-name application environment)
          tree (properties-tree application environment)
          options {}
          response (data/create-tree organisation repo-name tree options)]
      (if (busted? response)
        (do
          (with-logging-context {:response response}
            (warn "Failed to create tree" repo-name))
          (throw+ {:status 500
                   :message (format "Failed to create tree %s" repo-name)}))
        response))
    (catch Exception e
      (with-logging-context {:application application
                             :environment environment}
        (error e "Failed to create tree"))
      (throw+ {:status 500
               :message "Failed to create tree"}))))

(defn- create-commit
  [application environment tree]
  (try
    (let [repo-name (repo-name application environment)
          date (str (time/now))
          info {:date date :email "mixradiobot@gmail.com" :name "Mix Radio Bot"}
          options {:author info :committer info :parents []}
          response (data/create-commit organisation repo-name "Initial commit" tree options)]
      (if (busted? response)
        (do
          (with-logging-context {:response response}
            (warn "Failed to create commit" repo-name))
          (throw+ {:status 500
                   :message (format "Failed to create commit %s" repo-name)}))
        response))
    (catch Exception e
        (with-logging-context {:application application
                               :environment environment
                               :tree tree}
          (error e "Failed to create commit"))
        (throw+ {:status 500
                 :message "Failed to create commit"}))))

(defn- update-ref
  [application environment commit]
  (try
    (let [repo-name (repo-name application environment)
          options {:force true}
          response (data/edit-reference organisation repo-name "heads/master" commit options)]
      (if (busted? response)
        (do
          (with-logging-context {:response response}
            (warn "Failed to update reference" repo-name))
          (throw+ {:status 500
                   :message (format "Failed to update reference %s" repo-name)}))
        response))
    (catch Exception e
      (with-logging-context {:application application
                             :environment environment
                             :commit commit}
        (error e "Failed to update reference"))
      (throw+ {:status 500
               :message "Failed to update reference"}))))

(defn- update-repos
  []
  (reset! repos-atom (all-repositories)))

(defn- kick-off-repo-update
  []
  (.start (Thread. update-repos)))

(defn create-application-env
  [application environment update?]
  (let [repo (create-repository application environment)
        ssh-url (:ssh_url repo)]
    (when update?
      (kick-off-repo-update))
    (let [tree (create-tree application environment)
          tree-sha (:sha tree)
          commit (create-commit application environment tree-sha)
          commit-sha (:sha commit)]
      (update-ref application environment commit-sha)
      {:name (repo-name application environment) :path ssh-url})))

(defn create-application
  [application]
  (let [default-environments (environments/default-environments)
        repos (map (fn [[k _]] (create-application-env application (name k) false)) default-environments)]
    (kick-off-repo-update)
    {:repositories repos}))

(defn update-github-health
  []
  (reset! github-health-atom (github-working?)))

(defn github-healthy?
  []
  @github-health-atom)

(defn repos-healthy?
  []
  (some? @repos-atom))

(defn- setup-tentacles
  []
  (intern 'tentacles.core 'url (env :github-base-url) )
  (intern 'tentacles.core 'defaults {:oauth-token (env :github-auth-token)}))

(defn create-pool
  []
  (when-not @pool
    (reset! pool (at/mk-pool :cpu-count 2))))

(defn init
  []
  (setup-tentacles)
  (create-pool)
  (at/every (* 1000 12) update-github-health @pool :initial-delay 0)
  (at/every (* 1000 60 5) update-repos @pool :initial-delay 0))
