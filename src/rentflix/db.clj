(ns rentflix.db
    (:require [datomic.api :as dat]))

; This is our db uri.
(defonce uri "datomic:mem://rentflix")

; Make sure the database exists
(dat/create-database uri)

; Connect to the db.
(def conn (dat/connect uri))