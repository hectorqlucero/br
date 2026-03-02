(ns br.hooks.contratos
  (:require [br.models.crud :as crud]
            [clojure.string :as str]))

;; =============================================================================
;; BEFORE-SAVE: Validar contrato
;; =============================================================================

(defn validar-contrato
  "Valida datos del contrato"
  [params]
  (let [tipo (:tipo_contrato params)
        fecha-inicio (:fecha_inicio params)
        fecha-fin (:fecha_fin params)]

    (cond
      ;; Para arrendar, fechas requeridas
      (and (= tipo "Arrendamiento") (or (str/blank? fecha-inicio) (str/blank? fecha-fin)))
      {:errors {:general "Para arrendamiento, las fechas de inicio y fin son requeridas"}}

      ;; Fecha fin debe ser mayor a inicio
      (and fecha-inicio fecha-fin
           (try (not (.isBefore (java.time.LocalDate/parse fecha-inicio) (java.time.LocalDate/parse fecha-fin))) (catch Exception _ false)))
      {:errors {:fecha_fin "La fecha fin debe ser mayor a la fecha de inicio"}}

      :else
      params)))
