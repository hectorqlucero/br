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
  "Valida reglas de negocio antes de guardar"
  [params]
  (let [operacion (:operacion params)
        precio-venta (:precio_venta params)
        precio-renta (:precio_renta params)]

    (cond
      ;; Si operación es Venta, debe tener precio de venta
      (and (= operacion "Venta") (or (nil? precio-venta) (<= precio-venta 0)))
      {:errors {:precio_venta "Debe especificar precio de venta"}}

      ;; Si operación es Renta, debe tener precio de renta
      (and (= operacion "Renta") (or (nil? precio-renta) (<= precio-renta 0)))
      {:errors {:precio_renta "Debe especificar precio de renta mensual"}}

      ;; Si operación es Ambos, debe tener ambos precios
      (and (= operacion "Ambos")
           (or (nil? precio-venta) (<= precio-venta 0)
               (nil? precio-renta) (<= precio-renta 0)))
      {:errors {:general "Debe especificar precio de venta Y renta"}}

      ;; Construcción no puede ser mayor que terreno
      (and (:construccion_m2 params) (:terreno_m2 params)
           (> (:construccion_m2 params) (:terreno_m2 params)))
      {:errors {:construccion_m2 "No puede ser mayor que el terreno"}}

      ;; Todo OK
      :else params)))

;; =============================================================================
;; AFTER-SAVE: Generar clave única
;; =============================================================================

(defn generar-clave
  "Genera clave única para la propiedad si no tiene"
  [entity-id params]
  (when-not (:clave params)
    (let [tipo-id (:tipo_id params)
          estado-id (:estado_id params)
          ;; Obtener abreviatura del tipo y estado
          tipo-abrev (-> (crud/Query ["SELECT nombre FROM tipos_propiedad WHERE id = ?" tipo-id])
                         first
                         :nombre
                         (str/upper-case)
                         (subs 0 3))
          estado-abrev (-> (crud/Query ["SELECT clave FROM estados WHERE id = ?" estado-id])
                           first
                           :clave)
          ;; Generar clave: TIPO-ESTADO-ID (ej: CAS-JA-00123)
          clave (format "%s-%s-%05d" tipo-abrev estado-abrev entity-id)]

      ;; Actualizar la clave
      (crud/Query! ["UPDATE propiedades SET clave = ? WHERE id = ?" clave entity-id])))

  {:success true :message "Propiedad guardada exitosamente"})

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
