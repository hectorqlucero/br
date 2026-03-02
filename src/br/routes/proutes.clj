(ns br.routes.proutes
  (:require
   [compojure.core :refer [defroutes GET]]
   [br.handlers.reports.controller :as reports]))

;; All CRUD routes now handled by parameter-driven engine
;; Add custom non-CRUD routes here if needed

(defroutes proutes
  ;; Reportes
  (GET "/reports/users" req [] (reports/users req))
  (GET "/reports/propiedades" req [] (reports/propiedades req))
  (GET "/reports/ventas" req [] (reports/ventas req))
  (GET "/reports/rentas" req [] (reports/rentas req))
  (GET "/reports/clientes" req [] (reports/clientes req))
  (GET "/reports/agentes" req [] (reports/agentes req))
  (GET "/reports/leads" req [] (reports/leads req))
  (GET "/reports/comisiones" req [] (reports/comisiones req))
  (GET "/reports/citas" req [] (reports/citas req))
  (GET "/reports/pagos" req [] (reports/pagos req)))
