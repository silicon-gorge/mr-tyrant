(ns tyrant.web-test
  (:require [cheshire.core :as json]
            [midje.sweet :refer :all]
            [ring.util.io :refer [string-input-stream]]
            [slingshot.support :as s]
            [tyrant
             [environments :as environments]
             [store :as store]
             [web :refer :all]]))

(defn- json-body
  [raw-body]
  {:body (string-input-stream (json/encode raw-body))
   :headers {"content-type" "application/json"}})

(defn- streamed-body?
  [{:keys [body]}]
  (instance? java.io.InputStream body))

(defn- json-response?
  [{:keys [headers]}]
  (when-let [content-type (get headers "Content-Type")]
    (re-find #"^application/(vnd.+)?json" content-type)))

(defn request
  "Creates a compojure request map and applies it to our application.
   Accepts method, resource and optionally an extended map"
  [method resource & [{:keys [body headers params]
                       :or {:body nil
                            :headers {}
                            :params {}}}]]
  (let [{:keys [body headers] :as response} (app {:body body
                                                  :headers headers
                                                  :params params
                                                  :request-method method
                                                  :uri resource})]
    (cond-> response
            (streamed-body? response) (update-in [:body] slurp)
            (json-response? response) (update-in [:body] (fn [b] (json/parse-string b true))))))

(defn slingshot-exception
  [exception-map]
  (s/get-throwable (s/make-context exception-map (str "throw+: " map) (s/stack-trace) {})))

(fact "that we can retrieve the applications list"
      (request :get "/applications") => (contains {:body {:applications [{:something "hello"}]}})
      (provided
       (store/get-repository-list) => [{:something "hello"}]))

(fact "that creating an application passes the name down to store"
      (request :post "/applications" (json-body {:name "application"})) => (contains {:body {:repositories [{:repo 1}
                                                                                                                {:repo 2}]}
                                                                                          :status 201})
      (provided
       (store/create-application "application") => {:repositories [{:repo 1}
                                                                   {:repo 2}]}))

(fact "that a 422 error while creating an application is a 409"
      (request :post "/applications" (json-body {:name "application"})) => (contains {:status 409})
      (provided
       (store/create-application "application") =throws=> (slingshot-exception {:status 422})))

(fact "that ping returns a pong"
      (request :get "/ping") => (contains {:body "pong"}))

(fact "that an unknown environment name results in not found response"
      (request :get "/applications/unknown") => (contains {:status 404}))

(fact "that a known environment is accepted"
      (request :get "/applications/poke") => (contains {:status 200})
      (provided
       (environments/environments) => {:poke {}}
       (store/get-repository-list "poke") => {}))

(fact "that getting commits for an application in a known environment works"
      (request :get "/applications/dev/test") => (contains {:status 200})
      (provided
       (environments/environments) => {:dev {}}
       (store/get-commits "dev" "test") => []))

(fact "that getting commits for an application in an unknown environment is a 404"
      (request :get "/applications/dev/test") => (contains {:status 404})
      (provided
       (environments/environments) => {}))

(fact "that creating a repo for an unknown environment is a 404"
      (request :post "/applications/dev/test") => (contains {:status 404})
      (provided
       (environments/environments) => {}))

(fact "that creating a repo for a known environment works"
      (request :post "/applications/dev/test") => (contains {:status 201})
      (provided
       (environments/environments) => {:dev {}}
       (store/create-application-env "test" "dev" true) => {}))

(fact "that a 422 error while creating an application in a specific environment is a 409"
      (request :post "/applications/dev/test") => (contains {:status 409})
      (provided
       (environments/environments) => {:dev {}}
       (store/create-application-env "test" "dev" true) =throws=> (slingshot-exception {:status 422})))

(fact "that getting commits for application that doesn't exist results in not found response"
      (request :get "/applications/prod/test") => (contains {:status 404})
      (provided
       (environments/environments) => {:prod {}}
       (store/get-commits "prod" "test") => nil))

(fact "that getting a commit for an application in an unknown environment is a 404"
      (request :get "/applications/prod/test/head/application-properties") => (contains {:status 404})
      (provided
       (environments/environments) => {}))

(fact "that getting the head -n commit for application works"
      (request :get "/applications/prod/test/head~2/application-properties") => (contains {:status 200})
      (provided
       (environments/environments) => {:prod {}}
       (store/get-data "prod" "test" "head~2" "application-properties") => []))

(fact "that getting the head commit for application works"
      (request :get "/applications/prod/test/head/application-properties") => (contains {:status 200})
      (provided
       (environments/environments) => {:prod {}}
       (store/get-data "prod" "test" "head" "application-properties") => []))

(fact "that getting a specific commit for application works"
      (request :get "/applications/prod/test/921f195c98570550e743911bc3f5aca260d73f6f/application-properties") => (contains {:status 200})
      (provided
       (environments/environments) => {:prod {}}
       (store/get-data "prod" "test" "921f195c98570550e743911bc3f5aca260d73f6f" "application-properties") => []))

(fact "that getting a specific commit which doesn't exist results in a not found response"
      (request :get "/applications/prod/application/HEAD/application-properties") => (contains {:status 404})
      (provided
       (environments/environments) => {:prod {}}
       (store/get-data "prod" "application" "HEAD" "application-properties") => nil))

(fact "that getting an invalid commit value results in not found response"
      (request :get "/applications/prod/test/badcommit/application-properties") => (contains {:status 404}))

(fact "that getting deployment-params works"
      (request :get "/applications/prod/test/head/deployment-params") => (contains {:status 200})
      (provided
       (environments/environments) => {:prod {}}
       (store/get-data "prod" "test" "head" "deployment-params") => []))

(fact "that getting launch-data works"
      (request :get "/applications/prod/test/head/launch-data") => (contains {:status 200})
      (provided
       (environments/environments) => {:prod {}}
       (store/get-data "prod" "test" "head" "launch-data") => []))

(fact "that our healthcheck gives a 200 response if everything is healthy"
      (request :get "/healthcheck") => (contains {:status 200})
      (provided
       (environments/environments) => {}
       (store/github-healthy?) => true
       (store/repos-healthy?) => true))

(fact "that our healthcheck gives a 500 response if Git isn't healthy"
      (request :get "/healthcheck") => (contains {:status 500})
      (provided
       (environments/environments) => {}
       (store/github-healthy?) => false
       (store/repos-healthy?) => true))

(fact "that our healthcheck gives a 500 response if our environments haven't loaded"
      (request :get "/healthcheck") => (contains {:status 500})
      (provided
       (environments/environments) => nil
       (store/github-healthy?) => true
       (store/repos-healthy?) => true))

(fact "that our healthcheck gives a 500 response if our repos haven't loaded"
      (request :get "/healthcheck") => (contains {:status 500})
      (provided
       (environments/environments) => {}
       (store/github-healthy?) => true
       (store/repos-healthy?) => false))
