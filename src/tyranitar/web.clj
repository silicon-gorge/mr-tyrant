(ns tyranitar.web
  (:require [tyranitar.git :as git]
            [tyranitar.pokemon :as pokemon])
  (:require [compojure.core :refer [defroutes context GET PUT POST DELETE]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.middleware.format-response :refer [wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [clojure.data.xml :refer [element emit-str]]
            [clojure.string :refer [split]]
            [clojure.tools.logging :refer [info warn error]]
            [environ.core :refer [env]]
            [nokia.ring-utils.error :refer [wrap-error-handling error-response]]
            [nokia.ring-utils.metrics :refer [wrap-per-resource-metrics replace-outside-app
                                              replace-guid replace-mongoid replace-number]]
            [nokia.ring-utils.ignore-trailing-slash :refer [wrap-ignore-trailing-slash]]
            [metrics.ring.expose :refer [expose-metrics-as-json]]
            [metrics.ring.instrument :refer [instrument]]))

(def json-content-type "application/json;charset=utf-8")

(def category-regex #"service-properties|launch-data|deployment-params")

(def commit-regex #"HEAD~\d+|HEAD|head~\d+|head|[0-9a-fA-F]{40}")

(def env-regex #"dev|prod")

(def ^:dynamic *version* "none")
(defn set-version! [version]
  (alter-var-root #'*version* (fn [_] version)))

(defn response [data content-type & [status]]
  {:status (or status 200)
   :headers {"Content-Type" content-type}
   :body data})

(defn status
  []
  {:headers {"Content-Type" "application/xml"}
   :body    (emit-str (element :status
                               {:serviceName "tyranitar"
                                :version *version*
                                :success true}))})

(defn- get-data
  [env app commit category]
  (if-let [result (git/get-data env app commit category)]
    (response result json-content-type 200)
    (error-response (str "No data of type '" category "' for application '" app "'.") 404)))

(defn- get-list
  [env app]
  (if-let [result (git/get-list env app)]
    (response result json-content-type 200)
    (error-response (str "Application '" app "' does not exist.") 404)))

(defroutes applications-routes
  (GET ["/:env/:app" :env env-regex]
       [env app]
       (get-list env app))

  (GET ["/:env/:app/:commit/:category" :env env-regex :commit commit-regex :category category-regex]
       [env app commit category]
       (get-data env app commit category)))

(defroutes routes
  (context
   "/1.x" []

   (GET "/ping"
        [] "pong")

   (GET "/status"
        [] (status))

   (GET "/pokemon"
        [] (response pokemon/pokemon "text/plain;charset=utf-8"))

   (GET "/icon" []
        {:status 200
         :headers {"Content-Type" "image/jpeg"}
         :body (-> (clojure.java.io/resource "tyranitar.jpg")
                   (.getFile)
                   (java.io.FileInputStream.))})

   (context "/apps"
            [] applications-routes))
  
  (GET "/healthcheck" []
       (response "" json-content-type 200))

  (route/not-found (error-response "Resource not found" 404)))

(def app
  (-> routes
      (instrument)
      (wrap-error-handling)
      (wrap-ignore-trailing-slash)
      (wrap-keyword-params)
      (wrap-params)
      (wrap-json-response)
      (wrap-per-resource-metrics [replace-guid replace-mongoid replace-number (replace-outside-app "/1.x")])
      (expose-metrics-as-json)))
