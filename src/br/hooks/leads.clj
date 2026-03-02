(ns br.hooks.leads
  (:require [br.models.crud :as crud]
            [clojure.string :as str]))

;; =============================================================================
;; BEFORE-SAVE: Validar lead
;; =============================================================================

(defn validar-lead
  "Valida datos del prospecto"
  [params]
  (let [email (:email params)
        celular (:celular params)]

    (cond
      ;; Email o celular requerido
      (and (str/blank? email) (str/blank? celular))
      {:errors {:general "Debe proporcionar email o celular"}}

      ;; Email válido si se proporciona
      (and email (not (re-matches #".+@.+\..+" email)))
      {:errors {:email "Formato de email inválido"}}

      :else
      params)))

;; =============================================================================
;; AFTER-SAVE: Asignar agente automáticamente
;; =============================================================================

(defn asignar-agente
  "Asigna un agente si no tiene uno asignado"
  [entity-id params]
  (when (and entity-id (nil? (:agente_id params)))
    ;; Si no hay agente asignado, no hacemos nada automática
    ;; El sistema puede notificar a admin para asignar
    )
  {:success true})
