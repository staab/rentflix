(ns rentflix.server
  (:use (compojure [handler :only (api)]
                   [route :only (not-found)]
                   [core :only (GET POST ANY defroutes)])))

(defn api-root [request] "This is the root")
(defn api-list [request] "This is the list endpoint")
(defn api-detail [request] "This is the detail endpoint")

(defroutes api-routes
  (GET "/" [request] (api-root request))
  (GET "/:model" [request] (api-list request))
  (GET "/:model/:id" [request] (api-detail request))
  (ANY "*" [] (not-found "Page Not Found")))

(defonce api-handler (api api-routes))