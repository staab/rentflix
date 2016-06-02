(ns rentflix.server
  (:require [argo.core :as argo]
            [clojure.data.json :as json]
            [clojure.walk :refer [keywordize-keys]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.handler :refer [api]]
            [compojure.route :refer [not-found]]
            [datomic.api :as d]
            [rentflix.model :as model]
            [rentflix.db :as db]))

(def base-url "/api/v1")
(def model-url (str base-url "/model"))
(def query-url (str base-url "/query"))

; Resource functions

(defn resource-find
  "Find a resource given the request object"
  [type req]
  (let [limit (get-in req [:page :limit])
        offset (get-in req [:page :offset])]
    {:data (model/find-by-type type limit offset)}))

(defn resource-get
  "Get a single resource given the request object"
  [type req]
  (let [id (Long/parseLong (get-in req [:params :id]))]
    {:data (model/get-by-id id type)}))

; Resources

(argo/defresource title {:find (partial resource-find :type/title)
                         :get (partial resource-get :type/title)})

(argo/defapi v1-model {:resources [title] :base-url model-url})

; Routes

(defroutes api-routes
  (GET model-url [] "This is the api root")
  (GET (str model-url "*") req (v1-model req))
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