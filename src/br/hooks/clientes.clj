(ns br.hooks.clientes
  (:require [clojure.string :as str]))

;; =============================================================================
;; AFTER-LOAD: Agregar nombre completo
;; =============================================================================

(defn nombre-completo
  "Agrega campo computado con nombre completo"
  [rows params]
  (mapv (fn [row]
          (let [nombre (str/trim (str (:nombre row) " "
                                      (:apellido_paterno row) " "
                                      (:apellido_materno row)))]
            (assoc row :nombre_completo nombre)))
        rows))

;; =============================================================================
;; BEFORE-SAVE: Validaciones
;; =============================================================================

(defn validar-cliente
  "Valida datos del cliente"
  [params]
  (let [email (:email params)
        celular (:celular params)
        rfc (:rfc params)]

    (cond
      ;; Email debe ser único si se proporciona
      (and email (not (re-matches #".+@.+\..+" email)))
      {:errors {:email "Formato de email inválido"}}

      ;; RFC debe tener 12 o 13 caracteres
      (and rfc (not (<= 12 (count rfc) 13)))
      {:errors {:rfc "RFC debe tener 12 o 13 caracteres"}}

      ;; RFC solo letras y números
      (and rfc (not (re-matches #"[A-Z0-9]+" (str/upper-case rfc))))
      {:errors {:rfc "RFC solo debe contener letras y números"}}

      :else
      (-> params
          (update :rfc #(when % (str/upper-case %)))
          (update :curp #(when % (str/upper-case %)))))))
