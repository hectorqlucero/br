(ns br.hooks.propiedades
  (:require [br.models.crud :as crud]
            [clojure.string :as str]))

;; =============================================================================
;; BEFORE-LOAD: Cargar opciones para selects
;; =============================================================================

(defn cargar-opciones
  "Carga dinámicamente las opciones para los campos select"
  [params]
  ;; Este hook puede modificar params si necesita filtrar
  ;; Por ahora solo retorna params sin cambios
  params)

;; =============================================================================
;; BEFORE-SAVE: Validaciones
;; =============================================================================

(defn validar-propiedad
  "Valida reglas de negocio antes de guardar (normaliza precios y m2)"
  [params]
  (let [operacion (:operacion params)
        parse-num (fn [v]
                    (cond
                      (nil? v) nil
                      (and (string? v) (clojure.string/blank? v)) nil
                      (number? v) v
                      :else (try (Double/parseDouble (str v)) (catch Exception _ nil))))

        precio-venta (parse-num (:precio_venta params))
        precio-renta (parse-num (:precio_renta params))
        terreno-m2 (parse-num (:terreno_m2 params))
        construccion-m2 (parse-num (:construccion_m2 params))

        params (-> params
                   (assoc :precio_venta precio-venta)
                   (assoc :precio_renta precio-renta)
                   (assoc :terreno_m2 terreno-m2)
                   (assoc :construccion_m2 construccion-m2))]
    (let [result (cond
                   (and (= operacion "Venta") (or (nil? precio-venta) (<= precio-venta 0)))
                   {:errors {:precio_venta "Debe especificar precio de venta"}}

                   (and (= operacion "Renta") (or (nil? precio-renta) (<= precio-renta 0)))
                   {:errors {:precio_renta "Debe especificar precio de renta mensual"}}

                   (and (= operacion "Ambos")
                        (or (nil? precio-venta) (<= precio-venta 0)
                            (nil? precio-renta) (<= precio-renta 0)))
                   {:errors {:general "Debe especificar precio de venta Y renta"}}

                   (and (some? construccion-m2) (some? terreno-m2) (> construccion-m2 terreno-m2))
                   {:errors {:construccion_m2 "No puede ser mayor que el terreno"}}

                   :else params)]
      result)))

;; =============================================================================
;; AFTER-SAVE: Generar clave única
;; =============================================================================

(defn generar-clave
  "Genera clave única para la propiedad si no tiene.
   Accepts either (id params) or (params) for compatibility."
  [entity-id-or-params params-or-nil]
  (let [[entity-id params] (if (map? entity-id-or-params)
                             [(:id entity-id-or-params) entity-id-or-params]
                             [entity-id-or-params params-or-nil])]
    (when (and entity-id (not (:clave params)))
      (let [tipo-id (:tipo_id params)
            estado-id (:estado_id params)
            tipo-row (when tipo-id (first (crud/Query ["SELECT nombre FROM tipos_propiedad WHERE id = ?" tipo-id])))
            tipo-n (or (:nombre tipo-row) "")
            tipo-abrev (-> tipo-n str/upper-case (subs 0 (min 3 (count tipo-n))))
            estado-row (when estado-id (first (crud/Query ["SELECT clave FROM estados WHERE id = ?" estado-id])))
            estado-abrev (or (:clave estado-row) "")
            clave (format "%s-%s-%05d" tipo-abrev estado-abrev entity-id)]
        (try
          (when (and (seq tipo-abrev) (seq estado-abrev) (pos? (long entity-id)))
            (crud/Query! ["UPDATE propiedades SET clave = ? WHERE id = ?" clave entity-id]))
          (catch Exception e
            (try (println "[ERROR] generar-clave db update failed:" (.getMessage e)) (catch Throwable _))))))
    {:success true :message "Propiedad guardada exitosamente"}))

;; =============================================================================
;; BEFORE-DELETE: Verificar transacciones
;; =============================================================================

(defn verificar-transacciones
  "No permite borrar propiedad con ventas o rentas"
  [entity-id]
  (let [ventas (crud/Query ["SELECT COUNT(*) as cnt FROM ventas WHERE propiedad_id = ?" entity-id])
        rentas (crud/Query ["SELECT COUNT(*) as cnt FROM rentas WHERE propiedad_id = ?" entity-id])
        cnt-ventas (get-in ventas [0 :cnt] 0)
        cnt-rentas (get-in rentas [0 :cnt] 0)]

    (cond
      (> cnt-ventas 0)
      {:errors {:general "No se puede eliminar: tiene ventas registradas"}}

      (> cnt-rentas 0)
      {:errors {:general "No se puede eliminar: tiene rentas registradas"}}

      :else
      {:success true})))
