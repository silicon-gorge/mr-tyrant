(ns tyranitar.onix
  (:require [cemerick.url :refer [url]]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [environ.core :refer [env]]))

(def connect-timeout
  2000)

(def socket-timeout
  5000)

(def onix-url
  (url (env :onix-baseurl)))

(defn environments-url
  []
  (str (url onix-url "1.x" "environments")))

(defn environment-url
  [environment]
  (str (url onix-url "1.x" "environments" environment)))

(defn environments
  []
  (let [{:keys [body status]} (http/get (environments-url) {:conn-timeout connect-timeout
                                                            :socket-timeout socket-timeout
                                                            :throw-exceptions false})]
    (when (= status 200)
      (:environments (json/parse-string body true)))))

(defn environment
  [environment]
  (let [{:keys [body status]} (http/get (environment-url environment) {:conn-timeout connect-timeout
                                                                       :socket-timeout socket-timeout
                                                                       :throw-exceptions false})]
    (when (= status 200)
      (json/parse-string body true))))
