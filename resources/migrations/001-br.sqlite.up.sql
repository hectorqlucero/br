-- =====================================================
-- CATÁLOGOS BASE
-- =====================================================

-- Estados de México
CREATE TABLE estados (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  clave VARCHAR(2) NOT NULL UNIQUE,
  nombre VARCHAR(100) NOT NULL,
  activo CHAR(1) DEFAULT 'T'
);

INSERT INTO estados (clave, nombre) VALUES
  ('AG', 'Aguascalientes'),
  ('BC', 'Baja California'),
  ('BS', 'Baja California Sur'),
  ('CM', 'Campeche'),
  ('CS', 'Chiapas'),
  ('CH', 'Chihuahua'),
  ('CX', 'Ciudad de México'),
  ('CO', 'Coahuila'),
  ('CL', 'Colima'),
  ('DG', 'Durango'),
  ('GT', 'Guanajuato'),
  ('GR', 'Guerrero'),
  ('HG', 'Hidalgo'),
  ('JA', 'Jalisco'),
  ('EM', 'Estado de México'),
  ('MI', 'Michoacán'),
  ('MO', 'Morelos'),
  ('NA', 'Nayarit'),
  ('NL', 'Nuevo León'),
  ('OA', 'Oaxaca'),
  ('PU', 'Puebla'),
  ('QT', 'Querétaro'),
  ('QR', 'Quintana Roo'),
  ('SL', 'San Luis Potosí'),
  ('SI', 'Sinaloa'),
  ('SO', 'Sonora'),
  ('TB', 'Tabasco'),
  ('TM', 'Tamaulipas'),
  ('TL', 'Tlaxcala'),
  ('VE', 'Veracruz'),
  ('YU', 'Yucatán'),
  ('ZA', 'Zacatecas');

-- Municipios (ejemplo para algunos estados)
CREATE TABLE municipios (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  estado_id INTEGER NOT NULL,
  nombre VARCHAR(100) NOT NULL,
  activo CHAR(1) DEFAULT 'T',
  FOREIGN KEY (estado_id) REFERENCES estados(id)
);

-- Ejemplos de municipios de Jalisco
INSERT INTO municipios (estado_id, nombre) VALUES
  (14, 'Guadalajara'),
  (14, 'Zapopan'),
  (14, 'Tlaquepaque'),
  (14, 'Tonalá'),
  (14, 'Puerto Vallarta');

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

INSERT INTO tipos_propiedad (nombre, descripcion) VALUES
  ('Casa', 'Casa habitación independiente'),
  ('Departamento', 'Departamento o condominio'),
  ('Terreno', 'Terreno baldío'),
  ('Local Comercial', 'Local para negocio'),
  ('Oficina', 'Oficina o espacio corporativo'),
  ('Bodega', 'Bodega industrial o comercial'),
  ('Rancho', 'Rancho o finca rural'),
  ('Penthouse', 'Departamento de lujo en último piso');

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
-- ÍNDICES
-- =====================================================

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



-- =====================================================
-- SEED DATA FOR ALL TABLES (2026-centric)
-- =====================================================

-- Colonias (for Guadalajara, Zapopan, etc.)
INSERT INTO colonias (municipio_id, nombre, codigo_postal) VALUES
  (1, 'Colonia Americana', '44160'),
  (1, 'Providencia', '44630'),
  (2, 'Ciudad del Sol', '45050'),
  (2, 'Chapalita', '45040'),
  (3, 'Centro Tlaquepaque', '45500'),
  (4, 'Loma Dorada', '45410'),
  (5, '5 de Diciembre', '48350');

-- Agentes
INSERT INTO agentes (nombre, apellido_paterno, apellido_materno, email, telefono, celular, cedula_profesional, licencia_inmobiliaria, porcentaje_comision, activo, foto_url, biografia)
VALUES
  ('Ana', 'García', 'López', 'ana.garcia@inmuebles.com', '3312345678', '3312345678', 'CP123456', 'LIC-2026-01', 3.00, 'T', NULL, 'Especialista en propiedades residenciales.'),
  ('Carlos', 'Martínez', 'Ruiz', 'carlos.martinez@inmuebles.com', '3323456789', '3323456789', 'CP654321', 'LIC-2026-02', 3.00, 'T', NULL, 'Experto en ventas y rentas comerciales.');

-- Clientes
INSERT INTO clientes (tipo, nombre, apellido_paterno, apellido_materno, email, telefono, celular, rfc, curp, fecha_nacimiento, estado_civil, ocupacion, calle, numero_exterior, numero_interior, colonia_id, codigo_postal, activo, notas)
VALUES
  ('Comprador', 'Luis', 'Hernández', 'Soto', 'luis.hernandez@gmail.com', '3334567890', '3334567890', 'HELS900101XXX', 'HELS900101HJCSTR09', '1990-01-01', 'Soltero', 'Ingeniero', 'Av. México', '123', NULL, 1, '44160', 'T', NULL),
  ('Vendedor', 'María', 'Pérez', 'Gómez', 'maria.perez@gmail.com', '3345678901', '3345678901', 'PEGG850202XXX', 'PEGG850202MJCSTR08', '1985-02-02', 'Casada', 'Contadora', 'Calle Libertad', '456', '2', 2, '44630', 'T', NULL),
  ('Arrendatario', 'Jorge', 'Ramírez', 'Díaz', 'jorge.ramirez@gmail.com', '3356789012', '3356789012', 'RADI920303XXX', 'RADI920303HJCSTR07', '1992-03-03', 'Soltero', 'Diseñador', 'Calle Sol', '789', NULL, 3, '45050', 'T', NULL);

