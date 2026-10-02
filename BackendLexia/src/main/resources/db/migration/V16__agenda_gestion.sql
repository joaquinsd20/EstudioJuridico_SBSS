-- =============================================================================
-- LEXIA — V16: Gestión de Agenda
-- Completa eventos_agenda con procedencia/verificación y conserva el historial
-- de reprogramaciones. No incluye Monitor Judicial.
-- =============================================================================

ALTER TABLE eventos_agenda
    ADD COLUMN origen_informacion VARCHAR(15) NOT NULL DEFAULT 'MANUAL'
        COMMENT 'MANUAL, DESCARGADA, CORREO, CLIENTE o IA' AFTER color_hex,
    ADD COLUMN requiere_verificacion TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '1 cuando el evento o su información debe ser verificado' AFTER origen_informacion,
    ADD COLUMN usuario_modificacion_id BIGINT UNSIGNED NULL AFTER usuario_creacion_id,
    ADD KEY ix_eventos_verificacion (estudio_id, requiere_verificacion, fecha_inicio),
    ADD CONSTRAINT fk_eventos_modificador
        FOREIGN KEY (usuario_modificacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    ADD CONSTRAINT ck_eventos_origen CHECK (origen_informacion IN
        ('MANUAL','DESCARGADA','CORREO','CLIENTE','IA'));

CREATE TABLE evento_reprogramaciones (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    evento_id               BIGINT UNSIGNED NOT NULL,
    fecha_inicio_anterior   DATETIME        NOT NULL,
    fecha_fin_anterior      DATETIME        NOT NULL,
    fecha_inicio_nueva      DATETIME        NOT NULL,
    fecha_fin_nueva         DATETIME        NOT NULL,
    motivo                  VARCHAR(500)    NOT NULL,
    usuario_id              BIGINT UNSIGNED NULL,
    fecha_reprogramacion    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_reprogramaciones_evento (evento_id, fecha_reprogramacion DESC),
    CONSTRAINT fk_reprogramaciones_evento
        FOREIGN KEY (evento_id) REFERENCES eventos_agenda (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_reprogramaciones_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_reprogramaciones_fechas CHECK (fecha_fin_nueva >= fecha_inicio_nueva)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- Indices de escalabilidad para consultas por rango y filtros de agenda.
ALTER TABLE eventos_agenda
    ADD KEY ix_eventos_rango (estudio_id, activo, fecha_inicio, fecha_fin, estado),
    ADD KEY ix_eventos_tipo_estado_fecha (estudio_id, tipo, estado, fecha_inicio);

ALTER TABLE evento_participantes
    ADD KEY ix_participantes_usuario_evento (usuario_id, evento_id);
