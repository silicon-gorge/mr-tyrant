(ns tyranitar.web-test
  (:require [cheshire.core :as json]
            [midje.sweet :refer :all]
            [ring.util.io :refer [string-input-stream]]
            [slingshot.support :as s]
            [tyranitar
             [store :as store]
             [web :refer :all]])
  (:import [org.eclipse.jgit.api.errors GitAPIException InvalidRemoteException]))

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

(fact "that we can set the version"
      (set-version! "0.1") => anything
      *version* => "0.1"
      (set-version! "something") => anything
      *version* => "something")

(fact "that we can retrieve the applications list"
      (request :get "/1.x/applications") => (contains {:body {:applications [{:something "hello"}]}})
      (provided
       (store/get-repository-list) => [{:something "hello"}]))

(fact "that creating an application passes the name down to store"
      (request :post "/1.x/applications" (json-body {:name "application"})) => (contains {:body {:repositories [{:repo 1}
                                                                                                                {:repo 2}]}
                                                                                          :status 201})
      (provided
       (store/create-application "application") => {:repositories [{:repo 1}
                                                                   {:repo 2}]}))

(fact "that a 422 error while creating an application is a 409"
      (request :post "/1.x/applications" (json-body {:name "application"})) => (contains {:status 409})
      (provided
       (store/create-application "application") =throws=> (slingshot-exception {:status 422})))

(fact "that ping returns a pong"
      (request :get "/1.x/ping") => (contains {:body "pong"}))

(fact "that status returns ok"
      (request :get "/1.x/status") => (contains {:status 200})
      (provided
       (store/git-connection-working?) => true))

(fact "that an unknown environment name results in not found response"
      (request :get "/1.x/applications/wrongenv") => (contains {:status 404}))

(fact "that the environment name 'poke' is accepted"
      (request :get "/1.x/applications/poke") => (contains {:status 200})
      (provided
       (store/get-repository-list "poke") => {}))

(fact "that getting commits for application and dev environment works"
      (request :get "/1.x/applications/dev/test") => (contains {:status 200})
      (provided
       (store/get-commits "dev" "test") => []))

(fact "that getting commits for application and prod environment works"
      (request :get "/1.x/applications/prod/test") => (contains {:status 200})
      (provided
       (store/get-commits "prod" "test") => []))

(fact "that getting commits for application that doesn't exist results in not found response"
      (request :get "/1.x/applications/prod/test") => (contains {:status 404})
      (provided
       (store/get-commits "prod" "test") => nil))

(fact "that getting the head -n commit for application works"
      (request :get "/1.x/applications/prod/test/head~2/application-properties") => (contains {:status 200})
      (provided
       (store/get-data "prod" "test" "head~2" "application-properties") => []))

(fact "that getting the head commit for application works"
      (request :get "/1.x/applications/prod/test/head/application-properties") => (contains {:status 200})
      (provided
       (store/get-data "prod" "test" "head" "application-properties") => []))

(fact "that getting a specific commit for application works"
      (request :get "/1.x/applications/prod/test/921f195c98570550e743911bc3f5aca260d73f6f/application-properties") => (contains {:status 200})
      (provided
       (store/get-data "prod" "test" "921f195c98570550e743911bc3f5aca260d73f6f" "application-properties") => []))

(fact "that getting a specific commit which doesn't exist results in a not found response"
      (request :get "/1.x/applications/prod/application/HEAD/application-properties") => (contains {:status 404})
      (provided
       (store/get-data "prod" "application" "HEAD" "application-properties") => nil))

(fact "that getting an invalid commit value results in not found response"
      (request :get "/1.x/applications/prod/test/badcommit/application-properties") => (contains {:status 404}))

(fact "that getting deployment-params works"
      (request :get "/1.x/applications/prod/test/head/deployment-params") => (contains {:status 200})
      (provided
       (store/get-data "prod" "test" "head" "deployment-params") => []))

(fact "that getting launch-data works"
      (request :get "/1.x/applications/prod/test/head/launch-data") => (contains {:status 200})
      (provided
       (store/get-data "prod" "test" "head" "launch-data") => []))

(fact "that updating properties is called with the correct params"
      (request :post "/1.x/applications/dev/testapp/application-properties" (json-body {:a "one" :b "two"})) => (contains {:status 200})
      (provided
       (store/update-properties "testapp" "dev" "application-properties" {:a "one" :b "two"}) => {:dummy "value"}))

(fact "that a Git failure returns correct error response"
      (request :post "/1.x/applications/dev/testapp/application-properties" (json-body {:a "one" :b "two"})) => (contains {:status 409})
      (provided
       (store/update-properties "testapp" "dev" "application-properties" {:a "one" :b "two"}) =throws=> (InvalidRemoteException. "GIT update failed!")))

(fact "that our pokémon resource works"
      (request :get "/1.x/pokemon") => (contains {:status 200}))

(fact "that our icon resource works"
      (request :get "/1.x/icon") => (contains {:status 200}))

(fact "that our healthcheck gives a 200 response if it's healthy"
      (request :get "/healthcheck") => (contains {:status 200})
      (provided
       (store/git-connection-working?) => true))

(fact "that our healthcheck gives a 500 response if it's not healthy"
      (request :get "/healthcheck") => (contains {:status 500})
      (provided
       (store/git-connection-working?) => false))
