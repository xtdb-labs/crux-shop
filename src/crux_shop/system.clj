(ns crux-shop.system
  (:require
    [com.stuartsierra.component :as component]
    [crux-shop.schema :as schema]
    [crux-shop.server :as server]
    [crux-shop.db :as db]))

(defn new-system
  []
  (merge (component/system-map)
         (server/new-server)
         (schema/new-schema-provider)
         (db/new-node)))
