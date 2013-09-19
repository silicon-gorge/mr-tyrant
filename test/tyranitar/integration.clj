(ns tyranitar.integration
  (:require [tyranitar.web :as web]
            [clj-http.client :as client]
            [midje.sweet :refer :all]
            [cheshire.core :as json]
            [clojure.data.zip.xml :as xml]
            [environ.core :refer [env]])
  (:import [java.util UUID]))

(defn url+ [& suffix] (apply str
                             (format (env :service-url) (env :service-port))
                             suffix))

(defn content-type
  [response]
  (if-let [ct ((:headers response) "content-type")]
    (first (clojure.string/split ct #";"))
    :none))

(defmulti read-body content-type)

(defmethod read-body "application/xml" [http-response]
  (-> http-response
      :body
      .getBytes
      java.io.ByteArrayInputStream.
      clojure.xml/parse clojure.zip/xml-zip))

(defmethod read-body "application/json" [http-response]
  (json/parse-string (:body http-response) true))

(defmethod read-body :none [http-response]
  (throw (Exception. (str "No content-type in response: " http-response))))

(fact-group :integration
   (fact "Ping resource returns 200 HTTP response"
         (let [response (client/get (url+ "/ping")  {:throw-exceptions false})]
           response => (contains {:status 200})))

   (fact "Status returns all required elements"
         (let [response (client/get (url+ "/status") {:throw-exceptions false})
               body (read-body response)]
           response => (contains {:status 200})))

   (fact "Can obtain latest from Tyranitar test-dev git repository"
         (let [response (client/get (url+ "/apps/dev/test/latest/service-properties"))
               body (read-body response)]
           response => contains {:status 200}))

   (fact "Can obtain data from Tyranitar test-dev git repository using a specific commit id"
         (let [latest (client/get (url+ "/apps/dev/test/latest/service-properties"))
               latest-body (read-body latest)
               latest-commit-id (:hash latest-body)
               response (client/get (url+ (str "/apps/dev/test/" latest-commit-id "/service-properties")))
               body (read-body response)
               commit-id (:hash body)]
           response => (contains {:status 200})
           commit-id => latest-commit-id)))
