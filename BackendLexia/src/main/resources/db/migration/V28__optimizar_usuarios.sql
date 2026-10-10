-- =============================================================================
-- LEXIA — V28: optimizacion del panel de Usuarios
-- =============================================================================
-- No cambia reglas funcionales. Solo agrega indices para el listado paginado y
-- para la ficha/carga de trabajo del usuario.
-- =============================================================================

-- Panel: frontera por estudio/tipo + estado/activo + orden por nombre.
ALTER TABLE usuarios
    ADD INDEX ix_usuarios_panel
        (estudio_id, tipo_usuario, activo DESC, nombre_completo, id);

-- La ficha cuenta las versiones cargadas por cada usuario. V6 tenia indices por
-- documento/hash, pero no por usuario_carga_id.
ALTER TABLE documento_versiones
    ADD INDEX ix_versiones_usuario_carga
        (usuario_carga_id, fecha_carga DESC);
