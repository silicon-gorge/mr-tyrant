(ns tyrant.store-test
  (:require [cheshire.core :as json]
            [midje.sweet :refer :all]
            [tyrant
             [environments :as environments]
             [store :refer :all]]))

(fact "that we render and format correctly"
      (json/parse-string (#'tyrant.store/render-and-format {:something "{{value}}"} {:value "woo"}) true)
      => {:something "woo"})

(fact "that creating our properties tree is correct"
      (#'tyrant.store/properties-tree "application" "poke")
      => [{:content ..application-properties.. :mode "100644" :path "application-properties.json" :type "blob"}
          {:content ..deployment-params.. :mode "100644" :path "deployment-params.json" :type "blob"}
          {:content ..launch-data.. :mode "100644" :path "launch-data.json" :type "blob"}]
      (provided
       (environments/environments) => {:poke {:metadata {:default-application-properties {:something "{{app-name}}"}}}}
       (#'tyrant.store/render-and-format {:something "{{app-name}}"}
                                         {:app-name "application"
                                          :env-name "poke"})
       => ..application-properties..
       (#'tyrant.store/render-and-format {}
                                         {:app-name "application"
                                          :env-name "poke"})
       => ..deployment-params..
       (#'tyrant.store/render-and-format []
                                         {:app-name "application"
                                          :env-name "poke"})
       => ..launch-data..))

(fact "that when creating applications we go through each of them"
      (create-application "application") => {:repositories ["repoenv2" "repoenv1"]}
      (provided
       (environments/default-environments) => {:env1 {:name "env1"}
                                               :env2 {:name "env2"}}
       (create-application-env "application" "env1" false) => "repoenv1"
       (create-application-env "application" "env2" false) => "repoenv2"
       (all-repositories) => [] :times :any))
