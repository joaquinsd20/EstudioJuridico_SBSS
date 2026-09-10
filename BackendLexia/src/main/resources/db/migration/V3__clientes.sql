-- =============================================================================
-- LEXIA — V3: Clientes, contactos y contrapartes  (RF-03, RF-04 / HU-03, HU-04)
-- =============================================================================
-- El documento de identidad se guarda en DOS columnas (tipo + numero).
-- Las versiones anteriores usaban 'DNI-12345678' con el prefijo dentro del
-- valor, lo que impide la busqueda exacta del filtro "DNI/RUC" de la pantalla
-- de Clientes y hace imposible validar 8 vs 11 digitos.
-- =============================================================================

CREATE TABLE clientes (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid_publico            CHAR(36)        NOT NULL DEFAULT (UUID()),
    estudio_id              INT UNSIGNED    NOT NULL,
    tipo_persona            VARCHAR(20)     NOT NULL,
    tipo_documento          VARCHAR(20)     NOT NULL,
    numero_documento        VARCHAR(20)     NOT NULL COMMENT 'Columna DNI/RUC de la lista',
    -- Persona natural
    nombres                 VARCHAR(100)    NULL,
    apellido_paterno        VARCHAR(80)     NULL,
    apellido_materno        VARCHAR(80)     NULL,
    -- Persona juridica o entidad
    razon_social            VARCHAR(200)    NULL,
    nombre_comercial        VARCHAR(200)    NULL,
    -- Una sola columna para el buscador, sin importar el tipo de persona
    nombre_busqueda         VARCHAR(255)    GENERATED ALWAYS AS (
                                COALESCE(razon_social,
                                    CONCAT_WS(' ', apellido_paterno, apellido_materno, nombres))
                            ) STORED,
    email                   VARCHAR(150)    NULL,
    telefono_principal      VARCHAR(30)     NULL,
    telefono_secundario     VARCHAR(30)     NULL,
    direccion               VARCHAR(250)    NULL,
    distrito                VARCHAR(80)     NULL,
    provincia               VARCHAR(80)     NULL,
    departamento            VARCHAR(80)     NULL,
    estado                  VARCHAR(20)     NOT NULL DEFAULT 'PROSPECTO'
                            COMMENT 'Filtro "Estado del Cliente" de la pantalla',
    origen_captacion        VARCHAR(50)     NULL,
    situacion_financiera    VARCHAR(20)     NULL
                            COMMENT 'Apreciacion del §5. Distinta del Estado de Cuenta calculado',
    abogado_responsable_id  BIGINT UNSIGNED NULL,
    fecha_ultimo_contacto   DATETIME(3)     NULL
                            COMMENT 'Columna "Ultimo Contacto". La actualiza el servicio',
    observaciones           TEXT            NULL,
    -- auditoria
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_creacion_id     BIGINT UNSIGNED NULL,
    fecha_modificacion      DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    usuario_modificacion_id BIGINT UNSIGNED NULL,
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_clientes_uuid (uuid_publico),
    UNIQUE KEY uq_clientes_documento (estudio_id, tipo_documento, numero_documento),
    KEY ix_clientes_busqueda (estudio_id, nombre_busqueda, estado),
    KEY ix_clientes_responsable (abogado_responsable_id, activo),
    KEY ix_clientes_estado (estudio_id, estado, activo),
    KEY ix_clientes_documento_busq (numero_documento),
    FULLTEXT KEY ft_clientes_nombre (nombre_busqueda),
    CONSTRAINT fk_clientes_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_clientes_abogado
        FOREIGN KEY (abogado_responsable_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_clientes_creador
        FOREIGN KEY (usuario_creacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_clientes_modificador
        FOREIGN KEY (usuario_modificacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_clientes_tipo_persona CHECK (tipo_persona IN
        ('NATURAL','JURIDICA','ENTIDAD_PUBLICA')),
    CONSTRAINT ck_clientes_tipo_doc CHECK (tipo_documento IN
        ('DNI','RUC','CE','PASAPORTE','PARTIDA_REGISTRAL')),
    CONSTRAINT ck_clientes_estado CHECK (estado IN
        ('PROSPECTO','ACTIVO','INACTIVO','ANTIGUO')),
    CONSTRAINT ck_clientes_sit_fin CHECK (situacion_financiera IS NULL
        OR situacion_financiera IN ('SOLIDA','ESTABLE','REGULAR','RIESGO','MOROSA')),
    -- Coherencia identidad <-> tipo de persona
    CONSTRAINT ck_clientes_identidad CHECK (
           (tipo_persona =  'NATURAL' AND nombres IS NOT NULL AND apellido_paterno IS NOT NULL)
        OR (tipo_persona <> 'NATURAL' AND razon_social IS NOT NULL)
    ),
    -- Longitud del documento segun su tipo
    CONSTRAINT ck_clientes_documento_formato CHECK (
           (tipo_documento = 'DNI' AND numero_documento REGEXP '^[0-9]{8}$')
        OR (tipo_documento = 'RUC' AND numero_documento REGEXP '^[0-9]{11}$')
        OR (tipo_documento NOT IN ('DNI','RUC'))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- "Expedientes Vinculados" y "Estado de Cuenta" NO son columnas: el primero es
-- un COUNT y el segundo saldra del modulo de facturacion cuando exista.

-- FK diferida de V1: el usuario del portal apunta a su cliente
ALTER TABLE usuarios
    ADD CONSTRAINT fk_usuarios_cliente
        FOREIGN KEY (cliente_id) REFERENCES clientes (id)
        ON DELETE SET NULL ON UPDATE CASCADE;

-- -----------------------------------------------------------------------------
-- cliente_contactos — §5 "representantes / contactos autorizados".
-- numero_documento tambien se cruza en la busqueda de conflicto de intereses.
-- -----------------------------------------------------------------------------
CREATE TABLE cliente_contactos (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    cliente_id              BIGINT UNSIGNED NOT NULL,
    nombre_completo         VARCHAR(200)    NOT NULL,
    tipo_documento          VARCHAR(20)     NULL,
    numero_documento        VARCHAR(20)     NULL,
    cargo                   VARCHAR(100)    NULL,
    email                   VARCHAR(150)    NULL,
    telefono                VARCHAR(30)     NULL,
    es_representante_legal  TINYINT(1)      NOT NULL DEFAULT 0,
    puede_recibir_documentos TINYINT(1)     NOT NULL DEFAULT 0,
    numero_poder            VARCHAR(60)     NULL COMMENT 'Partida registral del poder',
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_creacion_id     BIGINT UNSIGNED NULL,
    fecha_modificacion      DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY ix_contactos_cliente (cliente_id, activo),
    KEY ix_contactos_documento (numero_documento),
    KEY ix_contactos_nombre (nombre_completo),
    CONSTRAINT fk_contactos_cliente
        FOREIGN KEY (cliente_id) REFERENCES clientes (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_contactos_creador
        FOREIGN KEY (usuario_creacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_contactos_tipo_doc CHECK (tipo_documento IS NULL
        OR tipo_documento IN ('DNI','CE','PASAPORTE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- contrapartes — tabla separada de clientes a proposito: la contraparte de hoy
-- puede ser el cliente de mañana, y ahi es donde salta el conflicto de
-- intereses (RF-04 / HU-04). El documento puede desconocerse al inicio.
-- -----------------------------------------------------------------------------
CREATE TABLE contrapartes (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid_publico            CHAR(36)        NOT NULL DEFAULT (UUID()),
    estudio_id              INT UNSIGNED    NOT NULL,
    tipo_persona            VARCHAR(20)     NOT NULL,
    tipo_documento          VARCHAR(20)     NULL,
    numero_documento        VARCHAR(20)     NULL,
    nombre_completo         VARCHAR(200)    NOT NULL,
    abogado_contrario       VARCHAR(200)    NULL,
    colegiatura_contraria   VARCHAR(30)     NULL,
    empresa_vinculada       VARCHAR(200)    NULL
                            COMMENT 'Grupo economico: amplia la busqueda de conflictos',
    observaciones           TEXT            NULL,
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_creacion_id     BIGINT UNSIGNED NULL,
    fecha_modificacion      DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_contrapartes_uuid (uuid_publico),
    KEY ix_contrapartes_documento (estudio_id, numero_documento),
    KEY ix_contrapartes_nombre (estudio_id, nombre_completo),
    FULLTEXT KEY ft_contrapartes_nombre (nombre_completo, empresa_vinculada),
    CONSTRAINT fk_contrapartes_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_contrapartes_creador
        FOREIGN KEY (usuario_creacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_contrapartes_tipo_persona CHECK (tipo_persona IN
        ('NATURAL','JURIDICA','ENTIDAD_PUBLICA')),
    CONSTRAINT ck_contrapartes_tipo_doc CHECK (tipo_documento IS NULL
        OR tipo_documento IN ('DNI','RUC','CE','PASAPORTE','PARTIDA_REGISTRAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
