(defproject clojure-event-sourcing "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.testcontainers/testcontainers "1.18.0"]
                 [org.testcontainers/postgresql "1.18.0"]
                 [com.github.seancorfield/next.jdbc "1.3.883"]
                 [org.postgresql/postgresql "42.2.10"]
                 [com.zaxxer/HikariCP "5.0.1"]
                 [com.github.seancorfield/honeysql "2.4.1066"]
                 [org.flywaydb/flyway-core "9.21.2"]
                 [cheshire "6.1.0"]
                 [metosin/malli "0.20.0"]]
  :main ^:skip-aot clojure-event-sourcing.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
