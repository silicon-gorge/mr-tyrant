(ns tyranitar.environments-test
  (:require [midje.sweet :refer :all]
            [overtone.at-at :as at]
            [tyranitar
             [environments :refer :all]
             [onix :as onix]]))

(fact "that getting nil back when updating environments does not replace the value"
      (do (reset! environments-atom nil) (update-environments) (environments)) => nil
      (provided
       (onix/environments) => nil))

(fact "that an exception while getting environments does not replace the value"
      (do (reset! environments-atom nil) (update-environments) (environments)) => nil
      (provided
       (onix/environments) =throws=> (ex-info "Busted" {})))

(fact "that updating our environments does what we expect"
      (do (reset! environments-atom nil) (update-environments) (environments)) => {:env1 {:name "env1"
                                                                                          :metadata {:first "metadata"}}
                                                                                   :env2 {:name "env2"
                                                                                          :metadata {:second "metadata"}}}
      (provided
       (onix/environments) => ["env1" "env2"]
       (onix/environment "env1") => {:name "env1" :metadata {:first "metadata"}}
       (onix/environment "env2") => {:name "env2" :metadata {:second "metadata"}}))

(fact "that getting default environments works"
      (default-environments) => {:env1 {:name "env1" :metadata {:account-id "onething"
                                                                :create-repo true}}}
      (provided
       (environments) => {:env1 {:name "env1"
                                 :metadata {:account-id "onething"
                                            :create-repo true}}
                          :env2 {:name "env2"
                                 :metadata {:account-id "another"}}}))
