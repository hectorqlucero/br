-- Estados de México
CREATE TABLE estados (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  clave VARCHAR(2) NOT NULL UNIQUE,
  nombre VARCHAR(100) NOT NULL,
  activo CHAR(1) DEFAULT 'T'
);

-- Municipios (ejemplo para algunos estados)
CREATE TABLE municipios (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  estado_id INTEGER NOT NULL,
  nombre VARCHAR(100) NOT NULL,
  activo CHAR(1) DEFAULT 'T',
  FOREIGN KEY (estado_id) REFERENCES estados(id)
);

-- Colonias/Barrios
CREATE TABLE colonias (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  municipio_id INTEGER NOT NULL,
  nombre VARCHAR(100) NOT NULL,
  codigo_postal VARCHAR(5),
  activo CHAR(1) DEFAULT 'T',
  FOREIGN KEY (municipio_id) REFERENCES municipios(id)
);

-- Tipos de Propiedad
CREATE TABLE tipos_propiedad (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  nombre VARCHAR(50) NOT NULL,
  descripcion TEXT,
  activo CHAR(1) DEFAULT 'T'
);

-- =====================================================
-- MÓDULO DE CLIENTES
-- =====================================================

CREATE TABLE clientes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tipo VARCHAR(20) NOT NULL, -- 'Comprador', 'Vendedor', 'Arrendatario', 'Arrendador'
  nombre VARCHAR(100) NOT NULL,
  apellido_paterno VARCHAR(50),
  apellido_materno VARCHAR(50),
  email VARCHAR(100),
  telefono VARCHAR(15),
  celular VARCHAR(15),
  rfc VARCHAR(13),
  curp VARCHAR(18),
  fecha_nacimiento DATE,
  estado_civil VARCHAR(20),
  ocupacion VARCHAR(100),
  
  -- Dirección
  calle VARCHAR(200),
  numero_exterior VARCHAR(10),
  numero_interior VARCHAR(10),
  colonia_id INTEGER,
  codigo_postal VARCHAR(5),
  
  -- Control
  activo CHAR(1) DEFAULT 'T',
  notas TEXT,
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME,
  
  FOREIGN KEY (colonia_id) REFERENCES colonias(id)
);

-- =====================================================
-- MÓDULO DE AGENTES
-- =====================================================

CREATE TABLE agentes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  nombre VARCHAR(100) NOT NULL,
  apellido_paterno VARCHAR(50),
  apellido_materno VARCHAR(50),
  email VARCHAR(100) UNIQUE NOT NULL,
  telefono VARCHAR(15),
  celular VARCHAR(15) NOT NULL,
  
  -- Información profesional
  cedula_profesional VARCHAR(20),
  licencia_inmobiliaria VARCHAR(50),
  porcentaje_comision DECIMAL(5,2) DEFAULT 3.00,
  
  -- Control
  activo CHAR(1) DEFAULT 'T',
  foto_url VARCHAR(255),
  biografia TEXT,
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME
);

-- =====================================================
-- MÓDULO DE PROPIEDADES
-- =====================================================

CREATE TABLE propiedades (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  
  -- Identificación
  clave VARCHAR(20) UNIQUE,
  titulo VARCHAR(200) NOT NULL,
  descripcion TEXT,
  tipo_id INTEGER NOT NULL,
  
  -- Ubicación
  calle VARCHAR(200),
  numero_exterior VARCHAR(10),
  numero_interior VARCHAR(10),
  colonia_id INTEGER,
  municipio_id INTEGER,
  estado_id INTEGER NOT NULL,
  codigo_postal VARCHAR(5),
  latitud DECIMAL(10,8),
  longitud DECIMAL(11,8),
  
  -- Características
  terreno_m2 DECIMAL(10,2),
  construccion_m2 DECIMAL(10,2),
  recamaras INTEGER,
  banos_completos INTEGER,
  medios_banos INTEGER,
  estacionamientos INTEGER,
  niveles INTEGER DEFAULT 1,
  antiguedad_anos INTEGER,
  
  -- Amenidades (Sí/No)
  alberca CHAR(1) DEFAULT 'F',
  jardin CHAR(1) DEFAULT 'F',
  roof_garden CHAR(1) DEFAULT 'F',
  terraza CHAR(1) DEFAULT 'F',
  balcon CHAR(1) DEFAULT 'F',
  cuarto_servicio CHAR(1) DEFAULT 'F',
  gym CHAR(1) DEFAULT 'F',
  seguridad_24h CHAR(1) DEFAULT 'F',
  area_juegos CHAR(1) DEFAULT 'F',
  salon_eventos CHAR(1) DEFAULT 'F',
  
  -- Comercial
  operacion VARCHAR(10) NOT NULL, -- 'Venta', 'Renta', 'Ambos'
  precio_venta DECIMAL(12,2),
  precio_renta DECIMAL(10,2),
  moneda VARCHAR(3) DEFAULT 'MXN',
  status VARCHAR(20) DEFAULT 'Disponible', -- Disponible, Reservada, Vendida, Rentada
  
  -- Relaciones
  cliente_propietario_id INTEGER,
  agente_id INTEGER NOT NULL,
  
  -- Control
  activo CHAR(1) DEFAULT 'T',
  destacada CHAR(1) DEFAULT 'F',
  fecha_registro DATE DEFAULT CURRENT_DATE,
  fecha_publicacion DATE,
  visitas INTEGER DEFAULT 0,
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME,
  
  FOREIGN KEY (tipo_id) REFERENCES tipos_propiedad(id),
  FOREIGN KEY (colonia_id) REFERENCES colonias(id),
  FOREIGN KEY (municipio_id) REFERENCES municipios(id),
  FOREIGN KEY (estado_id) REFERENCES estados(id),
  FOREIGN KEY (cliente_propietario_id) REFERENCES clientes(id),
  FOREIGN KEY (agente_id) REFERENCES agentes(id)
);

