(defproject tyranitar "0.56"
  :description "Tyranitar service"
  :url "http://wikis.in.nokia.com/NokiaMusicArchitecture/Tyranitar"

  :dependencies [[ch.qos.logback/logback-classic "1.1.2"]
                 [cheshire "5.3.1"]
                 [clj-http "1.0.0"]
                 [clj-time "0.8.0"]
                 [com.cemerick/url "0.1.1"]
                 [compojure "1.2.0"]
                 [de.ubercode.clostache/clostache "1.4.0"]
                 [environ "1.0.0"]
                 [io.clj/logging "0.8.1"]
                 [me.raynes/conch "0.7.0"]
                 [mixradio/graphite-filter "1.0.0"]
                 [mixradio/instrumented-ring-jetty-adapter "1.0.4"]
                 [mixradio/radix "1.0.5"]
                 [net.logstash.logback/logstash-logback-encoder "3.2"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [overtone/at-at "1.2.0"]
                 [ring-middleware-format "0.4.0"]
                 [tentacles.custom "0.2.8"]]

  :exclusions [commons-logging
               log4j
               org.clojure/clojure]

  :profiles {:dev {:dependencies [[midje "1.6.3"]]
                   :plugins [[jonase/kibit "0.0.8"]
                             [lein-midje "3.1.3"]
                             [lein-rpm "0.0.5"]]}}

  :plugins [[lein-environ "1.0.0"]
            [lein-release "1.0.5"]
            [lein-ring "0.8.12"]]

  :env {:auto-reload true
        :environment-name "dev"
        :graphite-enabled false
        :graphite-host "carbon.brislabs.com"
        :graphite-port 2003
        :graphite-post-interval-seconds 60
        :github-auth-token "auth-token"
        :github-baseurl "http://github/api/v3/"
        :github-organisation "tyranitar"
        :logging-consolethreashold "off"
        :logging-filethreshold "info"
        :logging-level "info"
        :logging-path "/tmp"
        :logging-stashthreshold "off"
        :onix-baseurl "http://onix"
        :production false
        :requestlog-enabled false
        :requestlog-retainhours 24
        :service-name "tyranitar"
        :service-port "8080"
        :shutdown-timeout-millis 5000
        :start-timeout-seconds 120
        :threads 254}

  :lein-release {:deploy-via :shell
                 :shell ["lein" "do" "clean," "uberjar," "pom," "rpm"]}

  :ring {:handler tyranitar.web/app
         :main tyranitar.setup
         :port ~(Integer/valueOf (get (System/getenv) "SERVICE_PORT" "8080"))
         :init tyranitar.setup/setup
         :browser-uri "/healthcheck"
         :nrepl {:start? true}}

  :repositories {"internal-clojars"
                 "http://clojars.brislabs.com/repo"
                 "rm.brislabs.com"
                 "http://rm.brislabs.com/nexus/content/groups/all-releases"}

  :uberjar-name "tyranitar.jar"

  :eastwood {:namespaces [:source-paths]}

  :rpm {:name "tyranitar"
        :summary "RPM for Tyranitar service"
        :copyright "MixRadio 2014"
        :preinstall {:scriptFile "scripts/rpm/preinstall.sh"}
        :postinstall {:scriptFile "scripts/rpm/postinstall.sh"}
        :preremove {:scriptFile "scripts/rpm/preremove.sh"}
        :postremove {:scriptFile "scripts/rpm/postremove.sh"}
        :requires ["jdk >= 2000:1.7.0_55-fcs"]
        :mappings [{:directory "/usr/local/tyranitar"
                    :filemode "444"
                    :username "tyranitar"
                    :groupname "tyranitar"
                    :sources {:source [{:location "target/tyranitar.jar"}]}}
                   {:directory "/usr/local/tyranitar/bin"
                    :filemode "744"
                    :username "tyranitar"
                    :groupname "tyranitar"
                    :sources {:source [{:location "scripts/bin"}]}}
                   {:directory "/etc/rc.d/init.d"
                    :filemode "755"
                    :sources {:source [{:location "scripts/service/tyranitar"
                                        :destination "tyranitar"}]}}]}

  :main tyranitar.setup)
  :repositories {"internal-clojars"
                 "http://clojars.brislabs.com/repo"
                 "rm.brislabs.com"
                 "http://rm.brislabs.com/nexus/content/groups/all-releases"}

  :repositories {"internal-clojars"
                 "http://clojars.brislabs.com/repo"
                 "rm.brislabs.com"
                 "http://rm.brislabs.com/nexus/content/groups/all-releases"}
