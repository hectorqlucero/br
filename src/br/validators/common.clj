(ns br.validators.common
  (:require [clojure.string :as str]))

;; =============================================================================
;; RFC (Registro Federal de Contribuyentes)
;; =============================================================================

(defn rfc-valido?
  "Valida formato de RFC mexicano (12 o 13 caracteres)"
  [value _]
  (when value
    (let [rfc (str/upper-case (str/trim (str value)))]
      (and (<= 12 (count rfc) 13)
           (re-matches #"[A-Z]{3,4}\d{6}[A-Z0-9]{2,3}" rfc)))))

;; =============================================================================
;; CURP (Clave Única de Registro de Población)
;; =============================================================================

(defn curp-valida?
  "Valida formato de CURP (18 caracteres)"
  [value _]
  (when value
    (let [curp (str/upper-case (str/trim (str value)))]
      (and (= 18 (count curp))
           (re-matches #"[A-Z]{4}\d{6}[HM][A-Z]{5}[A-Z0-9]\d" curp)))))

;; =============================================================================
;; Código Postal
;; =============================================================================

(defn codigo-postal-valido?
  "Valida CP de México (5 dígitos)"
  [value _]
  (when value
    (re-matches #"\d{5}" (str value))))

;; =============================================================================
;; Teléfono/Celular
;; =============================================================================

(defn telefono-valido?
  "Valida formato de teléfono mexicano"
  [value _]
  (when value
    (let [tel (str/replace (str value) #"[-\s()]" "")]
      (and (<= 10 (count tel) 15)
           (re-matches #"\d+" tel)))))

;; =============================================================================
;; Precio/Monto
;; =============================================================================

(defn precio-positivo?
  "Valida que el precio sea positivo"
  [value _]
  (and value (> value 0)))

(defn precio-razonable?
  "Valida rango razonable para precios inmobiliarios"
  [value _]
  (and value
       (>= value 10000) ; Mínimo $10,000 MXN
       (<= value 1000000000))) ; Máximo $1,000 millones

;; =============================================================================
;; Porcentaje
;; =============================================================================

(defn porcentaje-valido?
  "Valida que esté entre 0 y 100"
  [value _]
  (and value (>= value 0) (<= value 100)))

;; =============================================================================
;; Metros Cuadrados
;; =============================================================================

(defn m2-valido?
  "Valida metros cuadrados razonables"
  [value _]
  (and value
       (>= value 1) ; Mínimo 1 m²
       (<= value 100000))) ; Máximo 100,000 m² (10 hectáreas)
