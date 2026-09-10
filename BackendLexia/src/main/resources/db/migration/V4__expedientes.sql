-- =============================================================================
-- LEXIA — V4: Expedientes, equipo asignado y contrapartes del caso
--             (RF-05, RF-02 / HU-05, HU-02)
-- =============================================================================
-- Regla de oro, eslabon 1 -> 2: cliente_id es NOT NULL.
-- Ningun compromiso sin responsable: abogado_responsable_id es NOT NULL.
--
-- numero_expediente admite NULL (un prospecto o un arbitraje aun no lo tiene) y
-- NO lleva un CHECK con el formato CEJ. El estudio maneja procesos
-- administrativos, arbitrales y notariales que no siguen esa numeracion, y una
-- restriccion rigida los rechazaria. El formato se valida en la aplicacion, que
-- puede ADVERTIR sin bloquear.
-- =============================================================================

CREATE TABLE expedientes (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid_publico            CHAR(36)        NOT NULL DEFAULT (UUID()),
    estudio_id              INT UNSIGNED    NOT NULL,
    -- Identificacion
    codigo_interno          VARCHAR(30)     NOT NULL COMMENT 'Codigo del estudio: EXP-2026-0142',
    numero_expediente       VARCHAR(50)     NULL
                            COMMENT 'Formato CEJ cuando aplica: 01452-2026-0-1801-JR-FT-02',
    numero_correlativo      VARCHAR(10)     NULL,
    anio                    SMALLINT UNSIGNED NOT NULL,
    -- Partes (REGLA DE ORO)
    cliente_id              BIGINT UNSIGNED NOT NULL
                            COMMENT 'NOT NULL: no hay expediente sin cliente',
    -- Clasificacion
    especialidad_id         SMALLINT UNSIGNED NOT NULL,
    materia_id              SMALLINT UNSIGNED NULL COMMENT 'Columna "Tipo de Proceso"',
    organo_id               SMALLINT UNSIGNED NULL COMMENT 'Columna "Juzgado/Fiscalia"',
    estado_id               SMALLINT UNSIGNED NOT NULL COMMENT 'Badge de estado',
    etapa_procesal          VARCHAR(60)     NULL,
    instancia               VARCHAR(20)     NULL,
    -- Responsables
    abogado_responsable_id  BIGINT UNSIGNED NOT NULL COMMENT 'NOT NULL: nunca sin responsable',
    socio_supervisor_id     BIGINT UNSIGNED NULL,
    -- Gestion
    nivel_riesgo            VARCHAR(10)     NOT NULL DEFAULT 'MEDIO',
    cuantia                 DECIMAL(18,2)   NULL COMMENT 'DECIMAL, nunca FLOAT',
    moneda                  CHAR(3)         NOT NULL DEFAULT 'PEN',
    -- Fechas
    fecha_inicio            DATE            NOT NULL,
    fecha_ingreso_estudio   DATE            NOT NULL DEFAULT (CURRENT_DATE),
    fecha_ultima_actuacion  DATETIME(3)     NULL
                            COMMENT 'DESNORMALIZADO a proposito: columna "Ultima Actuacion"',
    fecha_cierre            DATE            NULL,
    resultado               VARCHAR(20)     NULL,
    -- Seguridad (RF-02 / HU-02)
    es_confidencial         TINYINT(1)      NOT NULL DEFAULT 0
                            COMMENT 'Roles operativos no acceden sin asignacion explicita',
    resumen                 TEXT            NULL,
    -- auditoria
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_creacion_id     BIGINT UNSIGNED NULL,
    fecha_modificacion      DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    usuario_modificacion_id BIGINT UNSIGNED NULL,
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_expedientes_uuid   (uuid_publico),
    UNIQUE KEY uq_expedientes_codigo (estudio_id, codigo_interno),
    -- En MySQL un indice UNIQUE admite multiples NULL, asi que esto ya permite
    -- varios expedientes sin numero CEJ sin necesidad de un indice filtrado.
    UNIQUE KEY uq_expedientes_numero (estudio_id, numero_expediente),
    KEY ix_expedientes_responsable  (abogado_responsable_id, estado_id, activo),
    KEY ix_expedientes_cliente      (cliente_id, activo),
    KEY ix_expedientes_estado       (estudio_id, estado_id, activo),
    KEY ix_expedientes_especialidad (especialidad_id, estado_id),
    KEY ix_expedientes_ultima_act   (fecha_ultima_actuacion),
    KEY ix_expedientes_confidencial (es_confidencial, estudio_id),
    CONSTRAINT fk_expedientes_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_expedientes_cliente
        FOREIGN KEY (cliente_id) REFERENCES clientes (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_expedientes_especialidad
        FOREIGN KEY (especialidad_id) REFERENCES especialidades (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_expedientes_materia
        FOREIGN KEY (materia_id) REFERENCES materias (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_expedientes_organo
        FOREIGN KEY (organo_id) REFERENCES organos_jurisdiccionales (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_expedientes_estado
        FOREIGN KEY (estado_id) REFERENCES estados_expediente (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_expedientes_abogado
        FOREIGN KEY (abogado_responsable_id) REFERENCES usuarios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_expedientes_socio
        FOREIGN KEY (socio_supervisor_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_expedientes_creador
        FOREIGN KEY (usuario_creacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_expedientes_modificador
        FOREIGN KEY (usuario_modificacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_expedientes_riesgo CHECK (nivel_riesgo IN
        ('BAJO','MEDIO','ALTO','CRITICO')),
    CONSTRAINT ck_expedientes_instancia CHECK (instancia IS NULL OR instancia IN
        ('PRIMERA','SEGUNDA','CASACION','ADMINISTRATIVA','ARBITRAL')),
    CONSTRAINT ck_expedientes_resultado CHECK (resultado IS NULL OR resultado IN
        ('GANADO','PERDIDO','CONCILIADO','DESISTIDO','ARCHIVADO','DERIVADO')),
    CONSTRAINT ck_expedientes_cuantia CHECK (cuantia IS NULL OR cuantia >= 0),
    CONSTRAINT ck_expedientes_fechas CHECK (fecha_cierre IS NULL OR fecha_cierre >= fecha_inicio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- expediente_equipo (M:N) — "Equipo asignado: Ana Morales, Luis Torres".
-- Es la base del control de acceso por expediente que exige RF-02 / HU-02, y
-- guarda el historial de cambios de responsable que pide el §6.
-- -----------------------------------------------------------------------------
CREATE TABLE expediente_equipo (
    expediente_id           BIGINT UNSIGNED NOT NULL,
    usuario_id              BIGINT UNSIGNED NOT NULL,
    rol_en_expediente       VARCHAR(20)     NOT NULL,
    puede_editar            TINYINT(1)      NOT NULL DEFAULT 1,
    fecha_asignacion        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    fecha_desasignacion     DATETIME(3)     NULL,
    usuario_asignacion_id   BIGINT UNSIGNED NULL,
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (expediente_id, usuario_id),
    KEY ix_equipo_usuario (usuario_id, activo),
    CONSTRAINT fk_equipo_expediente
        FOREIGN KEY (expediente_id) REFERENCES expedientes (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_equipo_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_equipo_asignador
        FOREIGN KEY (usuario_asignacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_equipo_rol CHECK (rol_en_expediente IN
        ('RESPONSABLE','APOYO','SUPERVISOR','ASISTENTE','OBSERVADOR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- expediente_contrapartes (M:N) — las versiones anteriores admitian una sola
-- contraparte por expediente. El litisconsorcio pasivo es habitual.
-- -----------------------------------------------------------------------------
CREATE TABLE expediente_contrapartes (
    expediente_id           BIGINT UNSIGNED NOT NULL,
    contraparte_id          BIGINT UNSIGNED NOT NULL,
    rol_procesal            VARCHAR(25)     NOT NULL,
    observaciones           VARCHAR(250)    NULL,
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (expediente_id, contraparte_id),
    KEY ix_exp_contrapartes_contraparte (contraparte_id),
    CONSTRAINT fk_exp_contrapartes_expediente
        FOREIGN KEY (expediente_id) REFERENCES expedientes (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_exp_contrapartes_contraparte
        FOREIGN KEY (contraparte_id) REFERENCES contrapartes (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_exp_contrapartes_rol CHECK (rol_procesal IN
        ('DEMANDANTE','DEMANDADO','TERCERO','AGRAVIADO','IMPUTADO','LITISCONSORTE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
