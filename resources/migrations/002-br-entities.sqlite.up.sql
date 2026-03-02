-- =====================================================
-- Migración 002: Nuevas tablas y campos para bienes raíces
-- Fecha: 2026-03-01
-- Idempotente: segura para ejecutar múltiples veces
-- =====================================================

-- Los campos de propiedades, ventas y rentas ya fueron agregados en la migración original
-- Solo creamos las nuevas tablas

-- =====================================================
-- TABLA: LEADS (Prospectos)
-- =====================================================
CREATE TABLE IF NOT EXISTS leads (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  nombre VARCHAR(100) NOT NULL,
  apellido_paterno VARCHAR(50),
  apellido_materno VARCHAR(50),
  email VARCHAR(100),
  telefono VARCHAR(15),
  celular VARCHAR(15),
  origen VARCHAR(50),
  tipo_interes VARCHAR(20),
  propiedad_id INTEGER,
  presupuesto_min DECIMAL(12,2),
  presupuesto_max DECIMAL(12,2),
  estado_id INTEGER,
  municipio_id INTEGER,
  agente_id INTEGER,
  status VARCHAR(30) DEFAULT 'Nuevo',
  ultimo_contacto DATETIME,
  proxima_accion TEXT,
  notas TEXT,
  fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP,
  activo CHAR(1) DEFAULT 'T',
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME,
  FOREIGN KEY (propiedad_id) REFERENCES propiedades(id),
  FOREIGN KEY (estado_id) REFERENCES estados(id),
  FOREIGN KEY (municipio_id) REFERENCES municipios(id),
  FOREIGN KEY (agente_id) REFERENCES agentes(id)
);

CREATE INDEX IF NOT EXISTS idx_leads_status ON leads(status);
CREATE INDEX IF NOT EXISTS idx_leads_agente ON leads(agente_id);

-- =====================================================
-- TABLA: CONTRATOS
-- =====================================================
CREATE TABLE IF NOT EXISTS contratos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tipo_contrato VARCHAR(30) NOT NULL,
  venta_id INTEGER,
  renta_id INTEGER,
  cliente_id INTEGER NOT NULL,
  propiedad_id INTEGER NOT NULL,
  fecha_firma DATE NOT NULL,
  fecha_inicio DATE,
  fecha_fin DATE,
  fecha_vencimiento DATE,
  precio_final DECIMAL(12,2),
  enganche DECIMAL(12,2),
  monto_mensual DECIMAL(10,2),
  plazo_meses INTEGER,
  notario VARCHAR(200),
  numero_escritura VARCHAR(50),
  fecha_escritura DATE,
  registro_publico VARCHAR(200),
  folio_registral VARCHAR(50),
  documento_url VARCHAR(255),
  status VARCHAR(30) DEFAULT 'Borrador',
  notas TEXT,
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME,
  FOREIGN KEY (venta_id) REFERENCES ventas(id),
  FOREIGN KEY (renta_id) REFERENCES rentas(id),
  FOREIGN KEY (cliente_id) REFERENCES clientes(id),
  FOREIGN KEY (propiedad_id) REFERENCES propiedades(id)
);

CREATE INDEX IF NOT EXISTS idx_contratos_status ON contratos(status);
CREATE INDEX IF NOT EXISTS idx_contratos_propiedad ON contratos(propiedad_id);

-- =====================================================
-- TABLA: COMISIONES
-- =====================================================
CREATE TABLE IF NOT EXISTS comisiones (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tipo VARCHAR(20) NOT NULL,
  venta_id INTEGER,
  renta_id INTEGER,
  propiedad_id INTEGER,
  agente_id INTEGER NOT NULL,
  monto_venta DECIMAL(12,2),
  porcentaje_comision DECIMAL(5,2),
  monto_comision DECIMAL(10,2) NOT NULL,
  monto_pagado DECIMAL(10,2),
  monto_pendiente DECIMAL(10,2),
  fecha_calculada DATE,
  fecha_pago DATE,
  status VARCHAR(30) DEFAULT 'Pendiente',
  notas TEXT,
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME,
  FOREIGN KEY (venta_id) REFERENCES ventas(id),
  FOREIGN KEY (renta_id) REFERENCES rentas(id),
  FOREIGN KEY (propiedad_id) REFERENCES propiedades(id),
  FOREIGN KEY (agente_id) REFERENCES agentes(id)
);

CREATE INDEX IF NOT EXISTS idx_comisiones_status ON comisiones(status);
CREATE INDEX IF NOT EXISTS idx_comisiones_agente ON comisiones(agente_id);
CREATE INDEX IF NOT EXISTS idx_comisiones_venta ON comisiones(venta_id);
CREATE INDEX IF NOT EXISTS idx_comisiones_renta ON comisiones(renta_id);
