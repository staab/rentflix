(ns rentflix.db
    (:require [datomic.api :as d]
              [environ.core :refer [env]]
              [io.rkn.conformity :as conformity]
              [rentflix.util :refer [map-keys]]))

; Basic connection stuff

; Db uri comes from environ
(def db-uri (env :datomic-uri))

; This is our schema
(def db-schema (read-string (slurp "resources/db-schema.edn")))

; Gets a connection to our database. Memoized for performance
(defn get-conn [] (d/connect db-uri))

; Shortcut method for getting current state of the db
(defn get-db [] (d/db (get-conn)))


; Database setup functions

; Deletes the database entirely
(defn delete-db [] (d/delete-database db-uri))

; Creates the database again
(defn create-db [] (d/create-database db-uri))

; Populates the schema using conformity lib
(defn populate-schema
    []
    (conformity/ensure-conforms
     (get-conn)
     {:rentflix/domain {:txes [db-schema]}}))

; Initialize database by creating and populating the schema. Returns conn
(defn init-db [] (do (create-db) (populate-schema) (get-conn)))

; Delete the database and reinitialize. Returns conn
(defn reset-db [] (do (delete-db) (init-db)))

; Make sure the db exists
(init-db)


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

(defn find-by-type
  [type limit offset]
  (->> (find-eids :type type limit offset)
     (map eid->entity)
     (map trim-entity-keys)))

(defn get-by-id
  ([eid type]
   (let [entity (eid->entity eid)]
    (if (:type entity) entity nil))))
