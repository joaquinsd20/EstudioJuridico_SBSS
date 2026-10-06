-- =============================================================================
-- LEXIA — V25: Índices de escalabilidad para Biblioteca Jurídica
-- =============================================================================
-- V10 ya puede estar aplicada en ambientes existentes, por lo que no se
-- modifica. Estos índices acompañan la paginación, filtros y borrado lógico.
-- =============================================================================

CREATE INDEX ix_biblioteca_paginacion
    ON recursos_juridicos (estudio_id, activo, fecha_creacion DESC, id DESC);

CREATE INDEX ix_biblioteca_filtros
    ON recursos_juridicos (estudio_id, activo, estado, tipo, categoria, anio);

CREATE INDEX ix_biblioteca_archivo
    ON recursos_juridicos (estudio_id, activo, nombre_archivo);
