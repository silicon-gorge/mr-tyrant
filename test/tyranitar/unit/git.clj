(ns tyranitar.unit.git
  (:require [clojure.java.io :as io]
            [cheshire.core :as json])
  (:use [tyranitar.git]
        [midje.sweet]))

(def dummy-repo-path "/tmp")

(defn check-deployment-params
  [props template-params]
  (let [name (:name template-params)]
    (and
     (= name (:selectedLoadBalancers props))
     (= name (first (:selectedSecurityGroups props))))))

(defn is-correct-val-for-env
  [env val]
  (if (= "prod" env)
    (= "true" val)
    (= "false" val)))

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
    (let [temp-file (str dummy-repo-path "/" (:template params))
          props (json/parse-string (slurp temp-file) true)]
      (f props params))
    (finally
      (io/delete-file (str dummy-repo-path "/" (:template params))))))

(fact-group :unit

            (facts "****** About templating properties files ******"

                   (fact "Template values are correctly substituted in deployment params."
                         (property-values-are-correctly-templated {:name "test"
                                                                   :template "deployment-params.json"
                                                                   :env "dev"}
                                                                  check-deployment-params) => true

                                                                  (provided
                                                                   (repo-path anything) => dummy-repo-path))

                   (fact "Template values are correctly substituted in dev application properties."
                         (property-values-are-correctly-templated {:name "test"
                                                                   :template "application-properties.json"
                                                                   :env "dev"}
                                                                  check-app-props) => true
                                                                  (provided
                                                                   (repo-path anything) => dummy-repo-path))

                    (fact "Template values are correctly substituted in prod application properties."
                         (property-values-are-correctly-templated {:name "test"
                                                                   :template "application-properties.json"
                                                                   :env "prod"}
                                                                  check-app-props) => true
                                                                  (provided
                                                                   (repo-path anything) => dummy-repo-path))))
