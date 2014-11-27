(ns tyrant.environments-test
  (:require [midje.sweet :refer :all]
            [tyrant
             [environments :refer :all]
             [lister :as lister]]))

(fact "that loading our environments does what we expect"
      (load-environments)
      => {:env1 {:name "env1"
                 :metadata {:first "metadata"}}
          :env2 {:name "env2"
                 :metadata {:second "metadata"}}}
      (provided
       (lister/environments) => ["env1" "env2"]
       (lister/environment "env1") => {:name "env1" :metadata {:first "metadata"}}
       (lister/environment "env2") => {:name "env2" :metadata {:second "metadata"}}))

(fact "that getting default environments works"
      (default-environments) => {:env1 {:name "env1" :metadata {:account-id "onething"
                                                                :create-repo true}}}
      (provided
       (environments) => {:env1 {:name "env1"
                                 :metadata {:account-id "onething"
                                            :create-repo true}}
                          :env2 {:name "env2"
                                 :metadata {:account-id "another"}}}))
