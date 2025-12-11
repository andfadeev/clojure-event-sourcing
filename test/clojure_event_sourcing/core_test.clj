(ns clojure-event-sourcing.core-test
  (:require [clojure.test :refer :all]
            [next.jdbc :as jdbc]
            [clojure-event-sourcing.core :as core]
            [matcher-combinators.test :refer [match?]])
  (:import (org.testcontainers.containers PostgreSQLContainer)
           (org.flywaydb.core Flyway)))

(def ^:dynamic *deps* nil)

(defn run-database-migrations!
  [{:keys [datasource]}]
  (.migrate
    (.. (Flyway/configure)
        (dataSource datasource)
        (locations (into-array String ["classpath:database/migrations"]))
        (table "schema_version")
        (load))))

(defn with-postgres
  [f]
  (let [container (doto (PostgreSQLContainer. "postgres:17")
                    (.start))]
    (println {:jdbcUrl (.getJdbcUrl container)
              :username (.getUsername container)
              :password (.getPassword container)})
    (binding [*deps* {:datasource (jdbc/get-datasource
                                    {:jdbcUrl (.getJdbcUrl container)
                                     :user (.getUsername container)
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
                                       :customer_id customer-id
                                       :price "100.45"
                                       :status "pending"}}
        order-paid-event {:aggregate_id order-id
                          :aggregate_type "Order"
                          :type "OrderPaid"
                          :payload {:status "paid"
                                    :payment_method "CARD"}}
        tracking-number (str "TX-" (random-uuid))
        order-dispatched-event {:aggregate_id order-id
                                :aggregate_type "Order"
                                :type "OrderDispatched"
                                :payload {:status "dispatched"
                                          :tracking_number tracking-number}}
        other-order-id (random-uuid)
        other-order-created-event {:aggregate_id other-order-id
                                   :aggregate_type "Order"
                                   :type "OrderCreated"
                                   :payload {:items ["something"]
                                             :customer_id customer-id
                                             :price "99.99"
                                             :status "pending"}}]

    (core/insert-event! *deps* order-created-event)
    (core/insert-event! *deps* other-order-created-event)
    (core/insert-event! *deps* order-paid-event)
    (core/insert-event! *deps* order-dispatched-event)

    (testing "can get order by id and project events to a resource"
      (is (match? {:items ["x" "y" "z"]
                   :order_id order-id
                   :payment_method "CARD"
                   :price "100.45"
                   :customer_id customer-id
                   :resource_type "Order"
                   :status "dispatched"
                   :updated_at inst?
                   :created_at inst?
                   :tracking_number string?} (core/get-events-by-aggregate-id *deps* order-id)))
      (is (match? {:items ["something"]
                   :order_id other-order-id
                   :customer_id customer-id
                   :price "99.99"
                   :resource_type "Order"
                   :status "pending"
                   :created_at inst?}
                  (core/get-events-by-aggregate-id *deps* other-order-id))))))

(def OrderResourceSchema
  [:map {:closed true}
   [:created_at :string]
   [:customer_id :string]
   [:items {:optional true} [:vector string?]]
   [:order_id :string]
   [:payment_method {:optional true} [:enum "CARD"]]
   [:price :string]
   [:resource_type [:enum "Order"]]
   [:status [:enum "pending" "paid" "dispatched"]]
   [:tracking_number {:optional true} :string]
   [:updated_at {:optional true} :string]])

(deftest event-sourcing-with-resources-test
  (let [*deps* (assoc *deps* :resource-schema OrderResourceSchema)
        customer-id (str "customer:" (random-uuid))
        order-id (random-uuid)
        order-created-event {:aggregate_id order-id
                             :aggregate_type "Order"
                             :type "OrderCreated"
                             :payload {:items ["x" "y" "z"]
                                       :customer_id customer-id
                                       :price "100.45"
                                       :status "pending"}}
        order-paid-event {:aggregate_id order-id
                          :aggregate_type "Order"
                          :type "OrderPaid"
                          :payload {:status "paid"
                                    :payment_method "CARD"}}
        tracking-number (str "TX-" (random-uuid))
        order-dispatched-event {:aggregate_id order-id
                                :aggregate_type "Order"
                                :type "OrderDispatched"
                                :payload {:status "dispatched"
                                          :tracking_number tracking-number}}
        other-order-id (random-uuid)
        other-order-created-event {:aggregate_id other-order-id
                                   :aggregate_type "Order"
                                   :type "OrderCreated"
                                   :payload {:items ["something"]
                                             :customer_id customer-id
                                             :price "99.99"
                                             :status "pending"}}]

    (core/publish! *deps* order-created-event)
    (is (match? {:created_at inst?
                 :id order-id
                 :last_event_id 1
                 :payload {:created_at string?
                           :customer_id customer-id
                           :items ["x"
                                   "y"
                                   "z"]
                           :order_id (str order-id)
                           :price "100.45"
                           :resource_type "Order"
                           :status "pending"}
                 :type :Order
                 :updated_at inst?}
                (core/get-resource-by-id *deps* order-id)))

    (core/publish! *deps* other-order-created-event)
    (is (match? {:created_at inst?
                 :id other-order-id
                 :last_event_id 2
                 :payload {:created_at string?
                           :customer_id customer-id
                           :items ["something"]
                           :order_id (str other-order-id)
                           :price "99.99"
                           :resource_type "Order"
                           :status "pending"}
                 :type :Order
                 :updated_at inst?} (core/get-resource-by-id *deps* other-order-id)))

    (core/publish! *deps* order-paid-event)
    (is (match? {:created_at inst?
                 :id order-id
                 :last_event_id 3
                 :payload {:created_at string?
                           :customer_id customer-id
                           :items ["x" "y" "z"]
                           :order_id (str order-id)
                           :payment_method "CARD"
                           :price "100.45"
                           :resource_type "Order"
                           :status "paid"
                           :updated_at string?}
                 :type :Order
                 :updated_at inst?}
                (core/get-resource-by-id *deps* order-id)))

    (core/publish! *deps* order-dispatched-event)
    (is (match? {:created_at inst?
                 :id order-id
                 :last_event_id 4
                 :payload {:created_at string?
                           :customer_id customer-id
                           :items ["x" "y" "z"]
                           :order_id (str order-id)
                           :payment_method "CARD"
                           :price "100.45"
                           :resource_type "Order"
                           :status "dispatched"
                           :tracking_number string?
                           :updated_at string?}
                 :type :Order
                 :updated_at inst?}
                (core/get-resource-by-id *deps* order-id)))

    (is (match? {:items ["x" "y" "z"]
                 :order_id (str order-id)
                 :payment_method "CARD"
                 :price "100.45"
                 :customer_id customer-id
                 :resource_type "Order"
                 :status "dispatched"
                 :updated_at string?
                 :created_at string?
                 :tracking_number string?} (core/get-events-by-aggregate-id *deps* order-id)))

    (is (match? {:items ["something"]
                 :order_id (str other-order-id)
                 :price "99.99"
                 :customer_id customer-id
                 :resource_type "Order"
                 :status "pending"
                 :created_at string?} (core/get-events-by-aggregate-id *deps* other-order-id)))))