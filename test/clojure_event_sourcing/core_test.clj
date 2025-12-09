(ns clojure-event-sourcing.core-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [next.jdbc :as jdbc]
            [clojure-event-sourcing.core :as core])
  (:import (org.testcontainers.containers PostgreSQLContainer)
           (org.flywaydb.core Flyway)))

(def ^:dynamic *deps* nil)

(defn run-database-migrations!
  [{:keys [datasource]}]
  (log/info "Running database migrations...")
  (.migrate
    (.. (Flyway/configure)
        (dataSource datasource)
        (locations (into-array String ["classpath:database/migrations"]))
        (table "schema_version")
        (load))))

(defn with-postgres
  [f]
  (let [container (doto (PostgreSQLContainer. "postgres:16")
                    (.start))]
    (println {:jdbcUrl (.getJdbcUrl container)
              :username (.getUsername container)
              :password (.getPassword container)})
    (binding [*deps* {:datasource (jdbc/get-datasource
                                    {:jdbcUrl (.getJdbcUrl container)
                                     :username (.getUsername container)
                                     :password (.getPassword container)})}]
      (try
        (run-database-migrations! *deps*)
        (f)
        (finally
          (.stop container))))))

(use-fixtures :each with-postgres)

(deftest event-sourcing-test
  (let [customer-id (str "customer:" (random-uuid))
        order-id (random-uuid)
        order-created-event {:aggregate_id order-id
                             :aggregate_type "Order"
                             :type "OrderCreated"
                             :payload {:items ["x" "y" "z"]
                                       :customer-id customer-id
                                       :price "100.45"
                                       :status "pending"}}
        order-paid-event {:aggregate_id order-id
                          :aggregate_type "Order"
                          :type "OrderPaid"
                          :payload {:status "paid"
                                    :payment-method "CARD"}}
        tracking-number (str "TX-" (random-uuid))
        order-dispatched-event {:aggregate_id order-id
                                :aggregate_type "Order"
                                :type "OrderDispatched"
                                :payload {:status "dispatched"
                                          :tracking-number tracking-number}}
        other-order-id (random-uuid)
        other-order-created-event {:aggregate_id other-order-id
                                   :aggregate_type "Order"
                                   :type "OrderCreated"
                                   :payload {:items ["something"]
                                             :customer-id customer-id
                                             :price "99.99"
                                             :status "pending"}}]
    (core/insert-event! *deps* order-created-event)
    (core/insert-event! *deps* other-order-created-event)
    (core/insert-event! *deps* order-paid-event)
    (core/insert-event! *deps* order-dispatched-event)

    (testing "can get order by id and project events to a resource"
      (let [order (core/get-events-by-aggregate-id *deps* order-id)]
        ;; TODO: use match?
        (is (= {:items ["x" "y" "z"]
                :order-id order-id
                :payment-method "CARD"
                :price "100.45"
                :customer-id customer-id
                :resource-type "order"
                :status "dispatched"}
               (dissoc order :updated-at :created-at :tracking-number)))
        (is (some? (:tracking-number order))))
      (let [other-order (core/get-events-by-aggregate-id *deps* other-order-id)]
        (is (= {:items ["something"]
                :order-id other-order-id
                :customer-id customer-id
                :price "99.99"
                :resource-type "order"
                :status "pending"}
               (dissoc other-order :created-at)))
        (is (some? (:created-at other-order)))))))