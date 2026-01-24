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
;; This is optional only when you need to reseed the database. It is date centric
;; so that the data makes sense when testing the software.
;; Nota:  para empezar es lo mismo lein migrate, lein database esto no corre
;; tienes que ir a comment al final y correrlo aqui dentro del editor no cree
;; un alias en leiningen. Esto es temporario para probar el software.
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
        bc-id (get estado-ids "BC")
        bs-id (get estado-ids "BS")
        municipios-data [;; Jalisco
                         {:estado_id ja-id :nombre "Guadalajara" :activo "T"}
                         {:estado_id ja-id :nombre "Zapopan" :activo "T"}
                         {:estado_id ja-id :nombre "Tlaquepaque" :activo "T"}
                         {:estado_id ja-id :nombre "Tonalá" :activo "T"}
                         {:estado_id ja-id :nombre "Puerto Vallarta" :activo "T"}

                         ;; Baja California
                         {:estado_id bc-id :nombre "Tijuana" :activo "T"}
                         {:estado_id bc-id :nombre "Mexicali" :activo "T"}
                         {:estado_id bc-id :nombre "Ensenada" :activo "T"}
                         {:estado_id bc-id :nombre "Tecate" :activo "T"}
                         {:estado_id bc-id :nombre "Playas de Rosarito" :activo "T"}

                         ;; Baja California Sur
                         {:estado_id bs-id :nombre "La Paz" :activo "T"}
                         {:estado_id bs-id :nombre "Los Cabos" :activo "T"}
                         {:estado_id bs-id :nombre "Comondú" :activo "T"}
                         {:estado_id bs-id :nombre "Mulegé" :activo "T"}
                         {:estado_id bs-id :nombre "Loreto" :activo "T"}]
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
  (let [colonias-data [;; Guadalajara area
                       {:municipio_id (municipio-ids "Guadalajara") :nombre "Colonia Americana" :codigo_postal "44160"}
                       {:municipio_id (municipio-ids "Guadalajara") :nombre "Providencia" :codigo_postal "44630"}
                       {:municipio_id (municipio-ids "Zapopan") :nombre "Ciudad del Sol" :codigo_postal "45050"}
                       {:municipio_id (municipio-ids "Zapopan") :nombre "Chapalita" :codigo_postal "45040"}
                       {:municipio_id (municipio-ids "Tlaquepaque") :nombre "Centro Tlaquepaque" :codigo_postal "45500"}
                       {:municipio_id (municipio-ids "Tonalá") :nombre "Loma Dorada" :codigo_postal "45410"}
                       {:municipio_id (municipio-ids "Puerto Vallarta") :nombre "5 de Diciembre" :codigo_postal "48350"}

                       ;; Baja California
                       {:municipio_id (municipio-ids "Tijuana") :nombre "Centro Tijuana" :codigo_postal "22000"}
                       {:municipio_id (municipio-ids "Mexicali") :nombre "Emiliano Zapata" :codigo_postal "21350"}
                       {:municipio_id (municipio-ids "Ensenada") :nombre "Zona Centro" :codigo_postal "22800"}
                       {:municipio_id (municipio-ids "Tecate") :nombre "Centro Tecate" :codigo_postal "21400"}
                       {:municipio_id (municipio-ids "Playas de Rosarito") :nombre "Plan Libertador" :codigo_postal "22700"}

                       ;; Baja California Sur
                       {:municipio_id (municipio-ids "La Paz") :nombre "El Centenario" :codigo_postal "23000"}
                       {:municipio_id (municipio-ids "Los Cabos") :nombre "Cabo San Lucas Centro" :codigo_postal "23450"}
                       {:municipio_id (municipio-ids "Comondú") :nombre "Ciudad Constitución Centro" :codigo_postal "23880"}
                       {:municipio_id (municipio-ids "Mulegé") :nombre "Santa Rosalía Centro" :codigo_postal "23880"}
                       {:municipio_id (municipio-ids "Loreto") :nombre "Centro Loreto" :codigo_postal "23880"}]
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
                      {:nombre "Carlos" :apellido_paterno "Martínez" :apellido_materno "Ruiz" :email "carlos.martinez@inmuebles.com" :telefono "3323456789" :celular "3323456789" :cedula_profesional "CP654321" :licencia_inmobiliaria "LIC-2026-02" :porcentaje_comision 3.00 :activo "T" :biografia "Experto en ventas y rentas comerciales."}
                      ;; Regional agents
                      {:nombre "Luis" :apellido_paterno "Rivera" :apellido_materno "Sánchez" :email "luis.rivera@inmuebles.com" :telefono "6641234567" :celular "6641234567" :cedula_profesional "CP789012" :licencia_inmobiliaria "LIC-2026-03" :porcentaje_comision 3.50 :activo "T" :biografia "Agente en Baja California con experiencia en propiedades de playa."}
                      {:nombre "Mariana" :apellido_paterno "Soto" :apellido_materno "Vega" :email "mariana.soto@inmuebles.com" :telefono "6123456789" :celular "6123456789" :cedula_profesional "CP890123" :licencia_inmobiliaria "LIC-2026-04" :porcentaje_comision 3.50 :activo "T" :biografia "Agente en Baja California Sur, especializada en inmuebles vacacionales."}]
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
                   {:clave "DEPTO-002" :titulo "Departamento en Ciudad del Sol" :descripcion "Departamento moderno, ideal para jóvenes profesionales." :tipo_id (tipo-ids "Departamento") :calle "Av. Patria" :numero_exterior "654" :colonia_id (colonia-ids "Ciudad del Sol") :municipio_id (municipio-ids "Zapopan") :estado_id (estado-ids "JA") :codigo_postal "45050" :terreno_m2 90.00 :construccion_m2 90.00 :recamaras 2 :banos_completos 1 :medios_banos 1 :estacionamientos 1 :niveles 1 :antiguedad_anos 2 :operacion "Renta" :precio_renta 18000.00 :moneda "MXN" :status "Disponible" :cliente_propietario_id (cliente-ids "luis.hernandez@gmail.com") :agente_id (agente-ids "carlos.martinez@inmuebles.com") :activo "T" :destacada "F" :fecha_registro (date-str 9) :fecha_publicacion (date-str 11) :visitas 8}
                   ;; Additional sample properties requested
                   {:clave "CASA-003" :titulo "Casa con jardín en Lomas" :descripcion "Amplio jardín y área social perfecta para familias." :tipo_id (tipo-ids "Casa") :calle "Calle Lomas" :numero_exterior "12" :colonia_id (colonia-ids "Loma Dorada") :municipio_id (municipio-ids "Tonalá") :estado_id (estado-ids "JA") :codigo_postal "44620" :terreno_m2 300.00 :construccion_m2 210.00 :recamaras 4 :banos_completos 3 :estacionamientos 3 :niveles 2 :antiguedad_anos 3 :operacion "Venta" :precio_venta 4200000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "ana.garcia@inmuebles.com") :activo "T" :destacada "T" :fecha_registro (date-str -2) :fecha_publicacion (date-str 1) :visitas 5}
                   {:clave "DEP-004" :titulo "Departamento moderno en Centro" :descripcion "Ubicación céntrica con acabados modernos." :tipo_id (tipo-ids "Departamento") :calle "Av. Juárez" :numero_exterior "45" :colonia_id (colonia-ids "Centro Tlaquepaque") :municipio_id (municipio-ids "Tlaquepaque") :estado_id (estado-ids "JA") :codigo_postal "44100" :construccion_m2 75.00 :recamaras 2 :banos_completos 2 :estacionamientos 1 :operacion "Renta" :precio_renta 15000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "carlos.martinez@inmuebles.com") :activo "T" :destacada "F" :fecha_registro (date-str -1) :fecha_publicacion (date-str 0) :visitas 2}
                   {:clave "TER-005" :titulo "Terreno en La Primavera" :descripcion "Parcela ideal para desarrollos o inversión." :tipo_id (tipo-ids "Terreno") :calle "Carretera La Primavera" :numero_exterior "S/N" :colonia_id (colonia-ids "5 de Diciembre") :municipio_id (municipio-ids "Puerto Vallarta") :estado_id (estado-ids "JA") :codigo_postal "45100" :operacion "Venta" :precio_venta 1200000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "ana.garcia@inmuebles.com") :activo "T" :fecha_registro (date-str -3) :fecha_publicacion (date-str 2) :visitas 1}
                   {:clave "OFI-006" :titulo "Oficina en Ciudad Tecnológica" :descripcion "Oficina con vista y estacionamiento disponible." :tipo_id (tipo-ids "Oficina") :calle "Paseo Tecnológico" :numero_exterior "101" :colonia_id (colonia-ids "Chapalita") :municipio_id (municipio-ids "Zapopan") :estado_id (estado-ids "JA") :codigo_postal "45110" :construccion_m2 120.00 :recamaras 0 :banos_completos 2 :estacionamientos 2 :operacion "Ambos" :precio_venta 3000000.00 :precio_renta 25000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "carlos.martinez@inmuebles.com") :activo "T" :fecha_registro (date-str -4) :fecha_publicacion (date-str 3) :visitas 0}
                   ;; Baja California
                   {:clave "BC-TIJ-101" :titulo "Casa en Centro Tijuana" :descripcion "Casa céntrica con patio y múltiples comodidades." :tipo_id (tipo-ids "Casa") :calle "Calle 1" :numero_exterior "45" :colonia_id (colonia-ids "Centro Tijuana") :municipio_id (municipio-ids "Tijuana") :estado_id (estado-ids "BC") :codigo_postal "22000" :terreno_m2 200.00 :construccion_m2 160.00 :recamaras 3 :banos_completos 2 :estacionamientos 2 :niveles 2 :operacion "Venta" :precio_venta 4200000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "ana.garcia@inmuebles.com") :activo "T" :fecha_registro (date-str -2) :fecha_publicacion (date-str 1) :visitas 3}
                   {:clave "BC-MXL-102" :titulo "Departamento en Emiliano Zapata" :descripcion "Departamento moderno cerca de servicios." :tipo_id (tipo-ids "Departamento") :calle "Av. Reforma" :numero_exterior "210" :colonia_id (colonia-ids "Emiliano Zapata") :municipio_id (municipio-ids "Mexicali") :estado_id (estado-ids "BC") :codigo_postal "21350" :construccion_m2 85.00 :recamaras 2 :banos_completos 1 :estacionamientos 1 :operacion "Renta" :precio_renta 12000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "carlos.martinez@inmuebles.com") :activo "T" :fecha_registro (date-str -10) :fecha_publicacion (date-str -7) :visitas 1}
                   {:clave "BC-ENS-103" :titulo "Terreno en Zona Centro" :descripcion "Terreno urbano ideal para construcción." :tipo_id (tipo-ids "Terreno") :calle "Carretera Ensenada" :numero_exterior "S/N" :colonia_id (colonia-ids "Zona Centro") :municipio_id (municipio-ids "Ensenada") :estado_id (estado-ids "BC") :codigo_postal "22800" :operacion "Venta" :precio_venta 950000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "ana.garcia@inmuebles.com") :activo "T" :fecha_registro (date-str -20) :fecha_publicacion (date-str -15) :visitas 0}
                   {:clave "BC-TEC-104" :titulo "Casa en Tecate" :descripcion "Casa familiar en zona tranquila." :tipo_id (tipo-ids "Casa") :calle "Av. Hidalgo" :numero_exterior "77" :colonia_id (colonia-ids "Centro Tecate") :municipio_id (municipio-ids "Tecate") :estado_id (estado-ids "BC") :codigo_postal "21400" :construccion_m2 140.00 :recamaras 3 :banos_completos 2 :estacionamientos 1 :operacion "Venta" :precio_venta 2200000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "carlos.martinez@inmuebles.com") :activo "T" :fecha_registro (date-str -8) :fecha_publicacion (date-str -2) :visitas 2}
                   {:clave "BC-ROS-105" :titulo "Departamento frente al mar" :descripcion "Departamento con vista a la playa en Rosarito." :tipo_id (tipo-ids "Departamento") :calle "Blvd. Costero" :numero_exterior "10" :colonia_id (colonia-ids "Plan Libertador") :municipio_id (municipio-ids "Playas de Rosarito") :estado_id (estado-ids "BC") :codigo_postal "22700" :construccion_m2 95.00 :recamaras 2 :banos_completos 2 :estacionamientos 1 :operacion "Renta" :precio_renta 18000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "ana.garcia@inmuebles.com") :activo "T" :fecha_registro (date-str -3) :fecha_publicacion (date-str 1) :visitas 4}

                   ;; Baja California Sur
                   {:clave "BS-LAP-201" :titulo "Casa en El Centenario" :descripcion "Casa de playa con jardín y alberca." :tipo_id (tipo-ids "Casa") :calle "Paseo del Mar" :numero_exterior "5" :colonia_id (colonia-ids "El Centenario") :municipio_id (municipio-ids "La Paz") :estado_id (estado-ids "BS") :codigo_postal "23000" :terreno_m2 320.00 :construccion_m2 220.00 :recamaras 4 :banos_completos 3 :estacionamientos 3 :operacion "Venta" :precio_venta 7800000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "ana.garcia@inmuebles.com") :activo "T" :fecha_registro (date-str -6) :fecha_publicacion (date-str -1) :visitas 6}
                   {:clave "BS-CAB-202" :titulo "Condominio en Cabo San Lucas" :descripcion "Condominio moderno en el corazón de Cabo San Lucas." :tipo_id (tipo-ids "Departamento") :calle "Marina" :numero_exterior "88" :colonia_id (colonia-ids "Cabo San Lucas Centro") :municipio_id (municipio-ids "Los Cabos") :estado_id (estado-ids "BS") :codigo_postal "23450" :construccion_m2 110.00 :recamaras 3 :banos_completos 3 :estacionamientos 2 :operacion "Venta" :precio_venta 9800000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "carlos.martinez@inmuebles.com") :activo "T" :fecha_registro (date-str -2) :fecha_publicacion (date-str 2) :visitas 8}
                   {:clave "BS-CMD-203" :titulo "Terreno en Ciudad Constitución" :descripcion "Terreno ideal para agricultura o vivienda." :tipo_id (tipo-ids "Terreno") :calle "Carretera Transpeninsular" :numero_exterior "S/N" :colonia_id (colonia-ids "Ciudad Constitución Centro") :municipio_id (municipio-ids "Comondú") :estado_id (estado-ids "BS") :codigo_postal "23880" :operacion "Venta" :precio_venta 450000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "ana.garcia@inmuebles.com") :activo "T" :fecha_registro (date-str -12) :fecha_publicacion (date-str -8) :visitas 0}
                   {:clave "BS-MUL-204" :titulo "Casa en Santa Rosalía" :descripcion "Casa con vista al mar en Santa Rosalía." :tipo_id (tipo-ids "Casa") :calle "Malecón" :numero_exterior "23" :colonia_id (colonia-ids "Santa Rosalía Centro") :municipio_id (municipio-ids "Mulegé") :estado_id (estado-ids "BS") :codigo_postal "23880" :construccion_m2 140.00 :recamaras 3 :banos_completos 2 :operacion "Venta" :precio_venta 1850000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "carlos.martinez@inmuebles.com") :activo "T" :fecha_registro (date-str -7) :fecha_publicacion (date-str -3) :visitas 1}
                   {:clave "BS-LRT-205" :titulo "Departamento en Loreto Centro" :descripcion "Departamento ideal para turistas y renta vacacional." :tipo_id (tipo-ids "Departamento") :calle "Calle Principal" :numero_exterior "2" :colonia_id (colonia-ids "Centro Loreto") :municipio_id (municipio-ids "Loreto") :estado_id (estado-ids "BS") :codigo_postal "23880" :construccion_m2 70.00 :recamaras 1 :banos_completos 1 :operacion "Renta" :precio_renta 9000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "ana.garcia@inmuebles.com") :activo "T" :fecha_registro (date-str -4) :fecha_publicacion (date-str 1) :visitas 2}

                   ;; Extra Guadalajara properties
                   {:clave "GDL-007" :titulo "Casa moderna en Providencia" :descripcion "Residencia con acabados de lujo y jardín." :tipo_id (tipo-ids "Casa") :calle "Av. Guadalupe" :numero_exterior "88" :colonia_id (colonia-ids "Providencia") :municipio_id (municipio-ids "Guadalajara") :estado_id (estado-ids "JA") :codigo_postal "44630" :terreno_m2 260.00 :construccion_m2 200.00 :recamaras 4 :banos_completos 3 :estacionamientos 3 :operacion "Venta" :precio_venta 8200000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "luis.rivera@inmuebles.com") :activo "T" :fecha_registro (date-str -10) :fecha_publicacion (date-str -2) :visitas 6}
                   {:clave "ZAP-008" :titulo "Townhouse en Zapopan" :descripcion "Townhouse con uso mixto y acabados contemporáneos." :tipo_id (tipo-ids "Casa") :calle "Av. Acueducto" :numero_exterior "120" :colonia_id (colonia-ids "Chapalita") :municipio_id (municipio-ids "Zapopan") :estado_id (estado-ids "JA") :codigo_postal "45040" :construccion_m2 180.00 :recamaras 3 :banos_completos 2 :estacionamientos 2 :operacion "Venta" :precio_venta 5200000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "mariana.soto@inmuebles.com") :activo "T" :fecha_registro (date-str -6) :fecha_publicacion (date-str -1) :visitas 4}
                   {:clave "PV-009" :titulo "Departamento frente al mar en Puerto Vallarta" :descripcion "Departamento con vista al mar y acceso a puerto." :tipo_id (tipo-ids "Departamento") :calle "Malecon" :numero_exterior "500" :colonia_id (colonia-ids "5 de Diciembre") :municipio_id (municipio-ids "Puerto Vallarta") :estado_id (estado-ids "JA") :codigo_postal "48350" :construccion_m2 120.00 :recamaras 2 :banos_completos 2 :estacionamientos 1 :operacion "Venta" :precio_venta 9500000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "luis.rivera@inmuebles.com") :activo "T" :fecha_registro (date-str -20) :fecha_publicacion (date-str -10) :visitas 10}

                   ;; Baja California extras (3 per municipality)
                   {:clave "TIJ-110" :titulo "Casa en La Cacho" :descripcion "Casa remodelada cerca del centro de Tijuana." :tipo_id (tipo-ids "Casa") :calle "Calle Independencia" :numero_exterior "10" :colonia_id (colonia-ids "Centro Tijuana") :municipio_id (municipio-ids "Tijuana") :estado_id (estado-ids "BC") :codigo_postal "22010" :construccion_m2 140.00 :recamaras 3 :banos_completos 2 :estacionamientos 1 :operacion "Venta" :precio_venta 3200000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "luis.rivera@inmuebles.com") :activo "T" :fecha_registro (date-str -7) :fecha_publicacion (date-str -2) :visitas 2}
                   {:clave "TIJ-111" :titulo "Departamento céntrico Tijuana" :descripcion "Departamento a pasos de restaurantes y servicios." :tipo_id (tipo-ids "Departamento") :calle "Av. Revolución" :numero_exterior "55" :colonia_id (colonia-ids "Centro Tijuana") :municipio_id (municipio-ids "Tijuana") :estado_id (estado-ids "BC") :codigo_postal "22000" :construccion_m2 78.00 :recamaras 2 :banos_completos 1 :estacionamientos 1 :operacion "Renta" :precio_renta 9000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "luis.rivera@inmuebles.com") :activo "T" :fecha_registro (date-str -12) :fecha_publicacion (date-str -8) :visitas 1}
                   {:clave "MXL-112" :titulo "Casa en Emiliano Zapata" :descripcion "Casa con patio y acceso rápido a servicios." :tipo_id (tipo-ids "Casa") :calle "Av. Reforma" :numero_exterior "300" :colonia_id (colonia-ids "Emiliano Zapata") :municipio_id (municipio-ids "Mexicali") :estado_id (estado-ids "BC") :codigo_postal "21350" :construccion_m2 150.00 :recamaras 3 :banos_completos 2 :estacionamientos 2 :operacion "Venta" :precio_venta 1850000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "luis.rivera@inmuebles.com") :activo "T" :fecha_registro (date-str -9) :fecha_publicacion (date-str -4) :visitas 2}
                   {:clave "ENS-113" :titulo "Terreno cerca del mar" :descripcion "Terreno ideal para vivienda vacacional en Ensenada." :tipo_id (tipo-ids "Terreno") :calle "Carretera Tijuana-Ensenada" :numero_exterior "S/N" :colonia_id (colonia-ids "Zona Centro") :municipio_id (municipio-ids "Ensenada") :estado_id (estado-ids "BC") :codigo_postal "22800" :operacion "Venta" :precio_venta 750000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "mariana.soto@inmuebles.com") :activo "T" :fecha_registro (date-str -18) :fecha_publicacion (date-str -14) :visitas 0}
                   {:clave "TEC-114" :titulo "Casa en Tecate Centro" :descripcion "Casa tradicional con patio central." :tipo_id (tipo-ids "Casa") :calle "Av. Hidalgo" :numero_exterior "12" :colonia_id (colonia-ids "Centro Tecate") :municipio_id (municipio-ids "Tecate") :estado_id (estado-ids "BC") :codigo_postal "21400" :construccion_m2 120.00 :recamaras 3 :banos_completos 1 :estacionamientos 1 :operacion "Venta" :precio_venta 1650000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "mariana.soto@inmuebles.com") :activo "T" :fecha_registro (date-str -11) :fecha_publicacion (date-str -5) :visitas 1}
                   {:clave "ROS-115" :titulo "Departamento Rosarito" :descripcion "Departamento a pasos de la playa." :tipo_id (tipo-ids "Departamento") :calle "Blvd. Costero" :numero_exterior "22" :colonia_id (colonia-ids "Plan Libertador") :municipio_id (municipio-ids "Playas de Rosarito") :estado_id (estado-ids "BC") :codigo_postal "22700" :construccion_m2 85.00 :recamaras 2 :banos_completos 1 :operacion "Renta" :precio_renta 16000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "luis.rivera@inmuebles.com") :activo "T" :fecha_registro (date-str -3) :fecha_publicacion (date-str 1) :visitas 3}

                   ;; Baja California Sur extras
                   {:clave "LAP-210" :titulo "Casa El Centenario - 2" :descripcion "Casa amplia con vista y fácil acceso a la playa." :tipo_id (tipo-ids "Casa") :calle "Paseo del Mar" :numero_exterior "12" :colonia_id (colonia-ids "El Centenario") :municipio_id (municipio-ids "La Paz") :estado_id (estado-ids "BS") :codigo_postal "23000" :terreno_m2 400.00 :construccion_m2 260.00 :recamaras 5 :banos_completos 4 :estacionamientos 3 :operacion "Venta" :precio_venta 12500000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "mariana.soto@inmuebles.com") :activo "T" :fecha_registro (date-str -2) :fecha_publicacion (date-str 0) :visitas 4}
                   {:clave "CAB-211" :titulo "Departamento en Cabo San Lucas" :descripcion "Departamento con marina y amenidades." :tipo_id (tipo-ids "Departamento") :calle "Marina" :numero_exterior "120" :colonia_id (colonia-ids "Cabo San Lucas Centro") :municipio_id (municipio-ids "Los Cabos") :estado_id (estado-ids "BS") :codigo_postal "23450" :construccion_m2 130.00 :recamaras 3 :banos_completos 3 :estacionamientos 2 :operacion "Venta" :precio_venta 14200000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "luis.rivera@inmuebles.com") :activo "T" :fecha_registro (date-str -4) :fecha_publicacion (date-str 1) :visitas 6}
                   {:clave "CMD-212" :titulo "Terreno en Ciudad Constitución - 2" :descripcion "Excelente lote para cultivo o vivienda." :tipo_id (tipo-ids "Terreno") :calle "Carretera Transpeninsular" :numero_exterior "S/N" :colonia_id (colonia-ids "Ciudad Constitución Centro") :municipio_id (municipio-ids "Comondú") :estado_id (estado-ids "BS") :codigo_postal "23880" :operacion "Venta" :precio_venta 650000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "mariana.soto@inmuebles.com") :activo "T" :fecha_registro (date-str -14) :fecha_publicacion (date-str -10) :visitas 0}
                   {:clave "MUL-213" :titulo "Casa en Santa Rosalía - 2" :descripcion "Casa céntrica con acabados rústicos." :tipo_id (tipo-ids "Casa") :calle "Malecón" :numero_exterior "45" :colonia_id (colonia-ids "Santa Rosalía Centro") :municipio_id (municipio-ids "Mulegé") :estado_id (estado-ids "BS") :codigo_postal "23880" :construccion_m2 130.00 :recamaras 3 :banos_completos 2 :operacion "Venta" :precio_venta 1650000.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "mariana.soto@inmuebles.com") :activo "T" :fecha_registro (date-str -5) :fecha_publicacion (date-str -1) :visitas 1}
                   {:clave "LRT-214" :titulo "Departamento en Loreto - 2" :descripcion "Departamento con buena ubicación turística." :tipo_id (tipo-ids "Departamento") :calle "Calle Hidalgo" :numero_exterior "10" :colonia_id (colonia-ids "Centro Loreto") :municipio_id (municipio-ids "Loreto") :estado_id (estado-ids "BS") :codigo_postal "23880" :construccion_m2 60.00 :recamaras 1 :banos_completos 1 :operacion "Renta" :precio_renta 8500.00 :moneda "MXN" :status "Disponible" :agente_id (agente-ids "luis.rivera@inmuebles.com") :activo "T" :fecha_registro (date-str -6) :fecha_publicacion (date-str -2) :visitas 2}]
        ids (reduce (fn [m p]
                      (let [id (insert-and-find-id "propiedades" p :clave :conn conn)]
                        (when id (swap! counts update :propiedades (fnil inc 0)))
                        (if id (assoc m (:clave p) id) m)))
                    {}
                    prop-rows)]
    (println "Inserted" (count ids) "propiedades")
    ids))

