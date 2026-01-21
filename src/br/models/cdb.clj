(ns br.models.cdb
  (:require
   [clojure.string :as st]
   [buddy.hashers :as hashers]
   [br.models.crud :as crud :refer [Insert-multi Query!]]))

(def users-rows
  [{:lastname  "User"
    :firstname "Regular"
    :username  "user@example.com"
    :password  (hashers/derive "user")
    :dob       "1957-02-07"
    :email     "user@example.com"
    :level     "U"
    :active    "T"}
   {:lastname "User"
    :firstname "Admin"
    :username "admin@example.com"
    :password (hashers/derive "admin")
    :dob "1957-02-07"
    :email "admin@example.com"
    :level "A"
    :active "T"}
   {:lastname "User"
    :firstname "System"
    :username "system@example.com"
    :password (hashers/derive "system")
    :dob "1957-02-07"
    :email "system@example.com"
    :level "S"
    :active "T"}])

(defn- normalize-token [s]
  (some-> s str st/trim (st/replace #"^:+" "") st/lower-case))

(def ^:private vendor->subprotocol
  {"mysql"     #(or (= % "mysql") (= % :mysql))
   "postgres"  #(or (= % "postgresql") (= % :postgresql) (= % "postgres") (= % :postgres))
   "postgresql" #(or (= % "postgresql") (= % :postgresql) (= % "postgres") (= % :postgres))
   "pg"        #(or (= % "postgresql") (= % :postgresql) (= % "postgres") (= % :postgres))
   "sqlite"    #(or (= % "sqlite") (= % :sqlite) (= % "sqlite3") (= % :sqlite3))
   "sqlite3"   #(or (= % "sqlite") (= % :sqlite) (= % "sqlite3") (= % :sqlite3))})

(defn- choose-conn-key
  "Resolve a user token (e.g., nil, pg, :pg, localdb, mysql) to a key in crud/dbs.
  Prefers exact connection keys (e.g., :pg, :localdb, :main, :default). Falls back to
  the first connection whose subprotocol matches a known vendor token. Defaults to :default."
  [token]
  (let [t (normalize-token token)
        dbs crud/dbs
        keys* (set (keys dbs))
        ;; map some common nicknames directly to configured keys
        t->key {"default" :default
                "mysql"   :default   ; assume default is mysql per config
                "main"    :main
                "pg"      :pg
                "postgres" :pg
                "postgresql" :pg
                "local"   :localdb
                "localdb" :localdb
                "sqlite"  :localdb
                "sqlite3" :localdb}
        direct (when (seq t)
                 (some (fn [k] (when (= (name k) t) k)) keys*))
        mapped (get t->key t)
        by-vendor (when (seq t)
                    (let [pred (get vendor->subprotocol t)]
                      (when pred
                        (some (fn [[k v]] (when (pred (:subprotocol v)) k)) dbs))))]
    (or direct mapped by-vendor :default)))

(defn populate-tables
  "Populate a table with rows on the selected connection. This version avoids vendor-specific
  locking and uses simple DELETE + batch insert wrapped in a transaction by Insert-multi."
  [table rows & {:keys [conn]}]
  (let [conn* (or conn :default)
        table-s (name (keyword table))
        ;; coerce row values to DB-appropriate types using schema introspection
        typed-rows (mapv (fn [row]
                           (crud/build-postvars table-s row :conn conn*))
                         rows)]
    (println (format "[database] Seeding %s on connection %s" table-s (name conn*)))
    (try
      ;; Clear existing rows (portable across MySQL/Postgres/SQLite)
      (Query! (str "DELETE FROM " table-s) :conn conn*)
      ;; Batch insert rows
      (Insert-multi (keyword table-s) typed-rows :conn conn*)
      (println (format "[database] Seeded %d rows into %s (%s)"
                       (count typed-rows) table-s (name conn*)))
      (catch Exception e
        (println "[ERROR] Seeding failed for" table-s "on" (name conn*) ":" (.getMessage e))
        (throw e)))))

(defn database
  "Usage:
   - lein database                 ; seeds default (mysql per config)
   - lein database pg              ; seeds Postgres (:pg)
   - lein database :pg             ; same as above
   - lein database localdb         ; seeds SQLite (:localdb)"
  [& args]
  (let [token (first args)
        conn  (choose-conn-key token)
        dbspec (get crud/dbs conn)
        sp (:subprotocol dbspec)]
    (println (format "[database] Using connection: %s (subprotocol=%s)" (name conn) sp))
    ;; add other tables here if needed
    (populate-tables "users" users-rows :conn conn)
    (println "[database] Done.")))

;; -----------------------------------------------------------------------------
;; Idempotent, date-centric seeds for migration tables (non-user data)
;; Call with (seed-migration-tables! :localdb) or (seed-migration-tables!)
;; -----------------------------------------------------------------------------

(defn- fmt-datetime
  "Format a LocalDate plus days and an hour/minute into 'yyyy-MM-dd HH:mm:ss'."
  [days hour minute]
  (let [today (java.time.LocalDate/now)
        dt (.atTime (.plusDays today days) hour minute)
        fmt (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")]
    (.format dt fmt)))

(defn- date-str
  "Return yyyy-MM-dd for today + days"
  [days]
  (let [today (java.time.LocalDate/now)]
    (.toString (.plusDays today days))))

(defn- insert-and-find-id
  "Insert a row into table and return the inserted row id by querying a unique column.
   Expects unique-col as a keyword (column name). Safe to call multiple times (idempotent)."
  [table row unique-col & {:keys [conn]}]
  (let [tbl-kw (keyword table)
        val (get row unique-col)
        q (str "SELECT id FROM " table " WHERE " (name unique-col) " = ? LIMIT 1")]
    (try
      (crud/Insert tbl-kw row :conn conn)
      (catch Exception e
        (println (format "[WARN] insert into %s failed: %s" table (.getMessage e)))))
    (let [res (try (first (crud/Query [q val] :conn conn)) (catch Exception e (do (println (format "[WARN] select id from %s failed: %s" table (.getMessage e))) nil)))]
      (when-not res
        (println (format "[WARN] no id found for %s where %s=%s" table (name unique-col) val)))
      (when res
        (:id res)))))

(defn clear-migration-tables!
  "Delete all rows from migration tables in FK-safe reverse order."
  [conn]
  (let [tables ["documentos" "avaluos" "pagos" "citas" "rentas" "ventas" "fotos_propiedad" "propiedades" "clientes" "agentes" "tipos_propiedad" "colonias" "municipios" "estados"]]
    (doseq [t tables]
      (println "Clearing" t)
      (Query! (str "DELETE FROM " t) :conn conn))))

(defn seed-estados! [conn counts]
  (println "Inserting estados...")
  (let [estados-data [{:clave "AG" :nombre "Aguascalientes" :activo "T"}
                      {:clave "BC" :nombre "Baja California" :activo "T"}
                      {:clave "BS" :nombre "Baja California Sur" :activo "T"}
                      {:clave "CM" :nombre "Campeche" :activo "T"}
                      {:clave "CS" :nombre "Chiapas" :activo "T"}
                      {:clave "CH" :nombre "Chihuahua" :activo "T"}
                      {:clave "CX" :nombre "Ciudad de México" :activo "T"}
                      {:clave "CO" :nombre "Coahuila" :activo "T"}
                      {:clave "CL" :nombre "Colima" :activo "T"}
                      {:clave "DG" :nombre "Durango" :activo "T"}
                      {:clave "GT" :nombre "Guanajuato" :activo "T"}
                      {:clave "GR" :nombre "Guerrero" :activo "T"}
                      {:clave "HG" :nombre "Hidalgo" :activo "T"}
                      {:clave "JA" :nombre "Jalisco" :activo "T"}
                      {:clave "EM" :nombre "Estado de México" :activo "T"}
                      {:clave "MI" :nombre "Michoacán" :activo "T"}
                      {:clave "MO" :nombre "Morelos" :activo "T"}
                      {:clave "NA" :nombre "Nayarit" :activo "T"}
                      {:clave "NL" :nombre "Nuevo León" :activo "T"}
                      {:clave "OA" :nombre "Oaxaca" :activo "T"}
                      {:clave "PU" :nombre "Puebla" :activo "T"}
                      {:clave "QT" :nombre "Querétaro" :activo "T"}
                      {:clave "QR" :nombre "Quintana Roo" :activo "T"}
                      {:clave "SL" :nombre "San Luis Potosí" :activo "T"}
                      {:clave "SI" :nombre "Sinaloa" :activo "T"}
                      {:clave "SO" :nombre "Sonora" :activo "T"}
                      {:clave "TB" :nombre "Tabasco" :activo "T"}
                      {:clave "TM" :nombre "Tamaulipas" :activo "T"}
                      {:clave "TL" :nombre "Tlaxcala" :activo "T"}
                      {:clave "VE" :nombre "Veracruz" :activo "T"}
                      {:clave "YU" :nombre "Yucatán" :activo "T"}
                      {:clave "ZA" :nombre "Zacatecas" :activo "T"}]
        ids (reduce (fn [m e]
                      (let [id (insert-and-find-id "estados" e :clave :conn conn)]
                        (when id (swap! counts update :estados (fnil inc 0)))
                        (if id (assoc m (:clave e) id) m)))
                    {}
                    estados-data)]
    (println "Inserted" (count ids) "estados")
    ids))

(defn seed-municipios! [conn counts estado-ids]
  (println "Inserting municipios...")
  (let [ja-id (get estado-ids "JA")
        municipios-data [{:estado_id ja-id :nombre "Guadalajara" :activo "T"}
                         {:estado_id ja-id :nombre "Zapopan" :activo "T"}
                         {:estado_id ja-id :nombre "Tlaquepaque" :activo "T"}
                         {:estado_id ja-id :nombre "Tonalá" :activo "T"}
                         {:estado_id ja-id :nombre "Puerto Vallarta" :activo "T"}]
        ids (reduce (fn [m mm]
                      (let [id (insert-and-find-id "municipios" mm :nombre :conn conn)]
                        (when id (swap! counts update :municipios (fnil inc 0)))
                        (if id (assoc m (:nombre mm) id) m)))
                    {}
                    municipios-data)]
    (println "Inserted" (count ids) "municipios")
    ids))

(defn seed-colonias! [conn counts municipio-ids]
  (println "Inserting colonias...")
  (let [colonias-data [{:municipio_id (municipio-ids "Guadalajara") :nombre "Colonia Americana" :codigo_postal "44160"}
                       {:municipio_id (municipio-ids "Guadalajara") :nombre "Providencia" :codigo_postal "44630"}
                       {:municipio_id (municipio-ids "Zapopan") :nombre "Ciudad del Sol" :codigo_postal "45050"}
                       {:municipio_id (municipio-ids "Zapopan") :nombre "Chapalita" :codigo_postal "45040"}
                       {:municipio_id (municipio-ids "Tlaquepaque") :nombre "Centro Tlaquepaque" :codigo_postal "45500"}
                       {:municipio_id (municipio-ids "Tonalá") :nombre "Loma Dorada" :codigo_postal "45410"}
                       {:municipio_id (municipio-ids "Puerto Vallarta") :nombre "5 de Diciembre" :codigo_postal "48350"}]
        ids (reduce (fn [m c]
                      (let [id (insert-and-find-id "colonias" c :nombre :conn conn)]
                        (when id (swap! counts update :colonias (fnil inc 0)))
                        (if id (assoc m (:nombre c) id) m)))
                    {}
                    colonias-data)]
    (println "Inserted" (count ids) "colonias")
    ids))

(defn seed-tipos! [conn counts]
  (println "Inserting tipos_propiedad...")
  (let [tipos-data [{:nombre "Casa" :descripcion "Casa habitación independiente" :activo "T"}
                    {:nombre "Departamento" :descripcion "Departamento o condominio" :activo "T"}
                    {:nombre "Terreno" :descripcion "Terreno baldío" :activo "T"}
                    {:nombre "Local Comercial" :descripcion "Local para negocio" :activo "T"}
                    {:nombre "Oficina" :descripcion "Oficina o espacio corporativo" :activo "T"}
                    {:nombre "Bodega" :descripcion "Bodega industrial o comercial" :activo "T"}
                    {:nombre "Rancho" :descripcion "Rancho o finca rural" :activo "T"}
                    {:nombre "Penthouse" :descripcion "Departamento de lujo en último piso" :activo "T"}]
        ids (reduce (fn [m t]
                      (let [id (insert-and-find-id "tipos_propiedad" t :nombre :conn conn)]
                        (when id (swap! counts update :tipos_propiedad (fnil inc 0)))
                        (if id (assoc m (:nombre t) id) m)))
                    {}
                    tipos-data)]
    (println "Inserted" (count ids) "tipos_propiedad")
    ids))

(defn seed-agentes! [conn counts]
  (println "Inserting agentes...")
  (let [agentes-data [{:nombre "Ana" :apellido_paterno "García" :apellido_materno "López" :email "ana.garcia@inmuebles.com" :telefono "3312345678" :celular "3312345678" :cedula_profesional "CP123456" :licencia_inmobiliaria "LIC-2026-01" :porcentaje_comision 3.00 :activo "T" :biografia "Especialista en propiedades residenciales."}
                      {:nombre "Carlos" :apellido_paterno "Martínez" :apellido_materno "Ruiz" :email "carlos.martinez@inmuebles.com" :telefono "3323456789" :celular "3323456789" :cedula_profesional "CP654321" :licencia_inmobiliaria "LIC-2026-02" :porcentaje_comision 3.00 :activo "T" :biografia "Experto en ventas y rentas comerciales."}]
        ids (reduce (fn [m a]
                      (let [id (insert-and-find-id "agentes" a :email :conn conn)]
                        (when id (swap! counts update :agentes (fnil inc 0)))
                        (if id (assoc m (:email a) id) m)))
                    {}
                    agentes-data)]
    (println "Inserted" (count ids) "agentes")
    ids))

(defn seed-clientes! [conn counts colonia-ids]
  (println "Inserting clientes...")
  (let [clientes-data [{:tipo "Comprador" :nombre "Luis" :apellido_paterno "Hernández" :apellido_materno "Soto" :email "luis.hernandez@gmail.com" :telefono "3334567890" :celular "3334567890" :rfc "HELS900101XXX" :curp "HELS900101HJCSTR09" :fecha_nacimiento (date-str -12000) :estado_civil "Soltero" :ocupacion "Ingeniero" :calle "Av. México" :numero_exterior "123" :colonia_id (colonia-ids "Colonia Americana") :codigo_postal "44160" :activo "T"}
                       {:tipo "Vendedor" :nombre "María" :apellido_paterno "Pérez" :apellido_materno "Gómez" :email "maria.perez@gmail.com" :telefono "3345678901" :celular "3345678901" :rfc "PEGG850202XXX" :curp "PEGG850202MJCSTR08" :fecha_nacimiento (date-str -14500) :estado_civil "Casada" :ocupacion "Contadora" :calle "Calle Libertad" :numero_exterior "456" :numero_interior "2" :colonia_id (colonia-ids "Providencia") :codigo_postal "44630" :activo "T"}
                       {:tipo "Arrendatario" :nombre "Jorge" :apellido_paterno "Ramírez" :apellido_materno "Díaz" :email "jorge.ramirez@gmail.com" :telefono "3356789012" :celular "3356789012" :rfc "RADI920303XXX" :curp "RADI920303HJCSTR07" :fecha_nacimiento (date-str -12000) :estado_civil "Soltero" :ocupacion "Diseñador" :calle "Calle Sol" :numero_exterior "789" :colonia_id (colonia-ids "Ciudad del Sol") :codigo_postal "45050" :activo "T"}]
        ids (reduce (fn [m c]
                      (let [id (insert-and-find-id "clientes" c :email :conn conn)]
                        (when id (swap! counts update :clientes (fnil inc 0)))
                        (if id (assoc m (:email c) id) m)))
                    {}
                    clientes-data)]
    (println "Inserted" (count ids) "clientes")
    ids))

(defn seed-propiedades! [conn counts tipo-ids colonia-ids municipio-ids estado-ids cliente-ids agente-ids]
  (println "Inserting propiedades...")
  (let [prop-rows [{:clave "CASA-001" :titulo "Casa en Providencia" :descripcion "Hermosa casa familiar en zona exclusiva." :tipo_id (tipo-ids "Casa") :calle "Calle Robles" :numero_exterior "321" :colonia_id (colonia-ids "Providencia") :municipio_id (municipio-ids "Guadalajara") :estado_id (estado-ids "JA") :codigo_postal "44630" :terreno_m2 250.00 :construccion_m2 180.00 :recamaras 3 :banos_completos 2 :medios_banos 1 :estacionamientos 2 :niveles 2 :antiguedad_anos 5 :operacion "Venta" :precio_venta 5500000.00 :moneda "MXN" :status "Disponible" :cliente_propietario_id (cliente-ids "maria.perez@gmail.com") :agente_id (agente-ids "ana.garcia@inmuebles.com") :activo "T" :destacada "T" :fecha_registro (date-str 0) :fecha_publicacion (date-str 4) :visitas 12}
                   {:clave "DEPTO-002" :titulo "Departamento en Ciudad del Sol" :descripcion "Departamento moderno, ideal para jóvenes profesionales." :tipo_id (tipo-ids "Departamento") :calle "Av. Patria" :numero_exterior "654" :colonia_id (colonia-ids "Ciudad del Sol") :municipio_id (municipio-ids "Zapopan") :estado_id (estado-ids "JA") :codigo_postal "45050" :terreno_m2 90.00 :construccion_m2 90.00 :recamaras 2 :banos_completos 1 :medios_banos 1 :estacionamientos 1 :niveles 1 :antiguedad_anos 2 :operacion "Renta" :precio_renta 18000.00 :moneda "MXN" :status "Disponible" :cliente_propietario_id (cliente-ids "luis.hernandez@gmail.com") :agente_id (agente-ids "carlos.martinez@inmuebles.com") :activo "T" :destacada "F" :fecha_registro (date-str 9) :fecha_publicacion (date-str 11) :visitas 8}]
        ids (reduce (fn [m p]
                      (let [id (insert-and-find-id "propiedades" p :clave :conn conn)]
                        (when id (swap! counts update :propiedades (fnil inc 0)))
                        (if id (assoc m (:clave p) id) m)))
                    {}
                    prop-rows)]
    (println "Inserted" (count ids) "propiedades")
    ids))

(defn seed-fotos! [conn counts propiedad-ids]
  (println "Inserting fotos_propiedad...")
  (doseq [f [{:propiedad_id (propiedad-ids "CASA-001") :url "https://example.com/fotos/casa-001-1.jpg" :descripcion "Fachada principal" :es_principal "T" :orden 1}
             {:propiedad_id (propiedad-ids "CASA-001") :url "https://example.com/fotos/casa-001-2.jpg" :descripcion "Sala" :es_principal "F" :orden 2}
             {:propiedad_id (propiedad-ids "DEPTO-002") :url "https://example.com/fotos/depto-002-1.jpg" :descripcion "Vista desde el balcón" :es_principal "T" :orden 1}]]
    (try
      (crud/Insert :fotos_propiedad f :conn conn)
      (swap! counts update :fotos_propiedad (fnil inc 0))
      (catch Exception e
        (println "[WARN] fotos_propiedad insert failed:" (.getMessage e))))))

(defn seed-ventas! [conn counts propiedad-ids cliente-ids agente-ids]
  (println "Inserting ventas...")
  (let [venta {:propiedad_id (propiedad-ids "CASA-001") :cliente_comprador_id (cliente-ids "luis.hernandez@gmail.com") :cliente_vendedor_id (cliente-ids "maria.perez@gmail.com") :agente_id (agente-ids "ana.garcia@inmuebles.com") :fecha_venta (date-str -6) :precio_venta 5500000.00 :enganche 500000.00 :financiamiento "T" :institucion_financiera "BBVA" :comision_total 165000.00 :comision_agente 49500.00 :status "En Proceso"}]
    (try
      (crud/Insert :ventas venta :conn conn)
      (swap! counts update :ventas (fnil inc 0))
      (catch Exception e
        (println "[WARN] ventas insert failed:" (.getMessage e))))))

(defn seed-rentas! [conn counts propiedad-ids cliente-ids agente-ids]
  (println "Inserting rentas...")
  (let [renta {:propiedad_id (propiedad-ids "DEPTO-002")
               :cliente_arrendatario_id (cliente-ids "jorge.ramirez@gmail.com")
               :cliente_arrendador_id (cliente-ids "luis.hernandez@gmail.com")
               :agente_id (agente-ids "carlos.martinez@inmuebles.com")
               :fecha_inicio (date-str -1)
               :fecha_fin (date-str 364)
               :renta_mensual 18000.00
               :deposito 18000.00
               :dia_pago 1
               :status "Activa"}]
    (try
      (crud/Insert :rentas renta :conn conn)
      (swap! counts update :rentas (fnil inc 0))
      (catch Exception e
        (println "[WARN] rentas insert failed:" (.getMessage e))))))

(defn seed-citas! [conn counts propiedad-ids cliente-ids agente-ids]
  (println "Inserting citas...")
  (let [citas-data [{:propiedad_id (propiedad-ids "CASA-001") :cliente_id (cliente-ids "luis.hernandez@gmail.com") :agente_id (agente-ids "ana.garcia@inmuebles.com") :fecha_cita (fmt-datetime -1 10 0) :duracion_minutos 60 :tipo "Visita" :status "Programada" :notas "Primera visita del comprador"}
                    {:propiedad_id (propiedad-ids "DEPTO-002") :cliente_id (cliente-ids "jorge.ramirez@gmail.com") :agente_id (agente-ids "carlos.martinez@inmuebles.com") :fecha_cita (fmt-datetime 0 16 0) :duracion_minutos 60 :tipo "Visita" :status "Programada" :notas "Arrendatario interesado en renta"}]]
    (doseq [c citas-data]
      (try
        (crud/Insert :citas c :conn conn)
        (swap! counts update :citas (fnil inc 0))
        (catch Exception e
          (println "[WARN] citas insert failed:" (.getMessage e)))))))

(defn seed-pagos! [conn counts cliente-ids agente-ids]
  (println "Inserting pagos...")
  (doseq [p [{:tipo "Venta" :referencia_id 1 :cliente_id (cliente-ids "luis.hernandez@gmail.com") :agente_id (agente-ids "ana.garcia@inmuebles.com") :fecha_pago (date-str -5) :monto 500000.00 :metodo_pago "Transferencia" :referencia "BBVA-12345" :concepto "Enganche de casa"}
             {:tipo "Renta" :referencia_id 1 :cliente_id (cliente-ids "jorge.ramirez@gmail.com") :agente_id (agente-ids "carlos.martinez@inmuebles.com") :fecha_pago (date-str -1) :monto 18000.00 :metodo_pago "Efectivo" :referencia "REC-" :concepto "Primer mes de renta"}]]
    (try
      (crud/Insert :pagos p :conn conn)
      (swap! counts update :pagos (fnil inc 0))
      (catch Exception e
        (println "[WARN] pagos insert failed:" (.getMessage e))))))

(defn seed-avaluos! [conn counts propiedad-ids]
  (println "Inserting avaluos...")
  (doseq [a [{:propiedad_id (propiedad-ids "CASA-001") :fecha_avaluo (date-str -11) :perito_valuador "Perito Juan López" :institucion "Avaluos MX" :valor_avaluo 5400000.00 :documento_url "https://example.com/docs/avaluo-casa-001.pdf" :notas "Avalúo previo a la venta"}
             {:propiedad_id (propiedad-ids "DEPTO-002") :fecha_avaluo (date-str -6) :perito_valuador "Perito María Torres" :institucion "Avaluos MX" :valor_avaluo 2000000.00 :documento_url "https://example.com/docs/avaluo-depto-002.pdf" :notas "Avalúo para renta"}]]
    (try
      (crud/Insert :avaluos a :conn conn)
      (swap! counts update :avaluos (fnil inc 0))
      (catch Exception e
        (println "[WARN] avaluos insert failed:" (.getMessage e))))))

(defn seed-documentos! [conn counts cliente-ids]
  (println "Inserting documentos...")
  (doseq [d [{:tipo "Contrato" :entidad "Venta" :entidad_id 1 :titulo "Contrato de Compraventa" :descripcion "Contrato firmado por ambas partes" :archivo_url "https://example.com/docs/contrato-venta-001.pdf" :tipo_archivo "PDF" :tamano_kb 350 :fecha_documento (date-str -6)}
             {:tipo "INE" :entidad "Cliente" :entidad_id (cliente-ids "luis.hernandez@gmail.com") :titulo "Identificación Oficial" :descripcion "INE del comprador" :archivo_url "https://example.com/docs/ine-luis.pdf" :tipo_archivo "PDF" :tamano_kb 120 :fecha_documento (date-str -32)}
             {:tipo "Contrato" :entidad "Renta" :entidad_id 1 :titulo "Contrato de Arrendamiento" :descripcion "Contrato de renta anual" :archivo_url "https://example.com/docs/contrato-renta-002.pdf" :tipo_archivo "PDF" :tamano_kb 200 :fecha_documento (date-str -1)}]]
    (try
      (crud/Insert :documentos d :conn conn)
      (swap! counts update :documentos (fnil inc 0))
      (catch Exception e
        (println "[WARN] documentos insert failed:" (.getMessage e))))))

(defn seed-migration-tables!
  "Orchestrator: clear, then seed all migration tables in FK order. Returns counts map."
  [& args]
  (let [token (first args)
        conn (choose-conn-key token)
        counts (atom {})]
    (println (format "[database] Seeding migration tables on connection: %s" (name conn)))
    (try
      (clear-migration-tables! conn)
      (let [estado-ids (seed-estados! conn counts)
            municipio-ids (seed-municipios! conn counts estado-ids)
            colonia-ids (seed-colonias! conn counts municipio-ids)
            tipo-ids (seed-tipos! conn counts)
            agente-ids (seed-agentes! conn counts)
            cliente-ids (seed-clientes! conn counts colonia-ids)
            propiedad-ids (seed-propiedades! conn counts tipo-ids colonia-ids municipio-ids estado-ids cliente-ids agente-ids)]
        (seed-fotos! conn counts propiedad-ids)
        (seed-ventas! conn counts propiedad-ids cliente-ids agente-ids)
        (seed-rentas! conn counts propiedad-ids cliente-ids agente-ids)
        (seed-citas! conn counts propiedad-ids cliente-ids agente-ids)
        (seed-pagos! conn counts cliente-ids agente-ids)
        (seed-avaluos! conn counts propiedad-ids)
        (seed-documentos! conn counts cliente-ids)
        (println "[database] Migration tables seeded.")
        (println "[database] Seeding summary:" @counts)
        @counts)
      (catch Exception e
        (println "[ERROR] seed-migration-tables! failed:" (.getMessage e))
        (.printStackTrace e)
        @counts))))

(comment
  (seed-migration-tables!))
