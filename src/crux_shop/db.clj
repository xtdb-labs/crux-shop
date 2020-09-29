(ns crux-shop.db
  (:require
   [com.stuartsierra.component :as component]
   [io.pedestal.log :as log]
   [crux.api :as crux]
   [clojure.string :as str]
   [clojure.set :as set]))

(defn- external-view
  [m]
  (if-let [id (:crux.db/id m)]
    (assoc m :id id)
    m))

(defn insert!
  "Inserts data into crux, data can be either a map or a sequence of maps.
   Optionally takes a async? boolean, if true the function will immediately
  return the data without waiting for crux to confirm the transaction. Async? is
  false by default"
  ([node data] (insert! node data false))
  ([node data async?]
   (println "inserting" data)
   (if (seq data)
     (let [{:keys [crux.tx/tx-time]}
           (crux/submit-tx
            node
            (if (map? data)
              [[:crux.tx/put
                data]]
              (vec (for [item data]
                     [:crux.tx/put item]))))]
       (when-not async? (crux/sync node))
       (when (map? data)
         (external-view (assoc data :valid-time tx-time))))
     (log/error "Not transacting as data is not valid" {:data data}))))

(defn query
  [node statement]
  (if node
    (let [db (crux/db node)
          query statement]
      (log/debug :query (str/replace query #"\s+" " "))
      (let [result (crux/q db query)]
        (if (and (seq result) (= 1 (count (:find query))))
          (->> result
               (map first)
               (map external-view))
          (external-view result))))
    (log/error "invalid node, node = " node)))

(defn all-ids
  [node]
  (query node '{:find [?e]
                :where [[?e :crux.db/id]]}))

(defn drop-db!
  "WARNING deletes every item in the db. Although as this doesn't use evict the
  data will still exist in the history."
  [node]
  (when node
    (crux/submit-tx
     node
     (for [id (all-ids node)]
       [:crux.tx/delete id]))))

(defn all-transactions
  [node]
  (query node
         '{:find [(eql/project ?e [*])]
           :where [(or [?e :type :PURCHASE]
                       [?e :type :REFUND])]}))

(defn shop-balance
  [node]
  (let [transactions (all-transactions node)]
    (reduce (fn [acc tx]
              ((if (= :PURCHASE (:type tx))
                 +
                 -)
               acc
               (* (:quantity tx)
                  (:amount tx)))) 0 transactions)))

(defn all-items
  [node]
  (query node
         '{:find [(eql/project ?e [*])]
           :where [[?e :type :item]]}))

(defn item-by-id
  [node args]
  (external-view
   (crux/entity (crux/db node) (:id args))))

(defn add-item
  [node doc]
  (let [crux-doc (-> doc
                     (assoc :type :item)
                     (set/rename-keys {:id :crux.db/id}))]
    (crux/submit-tx node [[:crux.tx/put crux-doc]])
    (log/debug :adding crux-doc)
    (external-view crux-doc)))

(defn update-quantity
  [node {:keys [id quantity] :as args}]
  (crux/submit-tx node [[:crux.tx/fn :update-quantity id quantity]])
  (external-view args))

(defn sell-item
  [node {:keys [id quantity] :or {quantity 1} :as args}]
  (crux/submit-tx node [[:crux.tx/fn :sell id quantity]])
  (external-view args))

(defn create-transaction
  [item-id quantity amount type]
  {:crux.db/id (str (gensym "transaction"))
   :type type
   :quantity quantity
   :item item-id
   :amount amount})

(defn seed!
  [node]
  (let [seed-docs [{:crux.db/id "moldy-bread"
                    :name "Moldy bread"
                    :type :item
                    :quantity 10
                    :price 100
                    :description "This isn't safe to eat"}

                   {:crux.db/id :sell
                    :crux.db/fn
                    '(fn [ctx item-id quantity]
                       (let [db (crux.api/db ctx)
                             item (crux.api/entity db item-id)
                             price (:price item)
                             new-quantity (- (:quantity item) quantity)]
                         (when (< 0 new-quantity)
                           [[:crux.tx/put (assoc item :quantity new-quantity)]
                            [:crux.tx/put (crux-shop.db/create-transaction item-id quantity price :PURCHASE)]])))}

                   {:crux.db/id :update-quantity
                    :crux.db/fn '(fn [ctx eid quantity]
                                   (let [db (crux.api/db ctx)
                                         entity (crux.api/entity db eid)]
                                     [[:crux.tx/put (assoc entity :quantity quantity)]]))}]]
    (log/debug :seeding seed-docs)
    (insert! node seed-docs))
  (log/debug :seeding "done"))

(defrecord CruxNode []
  component/Lifecycle
  (start [this]
    (log/debug :starting "starting")
    (let [node (crux/start-node {})]
      (seed! node)
      (assoc this :node node)))
  (stop [this]
    (when-let [node (:node this)]
      (drop-db! (:node this))
      (.close node))
    (assoc this :node nil)))

(defn new-node
  []
  {:db (map->CruxNode {})})
