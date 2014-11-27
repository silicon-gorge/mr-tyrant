(ns tyrant.setup
  (:require [clojure.string :as str]
            [environ.core :refer [env]]
            [mixradio.instrumented-jetty :refer [run-jetty]]
            [ninjakoala.ttlr :as ttlr]
            [tyrant
             [environments :as environments]
             [store :as store]
             [web :as web]]
            [radix.setup :as setup])
  (:gen-class))

(defonce server
  (atom nil))

(defn configure-server
  [server]
  (doto server
    (.setStopAtShutdown true)
    (.setStopTimeout setup/shutdown-timeout)))

(defn start-server
  []
  (run-jetty #'web/app {:port setup/service-port
                        :max-threads setup/threads
                        :join? false
                        :stacktraces? (not setup/production?)
                        :auto-reload? (not setup/production?)
                        :configurator configure-server
                        :send-server-version false}))

(defn start
  []
  (setup/configure-logging)
  (setup/start-graphite-reporting {:graphite-prefix (str/join "." [(env :environment-name) (env :service-name) (env :box-id setup/hostname)])})
  (ttlr/init :cpu-count 1)
  (environments/init)
  (store/init)
  (reset! server (start-server)))

(defn stop
  []
  (when-let [s @server]
    (.stop s)
    (reset! server nil))
  (shutdown-agents))

(defn -main
  [& args]
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop))
  (start))
