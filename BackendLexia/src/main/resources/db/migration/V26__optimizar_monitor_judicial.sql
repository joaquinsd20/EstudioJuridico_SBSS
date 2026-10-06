-- =============================================================================
-- LEXIA — V26: Optimización escalable del Monitor Judicial
--
-- Objetivos:
--   * ordenar y paginar monitores por estudio sin recorrer toda la tabla;
--   * consultar historial por monitor/fecha con orden estable;
--   * acelerar verificaciones pendientes y agregaciones del panel;
--   * no modificar V17, que ya puede estar aplicada por Flyway.
-- =============================================================================

-- Listado principal: estudio + activo + orden cronologico.
CREATE INDEX ix_monitor_exp_estudio_activo_ultima
    ON monitor_expedientes (estudio_id, activo, ultima_consulta DESC, id DESC);

-- Historial de un monitor. El id al final hace estable el ORDER BY cuando dos
-- consultas comparten el mismo milisegundo.
CREATE INDEX ix_monitor_cons_monitor_fecha_id
    ON monitor_consultas (monitor_id, fecha_consulta DESC, id DESC);

-- Conteos de pendientes por monitor y consultas de verificacion.
CREATE INDEX ix_monitor_cons_monitor_verif_fecha
    ON monitor_consultas (monitor_id, estado_verificacion, fecha_consulta DESC, id DESC);

-- Resumen de movimientos/resoluciones recientes de los monitores visibles.
CREATE INDEX ix_monitor_cons_monitor_flags_fecha
    ON monitor_consultas (monitor_id, fecha_consulta DESC, hubo_movimiento, nueva_resolucion);
