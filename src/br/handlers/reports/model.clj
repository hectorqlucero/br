(ns br.handlers.reports.model
  (:require
   [br.models.crud :refer [Query]]))

(defn get-users
  []
  (Query ["select * from users order by firstname, lastname"]))

(defn get-propiedades
  []
  (Query ["SELECT p.clave, p.titulo, p.operacion, p.status, p.precio_venta, p.precio_renta,
          p.recamaras, p.banos_completos, p.terreno_m2, p.construccion_m2,
          e.nombre AS estado, m.nombre AS municipio, c.nombre AS colonia,
          tp.nombre AS tipo
           FROM propiedades p
           LEFT JOIN estados e ON p.estado_id = e.id
           LEFT JOIN municipios m ON p.municipio_id = m.id
           LEFT JOIN colonias c ON p.colonia_id = c.id
           LEFT JOIN tipos_propiedad tp ON p.tipo_id = tp.id
           ORDER BY p.fecha_registro DESC"]))

(defn get-ventas
  []
  (Query ["SELECT v.id, v.fecha_venta, v.precio_venta, v.enganche, v.status,
          p.clave AS propiedad, p.titulo AS titulo_propiedad,
          CONCAT(cv.nombre, ' ', cv.apellido_paterno) AS comprador,
          CONCAT(cve.nombre, ' ', cve.apellido_paterno) AS vendedor,
          CONCAT(a.nombre, ' ', a.apellido_paterno) AS agente,
          v.comision_agente
           FROM ventas v
           LEFT JOIN propiedades p ON v.propiedad_id = p.id
           LEFT JOIN clientes cv ON v.cliente_comprador_id = cv.id
           LEFT JOIN clientes cve ON v.cliente_vendedor_id = cve.id
           LEFT JOIN agentes a ON v.agente_id = a.id
           ORDER BY v.fecha_venta DESC"]))

(defn get-rentas
  []
  (Query ["SELECT r.id, r.fecha_inicio, r.fecha_fin, r.renta_mensual, r.deposito, r.status,
          p.clave AS propiedad, p.titulo AS titulo_propiedad,
          CONCAT(ca.nombre, ' ', ca.apellido_paterno) AS arrendatario,
          CONCAT(carr.nombre, ' ', carr.apellido_paterno) AS arrendador,
          CONCAT(a.nombre, ' ', a.apellido_paterno) AS agente
           FROM rentas r
           LEFT JOIN propiedades p ON r.propiedad_id = p.id
           LEFT JOIN clientes ca ON r.cliente_arrendatario_id = ca.id
           LEFT JOIN clientes carr ON r.cliente_arrendador_id = carr.id
           LEFT JOIN agentes a ON r.agente_id = a.id
           ORDER BY r.fecha_inicio DESC"]))

(defn get-clientes
  []
  (Query ["SELECT c.tipo, c.nombre, c.apellido_paterno, c.apellido_materno,
          c.email, c.telefono, c.celular, c.rfc, c.ocupacion, c.estado_civil,
          c.colonia_id, c.activo
           FROM clientes c
           ORDER BY c.tipo, c.apellido_paterno, c.nombre"]))

(defn get-agentes
  []
  (Query ["SELECT a.nombre, a.apellido_paterno, a.apellido_materno, a.email,
          a.telefono, a.cedula_profesional, a.licencia_inmobiliaria,
          a.porcentaje_comision, a.activo,
          (SELECT COUNT(*) FROM propiedades WHERE agente_id = a.id) AS propiedades_asignadas,
          (SELECT COUNT(*) FROM ventas WHERE agente_id = a.id) AS ventas_realizadas,
          (SELECT COUNT(*) FROM rentas WHERE agente_id = a.id) AS rentas_realizadas
           FROM agentes a
           ORDER BY a.apellido_paterno, a.nombre"]))

(defn get-leads
  []
  (Query ["SELECT l.nombre, l.apellido_paterno, l.apellido_materno, l.email,
          l.telefono, l.origen, l.tipo_interes, l.presupuesto_min, l.presupuesto_max,
          l.status, l.fecha_registro, l.activo,
          CONCAT(a.nombre, ' ', a.apellido_paterno) AS agente_asignado,
          e.nombre AS estado_interes
           FROM leads l
           LEFT JOIN agentes a ON l.agente_id = a.id
           LEFT JOIN estados e ON l.estado_id = e.id
           ORDER BY l.fecha_registro DESC"]))

(defn get-comisiones
  []
  (Query ["SELECT c.tipo, c.monto_venta, c.porcentaje_comision, c.monto_comision,
          c.monto_pagado, c.monto_pendiente, c.fecha_calculada, c.status, c.notas,
          p.clave AS propiedad,
          CONCAT(a.nombre, ' ', a.apellido_paterno) AS agente
           FROM comisiones c
           LEFT JOIN propiedades p ON c.propiedad_id = p.id
           LEFT JOIN agentes a ON c.agente_id = a.id
           ORDER BY c.fecha_calculada DESC"]))

(defn get-citas
  []
  (Query ["SELECT c.fecha_cita, c.duracion_minutos, c.tipo, c.status, c.notas,
          p.clave AS propiedad, p.titulo AS titulo_propiedad,
          CONCAT(cl.nombre, ' ', cl.apellido_paterno) AS cliente,
          CONCAT(a.nombre, ' ', a.apellido_paterno) AS agente
           FROM citas c
           LEFT JOIN propiedades p ON c.propiedad_id = p.id
           LEFT JOIN clientes cl ON c.cliente_id = cl.id
           LEFT JOIN agentes a ON c.agente_id = a.id
           ORDER BY c.fecha_cita DESC"]))

(defn get-pagos
  []
  (Query ["SELECT p.fecha_pago, p.monto, p.metodo_pago, p.referencia, p.concepto, p.tipo,
          p.referencia_id,
          CONCAT(a.nombre, ' ', a.apellido_paterno) AS agente,
          CONCAT(c.nombre, ' ', c.apellido_paterno) AS cliente
           FROM pagos p
           LEFT JOIN agentes a ON p.agente_id = a.id
           LEFT JOIN clientes c ON p.cliente_id = c.id
           ORDER BY p.fecha_pago DESC"]))

(comment
  (get-propiedades)
  (get-ventas)
  (get-rentas)
  (get-clientes)
  (get-agentes)
  (get-leads)
  (get-comisiones)
  (get-citas)
  (get-pagos))
