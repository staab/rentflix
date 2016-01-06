(ns rentflix.server
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [ring.middleware.stacktrace :as trace]
            [ring.middleware.params :as p]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]))

(defroutes api
  (GET "/" []
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body "Hello World"})
  (ANY "*" []
       (route/not-found "Page Not Found")))