-- Fotos de Propiedades
CREATE TABLE fotos_propiedad (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  propiedad_id INTEGER NOT NULL,
  url VARCHAR(255) NOT NULL,
  descripcion VARCHAR(200),
  es_principal CHAR(1) DEFAULT 'F',
  orden INTEGER DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (propiedad_id) REFERENCES propiedades(id) ON DELETE CASCADE
);

-- =====================================================
-- MÓDULO DE TRANSACCIONES
-- =====================================================

-- Ventas
CREATE TABLE ventas (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  propiedad_id INTEGER NOT NULL,
  cliente_comprador_id INTEGER NOT NULL,
  cliente_vendedor_id INTEGER,
  agente_id INTEGER NOT NULL,
  
  fecha_venta DATE NOT NULL,
  precio_venta DECIMAL(12,2) NOT NULL,
  enganche DECIMAL(12,2),
  financiamiento CHAR(1) DEFAULT 'F',
  institucion_financiera VARCHAR(100),
  
  comision_total DECIMAL(10,2),
  comision_agente DECIMAL(10,2),
  
  status VARCHAR(20) DEFAULT 'En Proceso', -- En Proceso, Escriturada, Cancelada
  notas TEXT,
  
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME,
  
  FOREIGN KEY (propiedad_id) REFERENCES propiedades(id),
  FOREIGN KEY (cliente_comprador_id) REFERENCES clientes(id),
  FOREIGN KEY (cliente_vendedor_id) REFERENCES clientes(id),
  FOREIGN KEY (agente_id) REFERENCES agentes(id)
);

-- Rentas
CREATE TABLE rentas (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  propiedad_id INTEGER NOT NULL,
  cliente_arrendatario_id INTEGER NOT NULL,
  cliente_arrendador_id INTEGER,
  agente_id INTEGER NOT NULL,
  
  fecha_inicio DATE NOT NULL,
  fecha_fin DATE NOT NULL,
  renta_mensual DECIMAL(10,2) NOT NULL,
  deposito DECIMAL(10,2),
  dia_pago INTEGER DEFAULT 1,
  
  status VARCHAR(20) DEFAULT 'Activa', -- Activa, Finalizada, Cancelada
  notas TEXT,
  
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME,
  
  FOREIGN KEY (propiedad_id) REFERENCES propiedades(id),
  FOREIGN KEY (cliente_arrendatario_id) REFERENCES clientes(id),
  FOREIGN KEY (cliente_arrendador_id) REFERENCES clientes(id),
  FOREIGN KEY (agente_id) REFERENCES agentes(id)
);

-- Citas/Visitas
CREATE TABLE citas (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  propiedad_id INTEGER NOT NULL,
  cliente_id INTEGER NOT NULL,
  agente_id INTEGER NOT NULL,
  
  fecha_cita DATETIME NOT NULL,
  duracion_minutos INTEGER DEFAULT 60,
  tipo VARCHAR(20) DEFAULT 'Visita', -- Visita, Llamada, Virtual
  
  status VARCHAR(20) DEFAULT 'Programada', -- Programada, Completada, Cancelada, NoAsistió
  notas TEXT,
  resultado TEXT, -- Comentarios post-visita
  
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME,
  
  FOREIGN KEY (propiedad_id) REFERENCES propiedades(id),
  FOREIGN KEY (cliente_id) REFERENCES clientes(id),
  FOREIGN KEY (agente_id) REFERENCES agentes(id)
);

