(ns rentflix.server
  (:use (compojure [handler :only (api)]
                   [route :only (not-found)]
                   [core :only (GET POST ANY defroutes)])
        (clojure [pprint :only (pprint)])))

; Specific route handlers

(defn api-root
  [] "This is the root")

(defn api-list
  [model]
  (str "this is the list endpoint for " model))

(defn api-detail
  [model id]
  (str "This is the detail endpoint for " model " with id " id))

; Defroutes macro to define top level macro
; http://stackoverflow.com/a/3490479/1467342
; https://github.com/weavejester/compojure/wiki/Routes-In-Detail

(defroutes api-routes
  (GET "/" [] (api-root))
  (GET "/:model" [model] (api-list model))
  (GET "/:model/:id" [model id] (api-detail model id))
  (ANY "*" [] (not-found "Page Not Found")))

(defonce api-handler (api api-routes))