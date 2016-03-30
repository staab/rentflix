(ns rentflix.db
    (:require [datomic.api :as d]
              [rentflix.util :refer [map-keys]]))

; This is our db uri.
(defonce db-uri "datomic:free://localhost:4334/rentflix-dev")

; This is our schema
(defonce db-schema (read-string (slurp "resources/db-schema.edn")))

; This is our test data
(defonce db-test-data (read-string (slurp "resources/db-test-data.edn")))

; FOR TESTING - drop test database
(d/delete-database db-uri)

; This creates the db and puts the schema in there, then adds a connection
(def conn
    (do
        (if (d/create-database db-uri)
            (d/transact (d/connect db-uri) db-schema))
        (d/connect db-uri)))

; FOR TESTING - insert testing data
(d/transact conn db-test-data)

; Shortcut method for getting current state of the db
(defn get-db [] (d/db conn))

; Utils

(defn eid->entity
  [eid]
  (into {:id eid} (d/touch (d/entity (get-db) eid))))

(defn find-eids
  [attr value limit offset]
  (let [query '[:find ?eid
                :in $ ?attr ?value
                :where [?eid ?attr ?value]]
        db (get-db)
        eids (d/q query db attr value)
        result (drop offset (sort eids))]
    (map first (take limit result))))

(defn trim-entity-keys
  "Remove type, trim namespace off of keys"
  [entity]
  (let [alter-key #(keyword (second (re-find #"/?([a-zA-Z0-9]+)$" (str %))))]
    (dissoc (map-keys alter-key (dissoc entity :type)) :type)))