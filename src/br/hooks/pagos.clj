(ns br.hooks.pagos
  (:require [br.models.crud :as crud]))

;; =============================================================================
;; AFTER-SAVE: Generar clave única
;; =============================================================================

(defn after-save
  [data save-result]
  (when save-result
    (let [tipo (:tipo data)
          properties-id (Integer. (:referencia_id data))
          result (cond
                   (= tipo "Renta") (crud/Update crud/db :propiedades {:status "Rentada"} ["id = ?" properties-id])
                   (= tipo "Venta") (crud/Update crud/db :propiedades {:status "Vendida"} ["id = ?" properties-id])
                   :else false)]
      (or result
          {:success true}
          {:errors {:general "No se actualizo propiedades/status!"}}))))
