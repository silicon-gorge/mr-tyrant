(defproject tyranitar "0.53"
  :description "Tyranitar service"
  :url "http://wikis.in.nokia.com/NokiaMusicArchitecture/Tyranitar"

  :dependencies [[ch.qos.logback/logback-classic "1.1.2"]
                 [cheshire "5.3.1"]
                 [clj-http "1.0.0"]
                 [clj-time "0.8.0"]
                 [com.cemerick/url "0.1.1"]
                 [com.ovi.common.logging/logback-appender "0.0.47"]
                 [com.ovi.common.metrics/metrics-graphite "2.1.25"]
                 [compojure "1.1.8" :exclusions [javax.servlet/servlet-api]]
                 [de.ubercode.clostache/clostache "1.4.0"]
                 [environ "1.0.0"]
                 [io.clj/logging "0.8.1"]
                 [me.raynes/conch "0.7.0"]
                 [metrics-clojure "1.1.0"]
                 [metrics-clojure-ring "1.1.0"]
                 [nokia/instrumented-ring-jetty-adapter "0.1.10"]
                 [nokia/ring-utils "1.2.4"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.0"]
                 [org.eclipse.jetty/jetty-server "8.1.15.v20140411"]
                 [org.slf4j/jcl-over-slf4j "1.7.7"]
                 [org.slf4j/jul-to-slf4j "1.7.7"]
                 [org.slf4j/log4j-over-slf4j "1.7.7"]
                 [org.slf4j/slf4j-api "1.7.7"]
                 [overtone/at-at "1.2.0"]
                 [ring-middleware-format "0.4.0"]
                 [tentacles.custom "0.2.8"]]

  :exclusions [commons-logging
               log4j]

  :profiles {:dev {:dependencies [[midje "1.6.3"]]
                   :plugins [[lein-rpm "0.0.5"]
                             [lein-midje "3.1.3"]
                             [jonase/kibit "0.0.8"]]}}

  :plugins [[lein-ring "0.8.11"]
            [lein-environ "1.0.0"]
            [lein-release "1.0.73"]]

  :env {:environment-entertainment-graphite-host "carbon.brislabs.com"
        :environment-entertainment-graphite-port "2003"
        :environment-music-errorlogging1java-baseurl "http://errorlogging.music.cq1.brislabs.com:8080/ErrorLogging/1.x"
        :environment-name "dev"
        :github-auth-token "auth-token"
        :github-base-url "http://github/api/v3/"
        :github-organisation "tyranitar"
        :service-graphite-enabled "DISABLED"
        :service-graphite-post-interval "1"
        :service-graphite-post-unit "MINUTES"
        :service-logging-filethreshold "info"
        :service-logging-level "info"
        :service-logging-path "/tmp"
        :service-logging-servicethreshold "off"
        :service-name "tyranitar"
        :service-onix-url "http://onix"
        :service-port "8080"
        :service-production "false"}

  :lein-release {:release-tasks [:clean :uberjar :pom :rpm]
                 :clojars-url "clojars@clojars.brislabs.com:"}

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
        :copyright "Nokia 2013"
        :preinstall {:scriptFile "scripts/rpm/preinstall.sh"}
        :postinstall {:scriptFile "scripts/rpm/postinstall.sh"}
        :preremove {:scriptFile "scripts/rpm/preremove.sh"}
        :postremove {:scriptFile "scripts/rpm/postremove.sh"}
        :requires ["jdk >= 2000:1.6.0_31-fcs"]
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
                    :filemode "744"
                    :username "tyranitar"
                    :groupname "tyranitar"
                    :sources {:source [{:location "scripts/service/tyranitar"}]}}]}

  :main tyranitar.setup)
