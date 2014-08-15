(ns tyranitar.store-test
  (:require [midje.sweet :refer :all]
            [tyranitar
             [environments :as environments]
             [store :refer :all]]))

(fact "that when creating applications we go through each of them"
      (create-application "application") => {:repositories ["repoenv2" "repoenv1"]}
      (provided
       (environments/default-environments) => {:env1 {:name "env1"}
                                               :env2 {:name "env2"}}
       (create-application-env "application" "env1" false) => "repoenv1"
       (create-application-env "application" "env2" false) => "repoenv2"))
