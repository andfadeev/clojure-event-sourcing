(defproject clojure-event-sourcing "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.12.4"]
                 [org.testcontainers/testcontainers "2.0.2"]
                 [org.testcontainers/postgresql "1.21.3"]
                 [com.github.seancorfield/next.jdbc "1.3.1070"]
                 [org.postgresql/postgresql "42.7.8"]
                 [com.github.seancorfield/honeysql "2.7.1364"]
                 [org.flywaydb/flyway-core "11.19.0"]
                 [org.flywaydb/flyway-database-postgresql "11.19.0"]
                 [cheshire "6.1.0"]
                 [metosin/malli "0.20.0"]
                 [nubank/matcher-combinators "3.9.2"]]
  :main ^:skip-aot clojure-event-sourcing.core
  :target-path "target/%s"
  :plugins [[com.github.liquidz/antq "2.11.1276"]]
  :antq {}
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
