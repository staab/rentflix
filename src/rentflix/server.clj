(ns rentflix.server
  (:require [argo.core :as argo]
            [compojure.core :refer [GET defroutes]]
            [compojure.handler :refer [api]]
            [compojure.route :refer [not-found]]
            [datomic.api :as d]
            [rentflix.db :as db]))

(def base-url "/api/v1/model")

; Utils

(defn find-by-type
  [type limit offset]
  (->> (db/find-eids :type type limit offset)
     (map db/eid->entity)
     (map db/trim-entity-keys)))

(defn get-by-id
  ([eid type]
   (let [entity (db/eid->entity eid)]
    (if (:type entity) entity nil))))

; Resource functions

(defn resource-find
  "Find a resource given the request object"
  [type req]
  (let [limit (get-in req [:page :limit])
        offset (get-in req [:page :offset])]
    {:data (find-by-type type limit offset)}))

(defn resource-get
  "Get a single resource given the request object"
  [type req]
  (let [id (Long/parseLong (get-in req [:params :id]))]
    {:data (get-by-id id type)}))

; Resources

(argo/defresource title {:find (partial resource-find :type/title)
                         :get (partial resource-get :type/title)})

(argo/defapi v1 {:resources [title] :base-url base-url})

; Routes

(defroutes api-routes
  (GET base-url [] "This is the api root")
  (GET (str base-url "*") req (v1 req))
  (not-found "Page not found"))

; Middleware

(defn ignore-trailing-slash
  "Modifies the request uri before calling the handler.
  Removes a single trailing slash from the end of the uri if present.

  Useful for handling optional trailing slashes until Compojure's route
  matching syntax supports regex. Adapted from
  http://stackoverflow.com/questions/8380468/compojure-regex-for-matching-a-trailing-slash"
  [handler]
  (let [modify #(if
                  (and (not (= "/" %)) (.endsWith % "/"))
                  (subs % 0 (dec (count %)))
                  %)]
    #(handler (assoc % :uri (modify (:uri %))))))

(defn wrap-limit-offset
  [handler]
  (fn
    [req]
    (let [page {:limit (or (get-in req [:query-params "limit"]) "10")
                :offset (or (get-in req [:query-params "offset"]) "0")}]
      (handler (assoc-in req [:params :page] page)))))

; Our actual handler

(def api-handler
  (->
    api-routes
    ignore-trailing-slash
    wrap-limit-offset
    api))