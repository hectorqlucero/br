-- =====================================================
-- Rollback Migración 002: Eliminar nuevas tablas y campos
-- =====================================================

-- Eliminar tablas
DROP TABLE IF EXISTS comisiones;
DROP TABLE IF EXISTS contratos;
DROP TABLE IF EXISTS leads;

-- Eliminar columnas de propiedades
ALTER TABLE propiedades DROP COLUMN IF EXISTS salon_eventos;
ALTER TABLE propiedades DROP COLUMN IF EXISTS area_juegos;
ALTER TABLE propiedades DROP COLUMN IF EXISTS cuarto_servicio;
ALTER TABLE propiedades DROP COLUMN IF EXISTS balcon;

-- Eliminar columnas de ventas
ALTER TABLE ventas DROP COLUMN IF EXISTS notario;
ALTER TABLE ventas DROP COLUMN IF EXISTS fecha_escrituracion;
ALTER TABLE ventas DROP COLUMN IF EXISTS comision_pagada;

-- Eliminar columnas de rentas
ALTER TABLE rentas DROP COLUMN IF EXISTS poliza_arrendamiento;
ALTER TABLE rentas DROP COLUMN IF EXISTS incremento_anual;
