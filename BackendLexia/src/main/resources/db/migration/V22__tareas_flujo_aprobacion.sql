-- =============================================================================
-- LEXIA — V22: gestion funcional de tareas y flujo de aprobacion.
-- =============================================================================
-- Renumerada de V20 a V22. Llego como V20 y en `main` ya habia otra V20
-- (`V20__permisos_facturacion.sql`, PR #44): git no vio conflicto porque son
-- dos archivos con nombres distintos, y Flyway aborto el arranque de todos:
--
--     org.flywaydb.core.api.FlywayException: Found more than one migration
--     with version 20
--
-- V21 tampoco estaba libre (`V21__subtareas_uuid.sql`), asi que va a V22.
-- Reparacion para quien ya hubiera aplicado la V20 antigua: borrar su fila de
-- `flyway_schema_history` y deshacer lo que creo (la columna etapa_flujo, su
-- CHECK, la tabla tarea_historial y los cuatro indices), y arrancar de nuevo.
-- Ver db/migration/LEEME.md: es la tercera vez que pasa lo mismo.
-- =============================================================================

-- LEXIA — Gestión funcional de tareas y flujo de aprobación
ALTER TABLE tareas
    ADD COLUMN etapa_flujo VARCHAR(20) NOT NULL DEFAULT 'ELABORAR' AFTER estado,
    ADD CONSTRAINT ck_tareas_etapa_flujo CHECK (etapa_flujo IN
        ('ELABORAR','REVISAR','CORREGIR','APROBAR','FIRMAR','PRESENTAR','CARGAR_CARGO','CERRAR'));

CREATE TABLE tarea_historial (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tarea_id            BIGINT UNSIGNED NOT NULL,
    usuario_id          BIGINT UNSIGNED NOT NULL,
    estado_anterior     VARCHAR(20) NULL,
    estado_nuevo        VARCHAR(20) NOT NULL,
    etapa_anterior      VARCHAR(20) NULL,
    etapa_nueva         VARCHAR(20) NOT NULL,
    observacion         VARCHAR(500) NULL,
    fecha_cambio        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_tarea_historial_tarea (tarea_id, fecha_cambio DESC),
    CONSTRAINT fk_tarea_historial_tarea FOREIGN KEY (tarea_id) REFERENCES tareas(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_tarea_historial_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ============================================================
-- OPTIMIZACIÓN DE CONSULTAS FRECUENTES DEL MÓDULO TAREAS
-- Paginación, filtros y conteos asociados.
-- ============================================================
ALTER TABLE tareas
    ADD KEY ix_tareas_exp_activo_estado_prioridad_fecha
        (expediente_id, activo, estado, prioridad, fecha_limite, orden_tablero);

ALTER TABLE subtareas
    ADD KEY ix_subtareas_tarea_completada
        (tarea_id, completada);

ALTER TABLE documentos
    ADD KEY ix_documentos_tarea_activo_papelera
        (tarea_id, activo, en_papelera);

ALTER TABLE comentarios
    ADD KEY ix_comentarios_tarea_activo_fecha
        (tarea_id, activo, fecha_creacion DESC);


-- =============================================================================
-- VERIFICACION (regla 11 de CLAUDE.md)
-- =============================================================================
-- La columna y su valor por defecto.
--   SELECT column_name, column_default FROM information_schema.columns
--    WHERE table_schema = DATABASE() AND table_name = 'tareas'
--      AND column_name = 'etapa_flujo';
--   -- esperado: etapa_flujo / ELABORAR
--
-- Todas las tareas existentes arrancan en la primera etapa.
--   SELECT etapa_flujo, COUNT(*) FROM tareas GROUP BY etapa_flujo;
--   -- esperado: solo ELABORAR
--
-- La tabla de historial y los cuatro indices de este modulo.
--   SELECT COUNT(*) FROM information_schema.tables
--    WHERE table_schema = DATABASE() AND table_name = 'tarea_historial';
--   -- esperado: 1
--   SELECT index_name FROM information_schema.statistics
--    WHERE table_schema = DATABASE() AND index_name IN
--          ('ix_tareas_exp_activo_estado_prioridad_fecha',
--           'ix_subtareas_tarea_completada',
--           'ix_documentos_tarea_activo_papelera',
--           'ix_comentarios_tarea_activo_fecha')
--    GROUP BY index_name;
--   -- esperado: los 4
--
-- PRUEBA NEGATIVA: una etapa que no existe se rechaza.
--   UPDATE tareas SET etapa_flujo = 'INVENTADA' WHERE id = (SELECT MIN(id) FROM tareas);
--   -- esperado: ERROR 3819 (Check constraint 'ck_tareas_etapa_flujo' is violated)