(defn valid-image-url?
  "Return true if the given URL responds with an image Content-Type (HTTP HEAD or fallback GET).
   Uses a short timeout and returns false on any error."
  [url]
  (try
    (let [u (java.net.URL. url)]
      (try
        (let [^java.net.HttpURLConnection conn (.openConnection u)]
          (.setConnectTimeout conn 5000)
          (.setReadTimeout conn 5000)
          (.setRequestMethod conn "HEAD")
          (.setInstanceFollowRedirects conn true)
          (.connect conn)
          (let [code (.getResponseCode conn)
                ct (.getContentType conn)]
            (and (<= 200 code 299)
                 ct
                 (clojure.string/starts-with? (or ct "") "image/"))))
        (catch Exception _
          ;; fallback to GET for hosts that disallow HEAD
          (try
            (let [^java.net.HttpURLConnection conn2 (.openConnection u)]
              (.setRequestMethod conn2 "GET")
              (.setRequestProperty conn2 "Range" "bytes=0-0")
              (.setConnectTimeout conn2 5000)
              (.setReadTimeout conn2 5000)
              (.setInstanceFollowRedirects conn2 true)
              (.connect conn2)
              (let [code2 (.getResponseCode conn2)
                    ct2 (.getContentType conn2)]
                (and (<= 200 code2 299)
                     ct2
                     (clojure.string/starts-with? (or ct2 "") "image/"))))
            (catch Exception e
              (println "[WARN] valid-image-url? fallback failed for" url ":" (.getMessage e))
              false)))

        (catch Exception e
          (println "[WARN] valid-image-url? failed for" url ":" (.getMessage e))
          false)))))

