(ns br.handlers.reports.view
  (:require
   [br.models.grid :refer [build-dashboard]]))

(defn users-report
  [request title rows]
  (let [table-id "users-report"
        fields {:username "Usuario"
                :firstname "Nombre"
                :lastname "Apellido"
                :level "Nivel"
                :active "Estatus"}]
    (build-dashboard request title rows table-id fields)))

(defn propiedades-report
  [request title rows]
  (let [table-id "propiedades-report"
        fields {:clave "Clave"
                :titulo "Título"
                :operation "Operación"
                :status "Estatus"
                :precio_venta "Precio Venta"
                :precio_renta "Precio Renta"
                :recamaras "Recámaras"
                :banos_completos "Baños"
                :terreno_m2 "Terreno m²"
                :construccion_m2 "Construcción m²"
                :estado "Estado"
                :municipio "Municipio"
                :colonia "Colonia"
                :tipo "Tipo"}]
    (build-dashboard request title rows table-id fields)))

(defn ventas-report
  [request title rows]
  (let [table-id "ventas-report"
        fields {:id "ID"
                :fecha_venta "Fecha Venta"
                :precio_venta "Precio Venta"
                :enganche "Enganche"
                :status "Estatus"
                :propiedad "Clave"
                :titulo_propiedad "Propiedad"
                :comprador "Comprador"
                :vendedor "Vendedor"
                :agente "Agente"
                :comision_agente "Comisión"}]
    (build-dashboard request title rows table-id fields)))

(defn rentas-report
  [request title rows]
  (let [table-id "rentas-report"
        fields {:id "ID"
                :fecha_inicio "Inicio"
                :fecha_fin "Fin"
                :renta_mensual "Renta Mensual"
                :deposito "Depósito"
                :status "Estatus"
                :propiedad "Clave"
                :titulo_propiedad "Propiedad"
                :arrendatario "Arrendatario"
                :arrendador "Arrendador"
                :agente "Agente"}]
    (build-dashboard request title rows table-id fields)))

(defn clientes-report
  [request title rows]
  (let [table-id "clientes-report"
        fields {:tipo "Tipo"
                :nombre "Nombre"
                :apellido_paterno "Apellido Paterno"
                :apellido_materno "Apellido Materno"
                :email "Email"
                :telefono "Teléfono"
                :celular "Celular"
                :rfc "RFC"
                :ocupacion "Ocupación"
                :estado_civil "Estado Civil"
                :activo "Activo"}]
    (build-dashboard request title rows table-id fields)))

(defn agentes-report
  [request title rows]
  (let [table-id "agentes-report"
        fields {:nombre "Nombre"
                :apellido_paterno "Apellido Paterno"
                :apellido_materno "Apellido Materno"
                :email "Email"
                :telefono "Teléfono"
                :cedula_profesional "Cédula Profesional"
                :licencia_inmobiliaria "Licencia"
                :porcentaje_comision "% Comisión"
                :activo "Activo"
                :propiedades_asignadas "Propiedades"
                :ventas_realizadas "Ventas"
                :rentas_realizadas "Rentas"}]
    (build-dashboard request title rows table-id fields)))

(defn leads-report
  [request title rows]
  (let [table-id "leads-report"
        fields {:nombre "Nombre"
                :apellido_paterno "Apellido Paterno"
                :apellido_materno "Apellido Materno"
                :email "Email"
                :telefono "Teléfono"
                :origen "Origen"
                :tipo_interes "Tipo Interés"
                :presupuesto_min "Presupuesto Mín"
                :presupuesto_max "Presupuesto Máx"
                :status "Estatus"
                :fecha_registro "Fecha Registro"
                :activo "Activo"
                :agente_asignado "Agente"
                :estado_interes "Estado Interés"}]
    (build-dashboard request title rows table-id fields)))

(defn comisiones-report
  [request title rows]
  (let [table-id "comisiones-report"
        fields {:tipo "Tipo"
                :monto_venta "Monto Venta"
                :porcentaje_comision "% Comisión"
                :monto_comision "Comisión Total"
                :monto_pagado "Pagado"
                :monto_pendiente "Pendiente"
                :fecha_calculada "Fecha"
                :status "Estatus"
                :propiedad "Propiedad"
                :agente "Agente"
                :notas "Notas"}]
    (build-dashboard request title rows table-id fields)))

(defn citas-report
  [request title rows]
  (let [table-id "citas-report"
        fields {:fecha_cita "Fecha"
                :duracion_minutos "Duración (min)"
                :tipo "Tipo"
                :status "Estatus"
                :notas "Notas"
                :propiedad "Clave"
                :titulo_propiedad "Propiedad"
                :cliente "Cliente"
                :agente "Agente"}]
    (build-dashboard request title rows table-id fields)))

(defn pagos-report
  [request title rows]
  (let [table-id "pagos-report"
        fields {:fecha_pago "Fecha"
                :monto "Monto"
                :metodo_pago "Método"
                :referencia "Referencia"
                :concepto "Concepto"
                :tipo "Tipo"
                :referencia_id "ID Ref"
                :agente "Agente"
                :cliente "Cliente"}]
    (build-dashboard request title rows table-id fields)))
