(defproject tyranitar "0.49-SNAPSHOT"
  :description "Tyranitar service"
  :url "http://wikis.in.nokia.com/NokiaMusicArchitecture/Tyranitar"

  :dependencies [[ch.qos.logback/logback-classic "1.1.2"]
                 [cheshire "5.3.1"]
                 [clj-http "0.9.2"]
                 [clj-time "0.7.0"]
                 [com.cemerick/url "0.1.1"]
                 [com.ovi.common.logging/logback-appender "0.0.47"]
                 [com.ovi.common.metrics/metrics-graphite "2.1.25"]
                 [compojure "1.1.8" :exclusions [javax.servlet/servlet-api]]
                 [de.ubercode.clostache/clostache "1.3.1"]
                 [environ "0.5.0"]
                 [me.raynes/conch "0.7.0"]
                 [metrics-clojure "1.1.0"]
                 [metrics-clojure-ring "1.1.0"]
                 [nokia/instrumented-ring-jetty-adapter "0.1.9"]
                 [nokia/ring-utils "1.2.4"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.eclipse.jetty/jetty-server "8.1.15.v20140411"]
                 [org.eclipse.jgit "3.4.0.201406110918-r"]
                 [org.slf4j/jcl-over-slf4j "1.7.7"]
                 [org.slf4j/jul-to-slf4j "1.7.7"]
                 [org.slf4j/log4j-over-slf4j "1.7.7"]
                 [org.slf4j/slf4j-api "1.7.7"]
                 [overtone/at-at "1.2.0"]
                 [ring-middleware-format "0.3.2"]]

  :exclusions [commons-logging
               log4j]

  :profiles {:dev {:dependencies [[midje "1.6.3"]]
                   :plugins [[lein-rpm "0.0.5"]
                             [lein-midje "3.1.3"]
                             [jonase/kibit "0.0.8"]]}}

  :plugins [[lein-ring "0.8.10"]
            [lein-environ "0.4.0"]
            [lein-release "1.0.73"]]

  :env {:environment-name "Dev"
        :service-name "tyranitar"
        :service-port "8080"
        :service-url "http://localhost:%s/1.x"
        :restdriver-port "8081"
        :service-logging-filethreshold=info
        :service-logging-path=/var/log/tyranitar
        :environment-entertainment-graphite-host "carbon.brislabs.com"
        :environment-entertainment-graphite-port "2003"
        :service-graphite-post-interval "15"
        :service-graphite-post-unit "SECONDS"
        :service-graphite-enabled "DISABLED"
        :service-onix-url "http://onix:8080"
        :service-production "false"

        :service-base-git-repository-url "ssh://snc@source.nokia.com/tyranitar/git/"
        :service-base-git-repository-path "/tmp/repos/"
        :service-snc-api-base-url "https://source.nokia.com/api/v2/"
        :service-snc-api-username "mdaley"
        :service-snc-api-secret "45186ed1acb4a8f9f5d0ff8f700eb1f7"}

  :lein-release {:release-tasks [:clean :uberjar :pom :rpm]
                 :clojars-url "clojars@clojars.brislabs.com:"}

  :ring {:handler tyranitar.web/app
         :main tyranitar.setup
         :port ~(Integer.  (get (System/getenv) "SERVICE_PORT" "8080"))
         :init tyranitar.setup/setup
         :browser-uri "/1.x/status"
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
