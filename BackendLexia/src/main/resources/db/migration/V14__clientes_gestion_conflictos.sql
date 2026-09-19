-- =============================================================================
-- LEXIA — V14: ficha ampliada de clientes y apoyo al conflicto de intereses
-- =============================================================================
-- Renumerada de V12 a V14: mientras esta migracion estaba en curso se mergearon
-- V12 (roles y permisos) y V13 (cambio de contrasena). Dos migraciones con la
-- misma version hacen que Flyway aborte el arranque, y git no lo detecta porque
-- los archivos se llaman distinto.
-- =============================================================================
-- V3 ya contiene clientes, representantes/contactos y contrapartes. Esta
-- migración agrega las relaciones (personas/empresas vinculadas) y un historial
-- directo de comunicaciones del cliente para completar la ficha del RF-03/RF-04.
-- =============================================================================

CREATE TABLE cliente_vinculos (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid_publico            CHAR(36)        NOT NULL DEFAULT (UUID()),
    cliente_id              BIGINT UNSIGNED NOT NULL,
    estudio_id              INT UNSIGNED    NOT NULL,
    tipo_persona            VARCHAR(20)     NOT NULL,
    nombre                  VARCHAR(200)    NOT NULL,
    tipo_documento          VARCHAR(20)     NULL,
    numero_documento        VARCHAR(20)     NULL,
    relacion                VARCHAR(80)     NULL,
    observaciones           TEXT            NULL,
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_creacion_id     BIGINT UNSIGNED NULL,
    fecha_modificacion      DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    usuario_modificacion_id BIGINT UNSIGNED NULL,
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cliente_vinculos_uuid (uuid_publico),
    KEY ix_cliente_vinculos_cliente (cliente_id, activo),
    KEY ix_cliente_vinculos_documento (estudio_id, numero_documento, activo),
    KEY ix_cliente_vinculos_nombre (estudio_id, nombre, activo),
    CONSTRAINT fk_cliente_vinculos_cliente
        FOREIGN KEY (cliente_id) REFERENCES clientes (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_cliente_vinculos_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_cliente_vinculos_creador
        FOREIGN KEY (usuario_creacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_cliente_vinculos_modificador
        FOREIGN KEY (usuario_modificacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_cliente_vinculos_tipo_persona CHECK (tipo_persona IN
        ('NATURAL','JURIDICA','ENTIDAD_PUBLICA')),
    CONSTRAINT ck_cliente_vinculos_tipo_doc CHECK (tipo_documento IS NULL OR tipo_documento IN
        ('DNI','RUC','CE','PASAPORTE','PARTIDA_REGISTRAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cliente_comunicaciones (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid_publico            CHAR(36)        NOT NULL DEFAULT (UUID()),
    cliente_id              BIGINT UNSIGNED NOT NULL,
    estudio_id              INT UNSIGNED    NOT NULL,
    tipo                    VARCHAR(20)     NOT NULL,
    asunto                  VARCHAR(200)    NOT NULL,
    detalle                 TEXT            NULL,
    canal                   VARCHAR(40)     NULL,
    fecha_comunicacion      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_creacion_id     BIGINT UNSIGNED NULL,
    fecha_modificacion      DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    usuario_modificacion_id BIGINT UNSIGNED NULL,
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cliente_comunicaciones_uuid (uuid_publico),
    KEY ix_cliente_comunicaciones_cliente (cliente_id, fecha_comunicacion, activo),
    KEY ix_cliente_comunicaciones_estudio (estudio_id, fecha_comunicacion),
    CONSTRAINT fk_cliente_comunicaciones_cliente
        FOREIGN KEY (cliente_id) REFERENCES clientes (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_cliente_comunicaciones_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_cliente_comunicaciones_creador
        FOREIGN KEY (usuario_creacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_cliente_comunicaciones_modificador
        FOREIGN KEY (usuario_modificacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_cliente_comunicaciones_tipo CHECK (tipo IN
        ('LLAMADA','EMAIL','WHATSAPP','REUNION','OTRO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