-- Propiedades
INSERT INTO propiedades (clave, titulo, descripcion, tipo_id, calle, numero_exterior, colonia_id, municipio_id, estado_id, codigo_postal, terreno_m2, construccion_m2, recamaras, banos_completos, medios_banos, estacionamientos, niveles, antiguedad_anos, operacion, precio_venta, precio_renta, moneda, status, cliente_propietario_id, agente_id, activo, destacada, fecha_registro, fecha_publicacion, visitas)
VALUES
  ('CASA-001', 'Casa en Providencia', 'Hermosa casa familiar en zona exclusiva.', 1, 'Calle Robles', '321', 2, 1, 14, '44630', 250.00, 180.00, 3, 2, 1, 2, 2, 5, 'Venta', 5500000.00, NULL, 'MXN', 'Disponible', 2, 1, 'T', 'T', '2026-01-01', '2026-01-05', 12),
  ('DEPTO-002', 'Departamento en Ciudad del Sol', 'Departamento moderno, ideal para jóvenes profesionales.', 2, 'Av. Patria', '654', 3, 2, 14, '45050', 90.00, 90.00, 2, 1, 1, 1, 1, 2, 'Renta', NULL, 18000.00, 'MXN', 'Disponible', 1, 2, 'T', 'F', '2026-01-10', '2026-01-12', 8);

-- Fotos de Propiedades
INSERT INTO fotos_propiedad (propiedad_id, url, descripcion, es_principal, orden)
VALUES
  (1, 'https://example.com/fotos/casa-001-1.jpg', 'Fachada principal', 'T', 1),
  (1, 'https://example.com/fotos/casa-001-2.jpg', 'Sala', 'F', 2),
  (2, 'https://example.com/fotos/depto-002-1.jpg', 'Vista desde el balcón', 'T', 1);

-- Ventas
INSERT INTO ventas (propiedad_id, cliente_comprador_id, cliente_vendedor_id, agente_id, fecha_venta, precio_venta, enganche, financiamiento, institucion_financiera, comision_total, comision_agente, status)
VALUES
  (1, 1, 2, 1, '2026-01-15', 5500000.00, 500000.00, 'T', 'BBVA', 165000.00, 49500.00, 'En Proceso');

-- Rentas
INSERT INTO rentas (propiedad_id, cliente_arrendatario_id, cliente_arrendador_id, agente_id, fecha_inicio, fecha_fin, renta_mensual, deposito, dia_pago, status)
VALUES
  (2, 3, 1, 2, '2026-01-20', '2027-01-19', 18000.00, 18000.00, 1, 'Activa');

-- Citas
INSERT INTO citas (propiedad_id, cliente_id, agente_id, fecha_cita, duracion_minutos, tipo, status, notas)
VALUES
  (1, 1, 1, '2026-01-20 10:00:00', 60, 'Visita', 'Programada', 'Primera visita del comprador'),
  (2, 3, 2, '2026-01-21 16:00:00', 60, 'Visita', 'Programada', 'Arrendatario interesado en renta');

-- Pagos
INSERT INTO pagos (tipo, referencia_id, cliente_id, agente_id, fecha_pago, monto, metodo_pago, referencia, concepto)
VALUES
  ('Venta', 1, 1, 1, '2026-01-16', 500000.00, 'Transferencia', 'BBVA-12345', 'Enganche de casa'),
  ('Renta', 1, 3, 2, '2026-01-20', 18000.00, 'Efectivo', 'REC-20260120', 'Primer mes de renta');

-- Avalúos
INSERT INTO avaluos (propiedad_id, fecha_avaluo, perito_valuador, institucion, valor_avaluo, documento_url, notas)
VALUES
  (1, '2026-01-10', 'Perito Juan López', 'Avaluos MX', 5400000.00, 'https://example.com/docs/avaluo-casa-001.pdf', 'Avalúo previo a la venta'),
  (2, '2026-01-15', 'Perito María Torres', 'Avaluos MX', 2000000.00, 'https://example.com/docs/avaluo-depto-002.pdf', 'Avalúo para renta');

-- Documentos
INSERT INTO documentos (tipo, entidad, entidad_id, titulo, descripcion, archivo_url, tipo_archivo, tamano_kb, fecha_documento)
VALUES
  ('Contrato', 'Venta', 1, 'Contrato de Compraventa', 'Contrato firmado por ambas partes', 'https://example.com/docs/contrato-venta-001.pdf', 'PDF', 350, '2026-01-15'),
  ('INE', 'Cliente', 1, 'Identificación Oficial', 'INE del comprador', 'https://example.com/docs/ine-luis.pdf', 'PDF', 120, '2025-12-20'),
  ('Contrato', 'Renta', 1, 'Contrato de Arrendamiento', 'Contrato de renta anual', 'https://example.com/docs/contrato-renta-002.pdf', 'PDF', 200, '2026-01-20');
