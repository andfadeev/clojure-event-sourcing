(ns clojure-event-sourcing.core
  (:gen-class)
  (:require [cheshire.core :as json]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [malli.core :as m]
            [malli.error :as me])
  (:import (org.postgresql.util PGobject)))

(defn execute!
  [{:keys [datasource]} query]
  (jdbc/execute!
    datasource
    (sql/format query)
    {:builder-fn rs/as-unqualified-maps}))

(defn execute-one!
  [{:keys [datasource]} query]
  (jdbc/execute-one!
    datasource
    (sql/format query)
    {:builder-fn rs/as-unqualified-maps}))

(defn ->jsonb
  [value]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (json/encode value))))

(defn <-jsonb
  [v]
  (json/decode (.getValue v) true))

(defn insert-event!
  [deps event]
  (execute!
    deps
    {:insert-into [:events]
     :values [(update event :payload ->jsonb)]
     :returning :*}))

(defmulti apply-event
          (fn [_ event]
            [(:aggregate_type event)
             (:type event)]))

(defmethod apply-event [:Order :OrderCreated]
  [_ event]
  (merge
    {:resource-type (:aggregate_type event)
     :order-id (:aggregate_id event)
     :created-at (:created-at event)}
    (:payload event)))

(defmethod apply-event [:Order :OrderPaid]
  [state event]
  (merge state
         (:payload event)
         {:updated-at (:created-at event)}))

(defmethod apply-event [:Order :OrderDispatched]
  [state event]
  (merge state
         (:payload event)
         {:updated-at (:created-at event)}))

(defn project
  ([events]
   (project {} events))
  ([state events]
   (reduce apply-event state events)))

(defn get-events-by-aggregate-id
  [deps aggregate-id]
  (->> {:select :*
        :from :events
        :where [:= :aggregate_id aggregate-id]
        :order-by [:created-at]}
       (execute! deps)
       (map (fn [event] (update event :payload <-jsonb)))
       (project)))

(defn- event->row
  [event]
  (update event :payload ->jsonb))

(defn- row->event
  [row]
  (update row :payload <-jsonb))

(defn- row->resource [row]
  {:id (:id row)
   :type (keyword (:type row))
   :payload (<-jsonb (:payload row))
   :last-event-seqno (:last_event_seqno row)
   :created-at (:created_at row)
   :updated-at (:updated_at row)
   :schema-version (:schema_version row)})

(defn- validate-or-throw
  [{:keys [schema
           data
           message]}]
  (when schema
    (if (m/validate schema data)
      data
      (throw (ex-info message
                      {:errors (me/humanize (m/explain schema data))
                       :schema schema})))))

(defn lock-resource!
  [deps {:keys [aggregate-id aggregate-type]}]
  (execute-one!
    deps
    {:insert-into :resources
     :columns [:id :type :payload :last_event_id]
     :values [[aggregate-id aggregate-type (->jsonb {}) 0]]
     :on-conflict [:id]
     :do-nothing true
     :returning :*})
  (->> {:select [:*]
        :from :resources
        :where [:= :id aggregate-id]
        :for [:update]}
       (execute-one! deps)
       (row->resource)))

(defn update-resource!
  [{:keys [resource-schema] :as deps} resource]
  (let [events (->> {:select [:*]
                     :from :events
                     :where [:and
                             [:= :aggregate_id (:id resource)]
                             [:> :id (:last_event_id resource)]]
                     :order-by [[:id :asc]]}
                    (execute! deps)
                    (mapv row->event))
        payload (project (:payload resource) events)]
    (validate-or-throw {:schema resource-schema
                        :data payload
                        :message "Resource schema validation failed"})
    (execute-one!
      deps
      {:update :resources
       :set {:payload (->jsonb payload)
             :last_event_seqno (:seqno (last events))
             :updated_at [:now]}
       :where [:= :id (:id resource)]
       :returning :*})))

(defn publish!
  [{:keys [datasource
           event-schema
           resource-schema] :as deps} event]
  (jdbc/with-transaction
    [transaction datasource]
    (validate-or-throw {:schema event-schema
                        :data (:payload event)
                        :message "Event schema validation failed"})
    (let [deps (assoc deps :datasource transaction)
          resource (lock-resource! deps event)
          inserted-event (execute-one!
                           deps
                           {:insert-into :events
                            :values [(event->row event)]
                            :returning :*})]
      (when inserted-event
        (update-resource! deps resource)))))

(defn -main [])
