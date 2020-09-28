(ns user
  (:require
   [com.walmartlabs.lacinia :as lacinia]
   [crux-shop.system :refer [new-system]]
   [reloaded.repl :refer [system init start stop reset-all]]
   [crux-shop.test-utils :refer [simplify]]
   [io.pedestal.log :as log]))

(defn q
  [query-string]
  (-> system
      :schema-provider
      :schema
      (lacinia/execute query-string nil nil)
      simplify))

(defn go []
  (reloaded.repl/set-init! #(new-system))
  (reloaded.repl/go))

(defn reset []
  (log/debug :stop "stopping system")
  (stop)
  (go)
  (log/debug :start "started system"))
