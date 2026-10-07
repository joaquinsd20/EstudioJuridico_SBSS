-- ============================================================
-- LEXIA - OPTIMIZACION DEL MODULO DE REPORTES
-- V27
-- ============================================================
-- No modifica migraciones publicadas. Agrega indices para los predicados que
-- usa ReporteRepositoryJdbc: estudio + periodo + responsable y fechas reales
-- de honorarios, tareas y actuaciones.

CREATE INDEX ix_reportes_expedientes_periodo_responsable
    ON expedientes (estudio_id, fecha_inicio, abogado_responsable_id, materia_id, estado_id, id);

CREATE INDEX ix_reportes_honorarios_fecha_expediente
    ON honorarios (fecha_creacion, expediente_id, cliente_id);

CREATE INDEX ix_reportes_tareas_periodo_expediente
    ON tareas (activo, fecha_asignacion, expediente_id, estado, usuario_responsable_id);

CREATE INDEX ix_reportes_actuaciones_facturable_periodo
    ON actuaciones (activo, es_facturable, fecha_hora, expediente_id);
