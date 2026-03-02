(ns br.hooks.comisiones
  (:require [br.models.crud :as crud]
            [clojure.string :as str]))

;; =============================================================================
;; BEFORE-SAVE: Calcular comisión
;; =============================================================================

(defn calcular-comision
  "Calcula la comisión automáticamente"
  [params]
  (let [tipo (:tipo params)
        monto-venta (:monto_venta params)
        porcentaje (:porcentaje_comision params)
        agente-id (:agente_id params)

        parse-num (fn [v]
                    (cond
                      (nil? v) nil
                      (number? v) v
                      (string? v) (try (Double/parseDouble v) (catch Exception _ nil))
                      :else nil))

        venta (parse-num monto-venta)
        pct (parse-num porcentaje)]

    (cond
      ;; Si es venta, calcular comisión
      (and (= tipo "Venta") venta pct)
      (let [monto-comision (* venta (/ pct 100.0))]
        (assoc params :monto_comision monto-comision))

      :else
      params)))

;; =============================================================================
;; AFTER-SAVE: Actualizar estatus de venta
;; =============================================================================

(defn actualizar-estatus-venta
  "Cuando se paga la comisión, actualizar la venta"
  [entity-id params]
  (when (and entity-id (= (:status params) "Pagada") (:venta_id params))
    (try
      ;; Marcar comisión como pagada en la venta
      (crud/Query! ["UPDATE ventas SET comision_pagada = 'T' WHERE id = ?" (:venta_id params)])
      (catch Exception e
        (println "[WARN] Error actualizando estatus de venta:" (.getMessage e)))))
  {:success true})
