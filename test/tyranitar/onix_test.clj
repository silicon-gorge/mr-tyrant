(ns tyranitar.onix-test
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [midje.sweet :refer :all]
            [tyranitar.onix :refer :all]))

(fact "that failing to get the environment list gives back nil"
      (environments) => nil
      (provided
       (http/get "http://onix:8080/1.x/environments" anything) => {:status 404}))

(fact "that our environment list comes back properly"
      (environments) => ..environments..
      (provided
       (http/get "http://onix:8080/1.x/environments" anything) => {:body ..body..
                                                                   :status 200}
       (json/parse-string ..body.. true) => {:environments ..environments..}))

(fact "that failing to get an environment gives back nil"
      (environment "env") => nil
      (provided
       (http/get "http://onix:8080/1.x/environments/env" anything) => {:status 404}))

(fact "that we can retrieve an environment"
      (environment "env") => ..environment..
      (provided
       (http/get "http://onix:8080/1.x/environments/env" anything) => {:body ..body..
                                                                       :status 200}
       (json/parse-string ..body.. true) => ..environment..))
