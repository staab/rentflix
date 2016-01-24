(ns rentflix.db
    (:require [datomic.api :as dat]))

; This is our db uri.
(defonce db-uri "datomic:mem://rentflix")

; This is our schema
(defonce db-schema (read-string (slurp "resources/db-schema.edn")))

; This is our test data
(defonce db-test-data (read-string (slurp "resources/db-test-data.edn")))

; This creates the db and puts the schema in there, then adds a connection
(defonce conn
    (do
        (if (dat/create-database db-uri)
            (dat/transact (dat/connect db-uri) db-schema))
        (dat/connect db-uri)))

; Shortcut method for querying with arguments
(defn query
    [query & args]
    (apply dat/q (concat [query (dat/db conn)] args)))

