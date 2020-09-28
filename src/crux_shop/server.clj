(ns crux-shop.server
  (:require [com.stuartsierra.component :as component]
            [com.walmartlabs.lacinia.pedestal2 :as lp]
            [io.pedestal.http :as http]))

(defrecord Server [schema-provider server port]

  component/Lifecycle
  (start [this]
    (assoc this :server (-> schema-provider
                            :schema
                            (lp/default-service {:graphiql true
                                                 :port port})
                            http/create-server
                            http/start)))

  (stop [this]
    (when server (http/stop server))
    (assoc this :server nil)))

(defn new-server
  []
  {:server (component/using (map->Server {:port 8888})
                            [:schema-provider])})
