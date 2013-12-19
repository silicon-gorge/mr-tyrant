(ns tyranitar.unit.store
  (:require [tyranitar.git :as git])
  (:require [clojure.java.io :as io]
            [cheshire.core :as json])
  (:use [tyranitar.store]
        [midje.sweet]))

(def dummy-repo-path "/tmp")

(def dummy-data {:hash "d2d11ce2b4f6f0e96c3d80c1676c1ae4727c4939"
                 :data {:environment.stuff "stuffy"
                        :service.main.thing "wibble"
                        :service.other.thing "wobble"}})

(def update {:service.max.asg 3
             :service.min.asg 1
             :service.preferred.asg 2
             :service.other.thing "changed"})

(def expected-update {:environment.stuff "stuffy"
                      :service.main.thing "wibble"
                      :service.max.asg 3
                      :service.min.asg 1
                      :service.other.thing "changed"
                      :service.preferred.asg 2})

(defn check-deployment-params
  [props template-params]
  (let [name (:name template-params)]
    (and
     (= name (:selectedLoadBalancers props))
     (= "Brislabs-SSH" (second (:selectedSecurityGroups props))))))

(defn is-correct-val-for-env
  [env val]
  (if (= "prod" env)
    val
    (not val)))

(defn check-app-props
  [props template-params]
  (and
   (= (:env template-params) (:environment.name props))
   (= (:name template-params) (:service.name props))
   (is-correct-val-for-env (:env template-params) (:service.production props))))

(defn property-values-are-correctly-templated
  [params f]
  (try
    (write-templated-properties (:name params) (:template params) (:env params))
    (let [temp-file (str dummy-repo-path "/" (:template params) ".json")
          props (json/parse-string (slurp temp-file) true)]
      (f props params))
    (finally
      (io/delete-file (str dummy-repo-path "/" (:template params) ".json")))))

(fact-group :unit

            (facts "****** About templating properties files ******"

                   (fact "Template values are correctly substituted in deployment params."
                         (property-values-are-correctly-templated {:name "test"
                                                                   :template "deployment-params"
                                                                   :env "poke"}
                                                                  check-deployment-params) => true

                                                                  (provided
                                                                   (git/repo-path anything) => dummy-repo-path))

                   (fact "Template values are correctly substituted in poke application properties."
                         (property-values-are-correctly-templated {:name "test"
                                                                   :template "application-properties"
                                                                   :env "poke"}
                                                                  check-app-props) => true
                                                                  (provided
                                                                   (git/repo-path anything) => dummy-repo-path))

                    (fact "Template values are correctly substituted in prod application properties."
                         (property-values-are-correctly-templated {:name "test"
                                                                   :template "application-properties"
                                                                   :env "prod"}
                                                                  check-app-props) => true
                                                                  (provided
                                                                   (git/repo-path anything) => dummy-repo-path)))

            (facts "****** About updating application properties ******"

                   (fact "Existing properties are updated with correct values."
                         (update-properties "dummy" "poke" "dummy-props" update) => anything
                         (provided
                          (get-data "poke" "dummy" "head" "dummy-props") => dummy-data
                          (spit "/tmp/repos/dummy-poke/dummy-props.json" (json/generate-string expected-update {:pretty true})) => anything
                          (git/commit-and-push anything anything) => anything))))