-- =====================================================
-- MÓDULO FINANCIERO
-- =====================================================

-- Pagos
CREATE TABLE pagos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tipo VARCHAR(20) NOT NULL, -- 'Renta', 'Venta', 'Comision'
  referencia_id INTEGER, -- ID de venta o renta
  cliente_id INTEGER,
  agente_id INTEGER,
  
  fecha_pago DATE NOT NULL,
  monto DECIMAL(10,2) NOT NULL,
  metodo_pago VARCHAR(20), -- Efectivo, Transferencia, Cheque, Tarjeta
  referencia VARCHAR(100),
  
  concepto TEXT,
  notas TEXT,
  
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  
  FOREIGN KEY (cliente_id) REFERENCES clientes(id),
  FOREIGN KEY (agente_id) REFERENCES agentes(id)
);

-- Avalúos
CREATE TABLE avaluos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  propiedad_id INTEGER NOT NULL,
  
  fecha_avaluo DATE NOT NULL,
  perito_valuador VARCHAR(100),
  institucion VARCHAR(100),
  valor_avaluo DECIMAL(12,2) NOT NULL,
  
  documento_url VARCHAR(255),
  notas TEXT,
  
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  
  FOREIGN KEY (propiedad_id) REFERENCES propiedades(id)
);

-- =====================================================
-- MÓDULO DE DOCUMENTOS
-- =====================================================

CREATE TABLE documentos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tipo VARCHAR(50) NOT NULL, -- Contrato, Escritura, INE, RFC, etc
  entidad VARCHAR(20), -- 'Propiedad', 'Cliente', 'Venta', 'Renta'
  entidad_id INTEGER,
  
  titulo VARCHAR(200) NOT NULL,
  descripcion TEXT,
  archivo_url VARCHAR(255) NOT NULL,
  tipo_archivo VARCHAR(10), -- PDF, DOC, JPG, etc
  tamano_kb INTEGER,
  
  fecha_documento DATE,
  vigencia DATE,
  
  created_by INTEGER,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  modified_by INTEGER,
  modified_at DATETIME
);

-- =====================================================
-- MÓDULO DE SISTEMA
-- =====================================================

CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  lastname TEXT,
  firstname TEXT,
  username TEXT UNIQUE,
  password TEXT,
  dob TEXT,
  cell TEXT,
  phone TEXT,
  fax TEXT,
  email TEXT,
  level TEXT,
  active TEXT,
  imagen TEXT,
  agente_id integer, -- Vinculado a un agente
  last_login TEXT DEFAULT (datetime('now')),
  FOREIGN KEY (agente_id) REFERENCES agentes(id)
);

-- =====================================================
-- MÓDULO DE AUDITORIA
-- =====================================================
CREATE TABLE IF NOT EXISTS audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entity text NOT NULL,                           -- Entity nombre ('users', 'clientes')
    operation text NOT NULL,                        -- Operation ('create', 'update', 'delete') 
    data TEXT,                                      -- Serialized data (pr-str format),
    user_id INTEGER,                                -- Usuario que ejecuto la acción
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
);
-- =====================================================
-- ÍNDICES
-- =====================================================
CREATE  INDEX idx_audit_entity ON audit_log(entity);
CREATE  INDEX idx_audit_operation ON audit_log(operation);
CREATE  INDEX idx_audit_user ON audit_log(user_id);
CREATE  INDEX idx_audit_timestamp ON audit_log(timestamp);

CREATE INDEX idx_propiedades_estado ON propiedades(estado_id);
CREATE INDEX idx_propiedades_municipio ON propiedades(municipio_id);
CREATE INDEX idx_propiedades_tipo ON propiedades(tipo_id);
CREATE INDEX idx_propiedades_status ON propiedades(status);
CREATE INDEX idx_propiedades_operacion ON propiedades(operacion);
CREATE INDEX idx_propiedades_agente ON propiedades(agente_id);

CREATE INDEX idx_clientes_tipo ON clientes(tipo);
CREATE INDEX idx_clientes_email ON clientes(email);

CREATE INDEX idx_ventas_fecha ON ventas(fecha_venta);
CREATE INDEX idx_rentas_fecha_inicio ON rentas(fecha_inicio);
CREATE INDEX idx_citas_fecha ON citas(fecha_cita);
CREATE INDEX idx_citas_status ON citas(status);
