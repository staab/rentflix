(ns rentflix.db
    (:require [datomic.api :as dat]))

;; Setup

; This is our db uri.
(defonce uri "datomic:mem://rentflix")

; Connect to the db
(defonce conn (dat/connect uri))

; Make sure the database exists. If it doesn't, create it, and fill it with
; our schema
(if (dat/create-database uri)
    (dat/transact conn (read-string (slurp "resources/rentflix.schema.edn"))))

;; Public functions

; Shortcut method for querying with arguments
(defn query
    [query & args]
    (apply dat/q (concat [(dat/db conn) args])))