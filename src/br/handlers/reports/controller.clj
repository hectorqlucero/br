(ns br.handlers.reports.controller
  (:require
   [br.handlers.reports.model :as model]
   [br.handlers.reports.view :as view]
   [br.layout :refer [application]]
   [br.models.util :as util]))

(defn users
  [request]
  (let [title "Reporte de Usuarios"
        ok (util/get-session-id request)
        js nil
        rows (model/get-users)
        content (view/users-report request title rows)]
    (application request title ok js content)))

(defn propiedades
  [request]
  (let [title "Reporte de Propiedades"
        ok (util/get-session-id request)
        js nil
        rows (model/get-propiedades)
        content (view/propiedades-report request title rows)]
    (application request title ok js content)))

(defn ventas
  [request]
  (let [title "Reporte de Ventas"
        ok (util/get-session-id request)
        js nil
        rows (model/get-ventas)
        content (view/ventas-report request title rows)]
    (application request title ok js content)))

(defn rentas
  [request]
  (let [title "Reporte de Rentas"
        ok (util/get-session-id request)
        js nil
        rows (model/get-rentas)
        content (view/rentas-report request title rows)]
    (application request title ok js content)))

(defn clientes
  [request]
  (let [title "Reporte de Clientes"
        ok (util/get-session-id request)
        js nil
        rows (model/get-clientes)
        content (view/clientes-report request title rows)]
    (application request title ok js content)))

(defn agentes
  [request]
  (let [title "Reporte de Agentes"
        ok (util/get-session-id request)
        js nil
        rows (model/get-agentes)
        content (view/agentes-report request title rows)]
    (application request title ok js content)))

(defn leads
  [request]
  (let [title "Reporte de Leads"
        ok (util/get-session-id request)
        js nil
        rows (model/get-leads)
        content (view/leads-report request title rows)]
    (application request title ok js content)))

(defn comisiones
  [request]
  (let [title "Reporte de Comisiones"
        ok (util/get-session-id request)
        js nil
        rows (model/get-comisiones)
        content (view/comisiones-report request title rows)]
    (application request title ok js content)))

(defn citas
  [request]
  (let [title "Reporte de Citas"
        ok (util/get-session-id request)
        js nil
        rows (model/get-citas)
        content (view/citas-report request title rows)]
    (application request title ok js content)))

(defn pagos
  [request]
  (let [title "Reporte de Pagos"
        ok (util/get-session-id request)
        js nil
        rows (model/get-pagos)
        content (view/pagos-report request title rows)]
    (application request title ok js content)))
