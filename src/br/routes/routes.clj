(ns br.routes.routes
  (:require
   [compojure.core :refer [defroutes GET POST]]
   [br.handlers.home.controller :as home-controller]))

(defroutes open-routes
  (GET "/" req [] (home-controller/main req))
  (GET "/home/login" req [] (home-controller/login req))
  (POST "/home/login" req [] (home-controller/login-user req))
  (GET "/home/logoff" req [] (home-controller/logoff-user req))
  (GET "/change/password" req [] (home-controller/change-password req))
  (POST "/change/password" req [] (home-controller/process-password req))
  (GET "/property/:id" req (home-controller/property-detail req))
  (GET "/api/municipios" req [] (home-controller/municipios-api req)))
