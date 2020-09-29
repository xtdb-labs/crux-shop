(ns crux-shop.schema
  "Contains custom resolvers and a function to provide the full schema."
  (:require
   [clojure.java.io :as io]
   [com.walmartlabs.lacinia.util :as util]
   [com.stuartsierra.component :as component]
   [crux-shop.db :as db]
   [clojure.edn :as edn]
   [com.walmartlabs.lacinia.schema :as schema]))

(defn- all-items
  [node]
  (fn [_ _args _]
    (db/all-items node)))

(defn- all-transactions
  [node]
  (fn [_ _args _]
    (db/all-transactions node)))

(defn- shop-balance
  [node]
  (fn [_ _args _]
    (db/shop-balance node)))

(defn- item-by-id
  [node]
  (fn [_ args _]
    (db/item-by-id node args)))

(defn- add-item
  [node]
  (fn [_ args _]
    (db/add-item node args)))

(defn- update-quantity
  [node]
  (fn [_ args _]
    (db/update-quantity node args)))

(defn- sell-item
  [node]
  (fn [_ args _]
    (db/sell-item node args)))

(defn- resolver-map
  [component]
  (let [node (get-in component [:db :node])]
    {:query/all-items (all-items node)
     :query/all-transactions (all-transactions node)
     :query/shop-balance (shop-balance node)
     :query/item-by-id (item-by-id node)
     :mutation/add-item (add-item node)
     :mutation/update_quantity (update-quantity node)
     :mutation/sell_item (sell-item node)}))

(defn- load-schema
  [component]
  (-> (io/resource "schema.edn")
      slurp
      edn/read-string
      (util/attach-resolvers (resolver-map component))
      schema/compile))

(defrecord SchemaProvider [schema]

  component/Lifecycle

  (start [this]
    (assoc this :schema (load-schema this)))

  (stop [this]
    (assoc this :schema nil)))

(defn new-schema-provider
  []
  {:schema-provider (-> {}
                        map->SchemaProvider
                        (component/using [:db]))})
