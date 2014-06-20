(ns tyranitar.web
  (:require [cheshire.core :as json]
            [clojure.string :refer [split]]
            [clojure.tools.logging :refer [info warn error]]
            [compojure
             [core :refer [defroutes context GET PUT POST DELETE]]
             [handler :as handler]
             [route :as route]]
            [environ.core :refer [env]]
            [metrics.ring
             [expose :refer [expose-metrics-as-json]]
             [instrument :refer [instrument]]]
            [nokia.ring-utils
             [error :refer [wrap-error-handling error-response]]
             [ignore-trailing-slash :refer [wrap-ignore-trailing-slash]]]
            [ring.middleware
             [format-params :refer [wrap-json-kw-params]]
             [format-response :refer [wrap-json-response]]
             [params :refer [wrap-params]]]
            [slingshot.slingshot :refer [try+]]
            [tyranitar
             [environments :as environments]
             [git :as git]
             [pokemon :as pokemon]
             [store :as store]])
  (:import [org.eclipse.jgit.api.errors GitAPIException]))

(def json-content-type "application/json;charset=utf-8")

(def plain-text "text/plain;charset=utf-8")

(def category-regex #"application-properties|deployment-params|launch-data")

(def commit-regex #"HEAD~\d+|HEAD|head~\d+|head|[0-9a-fA-F]{40}")

(def env-regex #"dev|poke|prod")

(def ^:dynamic *version* "none")
(defn set-version! [version]
  (alter-var-root #'*version* (fn [_] version)))

(defn response
  [data & [content-type status]]
  {:status (or status 200)
   :headers {"Content-Type" (or content-type json-content-type)}
   :body data})

(defn unknown-environment-response
  []
  (response (str "Unknown environment") plain-text 404))

(defn- healthcheck
  []
  (let [git-ok? (store/git-healthy?)
        environments-ok? (some? (environments/environments))]
    {:name "tyranitar"
     :version *version*
     :success (and git-ok? environments-ok?)
     :dependencies [{:name "snc" :success git-ok?}
                    {:name "environments" :success environments-ok?}]}))

(defn- get-data
  [env app commit category]
  (if-let [result (store/get-data env app commit category)]
    (response result)
    (error-response (str "No data of type '" category "' for application '" app "' at revision '" commit "' - does it exist?") 404)))

(defn- get-list
  [env app]
  (if-let [result (store/get-commits env app)]
    (response result)
    (error-response (str "Application '" app "' does not exist.") 404)))

(defn- create-application
  [name]
  (try+
   (response (store/create-application name) json-content-type 201)
   (catch [:status 422] e
     (error-response (str "Could not create application '" name "', message: " (:message e)) 409))))

(defn- create-application-env
  [name env]
  (try+
   (response (store/create-application-env name env) json-content-type 201)
   (catch [:status 422] e
     (error-response (str "Could not create environment repo '" env "' for application '" name "', message: " (:message e)) 409))))

(defn read-json-body
  "Reads HTTP JSON input into a nice map."
  [body]
  (json/parse-string (slurp body) true))

(defn update-properties
  [app-name env category properties]
  (try
    (response (store/update-properties app-name env category (read-json-body properties)))
    (catch GitAPIException e
      (error-response (str "Unable to store update in Git, message: " (:message e)) 409))))

(defn known-environment?
  [environment]
  (contains? (environments/environments) (keyword environment)))

(defroutes applications-routes
  (GET "/"
       []
       (response {:applications (store/get-repository-list)}))

  (POST "/"
        [name]
        (create-application name))

  (GET ["/:env"]
       [env]
       (if (known-environment? env)
         (response {:applications (store/get-repository-list env)})
         (unknown-environment-response)))

  (GET ["/:env/:app"]
       [env app]
       (if (known-environment? env)
         (get-list env app)
         (unknown-environment-response)))

  (POST "/:env/:app"
        [env app]
        (if (known-environment? env)
          (create-application-env app env)
          (unknown-environment-response)))

  (GET ["/:env/:app/:commit/:category" :commit commit-regex :category category-regex]
       [env app commit category]
       (if (known-environment? env)
         (get-data env app commit category)
         (unknown-environment-response)))

  (POST ["/:env/:app/:category" :category category-regex]
        [env app category :as req]
        (if (known-environment? env)
          (update-properties app env category (:body req))
          (unknown-environment-response))))

(defroutes routes
  (context
   "/1.x" []

   (GET "/ping"
        []
        "pong")

   (GET "/status"
        []
        (response (healthcheck) json-content-type 200))

   (GET "/pokemon"
        []
        (response pokemon/pokemon plain-text))

   (GET "/icon"
        []
        {:status 200
         :headers {"Content-Type" "image/jpeg"}
         :body (-> (clojure.java.io/resource "tyranitar.jpg")
                   (clojure.java.io/input-stream))})

   (context "/applications"
            []
            applications-routes))

  (GET "/healthcheck"
       []
       (let [healthcheck-result (healthcheck)
             status (if (:success healthcheck-result) 200 500)]
         (response healthcheck-result json-content-type status)))

  (route/not-found (error-response "Resource not found" 404)))

(def app
  (-> routes
      (instrument)
      (wrap-error-handling)
      (wrap-ignore-trailing-slash)
      (wrap-json-response)
      (wrap-json-kw-params)
      (wrap-params)
      (expose-metrics-as-json)))
