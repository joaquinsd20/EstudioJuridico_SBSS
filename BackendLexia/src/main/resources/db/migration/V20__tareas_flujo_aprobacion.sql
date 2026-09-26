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
