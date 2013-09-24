(ns tyranitar.unit.web
  (:require [cheshire.core :as json]
            [tyranitar.git :as git])
  (:use [tyranitar.web]
        [midje.sweet]))

(defn request
  [method resource & {:as others}]
  (routes (merge {:request-method method
                  :uri resource} (update-in others [:body]
                                         #(java.io.ByteArrayInputStream.
                                           (.getBytes (json/generate-string %)))))))

(defn request [method resource]
  (routes {:request-method method
           :uri resource } ))

(fact-group :unit
  (fact "Ping returns a pong"
        (:body (request :get "/1.x/ping"))  => "pong" )

  (fact "Status returns ok"
        (request :get "/1.x/status") => (contains {:status 200}))

  (fact "Healthcheck returns ok"
        (request :get "/healthcheck") => (contains {:status 200}))

  (fact "Invalid environment name results in not found response"
        (request :get "/1.x/applications/wrongenv/test") => (contains {:status 404}))

  (fact "Get commits for application and dev environment works"
        (request :get "/1.x/applications/dev/test") => (contains {:status 200})
        (provided
         (git/get-list "dev" "test") => []))

  (fact "Get commits for application and prod environment works"
        (request :get "/1.x/applications/prod/test") => (contains {:status 200})
        (provided
         (git/get-list "prod" "test") => []))

  (fact "Get commits for application that doesn't exist results in not found response"
        (request :get "/1.x/applications/prod/test") => (contains {:status 404})
        (provided
         (git/get-list "prod" "test") => nil))

  (fact "Get head -n commit for application works"
        (request :get "/1.x/applications/prod/test/head~2/application-properties") => (contains {:status 200})
        (provided
         (git/get-data "prod" "test" "head~2" "application-properties") => []))

  (fact "Get head commit for application works"
        (request :get "/1.x/applications/prod/test/head/application-properties") => (contains {:status 200})
        (provided
         (git/get-data "prod" "test" "head" "application-properties") => []))

  (fact "Get specific commit for application works"
        (request :get "/1.x/applications/prod/test/921f195c98570550e743911bc3f5aca260d73f6f/application-properties") => (contains {:status 200})
        (provided
         (git/get-data "prod" "test" "921f195c98570550e743911bc3f5aca260d73f6f" "application-properties") => []))

  (fact "Get for an invalid commit value results in not found response"
        (request :get "/1.x/applications/prod/test/badcommit/application-properties") => (contains {:status 404}))

  (fact "Getting deployment-params works"
        (request :get "/1.x/applications/prod/test/head/deployment-params") => (contains {:status 200})
        (provided
         (git/get-data "prod" "test" "head" "deployment-params") => []))

    (fact "Getting launch-data works"
        (request :get "/1.x/applications/prod/test/head/launch-data") => (contains {:status 200})
        (provided
         (git/get-data "prod" "test" "head" "launch-data") => [])))