(defn insert-photos-for-property!
  [prop-id urls & {:keys [conn desired-count] :or {desired-count 4}}]
  (let [candidates (take desired-count urls)
        valid-urls (filter valid-image-url? candidates)
        to-insert (map-indexed (fn [idx url]
                                 {:propiedad_id prop-id
                                  :url url
                                  :descripcion ""
                                  :es_principal (if (zero? idx) "T" "F")
                                  :orden (inc idx)})
                               valid-urls)]
    (doseq [photo to-insert]
      (try
        (crud/Insert :fotos_propiedad photo :conn conn)
        (catch Exception e
          (println "[WARN] insert photo failed for" (:url photo) ":" (.getMessage e)))))
    (count to-insert)))

(defn seed-fotos! [conn counts propiedad-ids]
  (println "Inserting fotos_propiedad...")
  ;; Use a rotating pool of Unsplash images and assign 4 per property for variety and availability
  (let [pool ["https://images.unsplash.com/photo-1542314831-068cd1dbfeeb"
              "https://images.unsplash.com/photo-1524758631624-e2822e304c36"
              "https://images.unsplash.com/photo-1444065381814-865dc9da92c0"
              "https://images.unsplash.com/photo-1494526585095-c41746248156"
              "https://images.unsplash.com/photo-1502672023488-70e25813eb80"
              "https://images.unsplash.com/photo-1472214103451-9374bd1c798e"
              "https://images.unsplash.com/photo-1519710164239-da123dc03ef4"
              "https://images.unsplash.com/photo-1472224371017-08207f84aaae"
              "https://images.unsplash.com/photo-1502673530728-f79b4cab31b1"
              "https://images.unsplash.com/photo-1484154218962-a197022b5858"]]
    (doseq [[idx clave] (map-indexed vector (keys propiedad-ids))]
      (when-let [pid (propiedad-ids clave)]
        (let [urls (mapv #(nth pool (mod (+ idx %) (count pool))) (range 4))
              inserted (insert-photos-for-property! pid urls :conn conn :desired-count 4)]
          (swap! counts update :fotos_propiedad (fnil + 0) inserted)
          (when (< inserted 4)
            (println "[WARN] only" inserted "valid images found for" clave)))))
    (println "Done inserting fotos_propiedad.")))

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
    ;; To reseed, evaluate in a REPL or with clojure -e:
  (br.models.cdb/seed-migration-tables! :localdb)
    ;; This call is intentionally commented out to avoid accidental execution.
  )


