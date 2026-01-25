(ns br.handlers.home.model
  (:require
   [br.models.crud :refer [db Query Update]]
   [clojure.string :as st]))

(defn get-user
  [username]
  (Query db ["SELECT * FROM users WHERE username=?" username]))

(defn get-users
  []
  (Query db ["SELECT * FROM users"]))

(defn update-password
  [username password]
  (let [where-clause ["username = ?" username]
        result (first (Update db :users {:password password} where-clause))]
    (Integer. result)))

;; Properties and photos helpers
(defn get-photos-by-property
  "Return vector of photos for a property ordered by :orden"
  [prop-id]
  (Query db ["SELECT id, url, descripcion, es_principal, orden FROM fotos_propiedad WHERE propiedad_id = ? ORDER BY orden ASC" prop-id]))

(defn get-estados
  "Return vector of estados that have at least one available property (activo='T' AND status='Disponible')."
  []
  (Query db ["SELECT DISTINCT e.id, e.nombre FROM estados e JOIN propiedades p ON p.estado_id = e.id WHERE p.activo = 'T' AND p.status = 'Disponible' ORDER BY e.nombre ASC"]))

(defn get-municipios-by-estado
  "Return vector of municipios for a given estado id that have at least one available property."
  [estado-id]
  (if estado-id
    (Query db ["SELECT DISTINCT m.id, m.nombre FROM municipios m JOIN propiedades p ON p.municipio_id = m.id WHERE m.estado_id = ? AND p.activo = 'T' AND p.status = 'Disponible' ORDER BY m.nombre ASC" estado-id])
    []))

(defn get-featured-properties
  "Return featured properties with primary photo attached.
   Usage: (get-featured-properties nil) -> no limit
          (get-featured-properties limit {:estado-id 1 :municipio-id 2}) -> filtered"
  ([] (get-featured-properties nil {}))
  ([limit] (get-featured-properties limit {}))
  ([limit filters]
   (let [base-sql "SELECT p.id, p.titulo, p.descripcion, p.calle, p.numero_exterior, p.colonia_id, p.codigo_postal, p.precio_venta, p.precio_renta, p.moneda, p.operacion, p.recamaras, p.banos_completos, p.construccion_m2, p.terreno_m2, p.destacada, p.operacion, p.status, p.fecha_registro, tp.nombre as tipo_nombre, e.nombre as estado_nombre, m.nombre as municipio_nombre, co.nombre as colonia_nombre, concat(a.nombre, ' ', a.apellido_paterno, ' ', a.apellido_materno) as agente_nombre, a.telefono as agente_telefono, a.celular as agente_celular, a.email as agente_email FROM propiedades p LEFT JOIN tipos_propiedad tp ON p.tipo_id = tp.id LEFT JOIN estados e ON p.estado_id = e.id LEFT JOIN municipios m ON p.municipio_id = m.id LEFT JOIN colonias co ON p.colonia_id = co.id LEFT JOIN agentes a ON p.agente_id = a.id WHERE p.activo = 'T' AND p.status = 'Disponible'"
         estado-id (:estado-id filters)
         municipio-id (:municipio-id filters)
         conds (cond-> []
                 estado-id (conj "AND p.estado_id = ?")
                 municipio-id (conj "AND p.municipio_id = ?"))
         params (cond-> []
                  estado-id (conj estado-id)
                  municipio-id (conj municipio-id))
         sql (str base-sql (when (seq conds) (str " " (st/join " " conds))) " ORDER BY p.destacada DESC, p.fecha_registro DESC" (when limit " LIMIT ?"))
         qparams (if limit (into [sql] (conj params limit)) (into [sql] params))
         rows (Query db qparams)]
     (map (fn [r]
            (let [photos (get-photos-by-property (:id r))]
              (assoc r :photos (vec photos)
                     :foto_url (when (seq photos) (:url (first photos))))))
          rows))))

(defn get-property-by-id
  "Fetch a single property by id and include its photos"
  [id]
  (let [row (first (Query db ["SELECT p.*, tp.nombre as tipo_nombre, e.nombre as estado_nombre, m.nombre as municipio_nombre, co.nombre as colonia_nombre, concat(a.nombre, ' ', a.apellido_paterno, ' ', a.apellido_materno) as agente_nombre, a.telefono as agente_telefono, a.celular as agente_celular, a.email as agente_email FROM propiedades p LEFT JOIN tipos_propiedad tp ON p.tipo_id = tp.id LEFT JOIN estados e ON p.estado_id = e.id LEFT JOIN municipios m ON p.municipio_id = m.id LEFT JOIN colonias co ON p.colonia_id = co.id LEFT JOIN agentes a ON p.agente_id = a.id WHERE p.id = ?" id]))]
    (when row
      (let [photos (get-photos-by-property (:id row))]
        (assoc row :photos (vec photos) :foto_url (when (seq photos) (:url (first photos))))))))
