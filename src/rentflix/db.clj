(ns rentflix.db
    (:require [datomic.api :as dat]))

; This is our db uri.
(defonce db-uri "datomic:mem://rentflix")

; This is our schema
(defonce db-schema (read-string (slurp "resources/db-schema.edn")))

; This is our test data
(defonce db-test-data (read-string (slurp "resources/db-test-data.edn")))

; FOR TESTING - drop test database
(dat/delete-database db-uri)

; This creates the db and puts the schema in there, then adds a connection
(def conn
    (do
        (if (dat/create-database db-uri)
            (dat/transact (dat/connect db-uri) db-schema))
        (dat/connect db-uri)))

; FOR TESTING - insert testing data
(dat/transact conn db-test-data)

; Shortcut method for getting current state of the db
(defn get-db [] (dat/db conn))

; Got an entity id? Dereference it to a map!
(defn get-entity [id] (dat/entity (get-db) id))

; Shortcut method for querying with arguments
(defn query
    [query & args]
    (apply dat/q (concat [query (get-db)] args)))

