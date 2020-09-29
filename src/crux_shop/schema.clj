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

#_(defn- add-item
  [node]
  (fn [_ args _]
    (db/add-item node args)))

(defn- resolver-map
  [component]
  (let [node (get-in component [:db :node])]
    {:query/all-items (all-items node)
;;     :mutation/add-item (add-item node)
     }))

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
