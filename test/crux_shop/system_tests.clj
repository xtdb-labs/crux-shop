(ns crux-shop.system-tests
  (:require
    [clojure.test :refer [deftest testing is use-fixtures]]
    [crux-shop.system :as system]
    [crux-shop.test-utils :refer [simplify]]
    [com.stuartsierra.component :as component]
    [com.walmartlabs.lacinia :as lacinia]))

(defn ^:private test-system
  "Creates a new system suitable for testing, and ensures that
  the HTTP port won't conflict with a default running system."
  []
  (-> (system/new-system)
      (assoc-in [:server :port] 8989)))

(def ^:dynamic ^:private *system*)

(use-fixtures :once
  (fn [test-fn]
    (binding [*system* (component/start-system (test-system))]
      (try
        (test-fn)
        (finally
          (component/stop-system *system*))))))

(defn ^:private q
  "Extracts the compiled schema and executes a query."
  ([query]
   (q query nil))
  ([query variables]
   (-> *system*
       (get-in [:schema-provider :schema])
       (lacinia/execute query variables nil)
       simplify)))

(deftest query-all-test
  (testing "can query all items in the db"
    (let [expected {:data
                    {:all_items
                     [{:id "moldy-bread",
                       :name "Moldy bread",
                       :description "This isn't safe to eat"}]}}
          actual (q "{all_items {id name description}}")]
      (is (= expected actual)))))

#_(deftest add-item-test
  (testing "can add item to db"
    (let [expected {:data
                    {:add_item
                     {:id "pasta", :name "pasta", :description "Delicious pasta"}}}
          actual (q "mutation {add_item(id: \"pasta\", name: \"pasta\", description: \"Delicious pasta\") {id name description}}")]
      (is (= expected actual)))

    (testing "item is actually added to the db"
      (is (-> (q "{all_items {id name description}}")
              :data
              :all_items
              set
              (contains? {:id "pasta", :name "pasta", :description "Delicious pasta"}))))))
