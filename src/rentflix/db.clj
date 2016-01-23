(ns rentflix.db
    (:require [datomic.api :as dat]))

;; Setup

; This is our db uri.
(defonce uri "datomic:mem://rentflix")

; Make sure the database exists
(dat/create-database uri)

; Connect to the db
(def conn (dat/connect uri))

; submit schema transaction
(dat/transact conn (read-string (slurp "resources/rentflix.schema.edn")))

;; Public functions

; Shortcut method for querying with arguments
(defn query
    [query & args]
    (apply dat/q (concat [(dat/db conn) args])))