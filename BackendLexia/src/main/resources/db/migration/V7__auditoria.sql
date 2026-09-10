-- =============================================================================
-- LEXIA — V7: Auditoria  (RNF-09 / HU-13)
-- =============================================================================
-- Tabla APPEND-ONLY: no se actualiza ni se borra nunca.
-- Las versiones anteriores guardaban solo accion + detalles en texto libre, con
-- lo que no se podia responder "muestrame el historial de cambios del
-- expediente 5". El RNF-09 exige un historial inmutable por registro.
-- =============================================================================

CREATE TABLE auditoria (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    estudio_id              INT UNSIGNED    NOT NULL,
    usuario_id              BIGINT UNSIGNED NULL COMMENT 'NULL en acciones del sistema',
    -- A que registro apunta: esto es lo que hace consultable la auditoria
    entidad                 VARCHAR(60)     NOT NULL COMMENT 'expedientes, plazos, documentos...',
    registro_id             BIGINT UNSIGNED NOT NULL,
    expediente_id           BIGINT UNSIGNED NULL COMMENT 'Denormalizado: filtra por caso',
    accion                  VARCHAR(20)     NOT NULL,
    -- Antes y despues, en JSON. MySQL 8 valida que sea JSON bien formado.
    valores_anteriores      JSON            NULL,
    valores_nuevos          JSON            NULL,
    descripcion             VARCHAR(500)    NULL,
    -- Contexto de la peticion
    ip_origen               VARCHAR(45)     NULL COMMENT 'Soporta IPv6',
    user_agent              VARCHAR(250)    NULL,
    exito                   TINYINT(1)      NOT NULL DEFAULT 1
                            COMMENT '0 = intento denegado. HU-02 exige registrarlos',
    fecha                   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_auditoria_entidad (entidad, registro_id, fecha DESC),
    KEY ix_auditoria_usuario (usuario_id, fecha DESC),
    KEY ix_auditoria_expediente (expediente_id, fecha DESC),
    KEY ix_auditoria_fecha (fecha DESC),
    KEY ix_auditoria_accion (accion, fecha DESC),
    KEY ix_auditoria_denegados (exito, fecha DESC),
    CONSTRAINT fk_auditoria_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_auditoria_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_auditoria_accion CHECK (accion IN
        ('CREAR','ACTUALIZAR','ELIMINAR','LEER','DESCARGAR','EXPORTAR',
         'LOGIN','LOGOUT','LOGIN_FALLIDO','APROBAR','RECHAZAR','ANULAR','ACCESO_DENEGADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Append-only. Particionar por año a partir del segundo año.';

-- Nota de implementacion: la auditoria se escribe desde Spring con un
-- @EntityListener sobre EntidadAuditable, no con triggers de MySQL. Asi queda
-- registrado el usuario autenticado, la IP y el user-agent, que la base no conoce.
