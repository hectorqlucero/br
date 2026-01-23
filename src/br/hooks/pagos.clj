(ns br.hooks.pagos
  (:require [br.models.crud :as crud]))

;; =============================================================================
;; AFTER-SAVE: Acutalizar el estatus de propiedades
;; =============================================================================

(defn after-save
  [data save-result]
  (when save-result
    (let [tipo (:tipo data)
          propiedades-id (Integer. (:referencia_id data))
          result (cond
                   (= tipo "Renta") (crud/Update crud/db :propiedades {:status "Rentada"} ["id = ?" propiedades-id])
                   (= tipo "Venta") (crud/Update crud/db :propiedades {:status "Vendida"} ["id = ?" propiedades-id])
                   :else false)]
      (or result
          {:success true}
          {:errors {:general "No se actualizo propiedades/status!"}}))))
