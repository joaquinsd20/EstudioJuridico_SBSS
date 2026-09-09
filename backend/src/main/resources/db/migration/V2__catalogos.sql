-- =============================================================================
-- LEXIA — V2: Catalogos configurables
-- =============================================================================
-- Criterio de diseño:
--   Lo que el ESTUDIO configura            -> tabla catalogo (esta migracion)
--   Lo que la LOGICA DE NEGOCIO interpreta -> VARCHAR + CHECK (V4, V5, V6)
-- El §6 del PDF de requisitos dice "estados configurables", por eso
-- estados_expediente es una tabla y no un CHECK.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- especialidades — grafico "Expedientes por especialidad" del Dashboard.
-- color_hex vive en la BD y no en el CSS de Angular: si el estudio agrega una
-- especialidad desde Configuracion, el grafico la pinta sin recompilar el front.
-- -----------------------------------------------------------------------------
CREATE TABLE especialidades (
    id                      SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    estudio_id              INT UNSIGNED    NOT NULL,
    codigo                  VARCHAR(20)     NOT NULL,
    nombre                  VARCHAR(80)     NOT NULL,
    color_hex               CHAR(7)         NOT NULL DEFAULT '#7C3AED',
    orden                   SMALLINT        NOT NULL DEFAULT 0,
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_especialidades_codigo (estudio_id, codigo),
    CONSTRAINT fk_especialidades_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_especialidades_color CHECK (color_hex REGEXP '^#[0-9A-Fa-f]{6}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- materias — columna "Tipo de Proceso" de la lista de Expedientes
-- -----------------------------------------------------------------------------
CREATE TABLE materias (
    id                      SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    especialidad_id         SMALLINT UNSIGNED NOT NULL,
    nombre                  VARCHAR(120)    NOT NULL,
    via_procedimental       VARCHAR(60)     NULL,
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_materias_nombre (especialidad_id, nombre),
    CONSTRAINT fk_materias_especialidad
        FOREIGN KEY (especialidad_id) REFERENCES especialidades (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- organos_jurisdiccionales — filtro "Juzgado/Sala" y grafico "Juzgados con
-- Mayor Actividad" del Monitor Judicial
-- -----------------------------------------------------------------------------
CREATE TABLE organos_jurisdiccionales (
    id                      SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    estudio_id              INT UNSIGNED    NOT NULL,
    tipo                    VARCHAR(30)     NOT NULL,
    nombre                  VARCHAR(150)    NOT NULL,
    distrito_judicial       VARCHAR(80)     NULL,
    instancia               VARCHAR(20)     NULL,
    direccion               VARCHAR(250)    NULL,
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_organos_nombre (estudio_id, nombre),
    KEY ix_organos_distrito (distrito_judicial),
    CONSTRAINT fk_organos_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_organos_tipo CHECK (tipo IN
        ('JUZGADO','SALA','FISCALIA','TRIBUNAL','ENTIDAD_ADMINISTRATIVA','ARBITRAJE')),
    CONSTRAINT ck_organos_instancia CHECK (instancia IS NULL OR instancia IN
        ('PRIMERA','SEGUNDA','SUPREMA','UNICA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- estados_expediente — los 12 estados configurables del §6
-- -----------------------------------------------------------------------------
CREATE TABLE estados_expediente (
    id                      SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    estudio_id              INT UNSIGNED    NOT NULL,
    codigo                  VARCHAR(20)     NOT NULL,
    nombre                  VARCHAR(50)     NOT NULL COMMENT 'Texto del badge',
    color_hex               CHAR(7)         NOT NULL DEFAULT '#6B7280',
    es_estado_final         TINYINT(1)      NOT NULL DEFAULT 0
                            COMMENT '1 = bloquea nuevas actuaciones',
    cuenta_como_activo      TINYINT(1)      NOT NULL DEFAULT 1
                            COMMENT 'Alimenta la tarjeta "Activos 18 (64%)"',
    orden                   SMALLINT        NOT NULL DEFAULT 0,
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_estados_exp_codigo (estudio_id, codigo),
    CONSTRAINT fk_estados_exp_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_estados_exp_color CHECK (color_hex REGEXP '^#[0-9A-Fa-f]{6}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- tipos_actuacion — EL MOTOR DE LA AUTOMATIZACION (RF-08 / §17 del PDF).
-- Los cuatro flags deciden que se dispara al registrar una actuacion, asi la
-- regla vive en datos configurables y no codificada en Java.
-- -----------------------------------------------------------------------------
CREATE TABLE tipos_actuacion (
    id                      SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    estudio_id              INT UNSIGNED    NOT NULL,
    codigo                  VARCHAR(30)     NOT NULL,
    nombre                  VARCHAR(100)    NOT NULL,
    categoria               VARCHAR(25)     NOT NULL
                            COMMENT 'Alimenta "Distribucion de Tiempo por Actividad"',
    genera_plazo            TINYINT(1)      NOT NULL DEFAULT 0,
    genera_tarea            TINYINT(1)      NOT NULL DEFAULT 0,
    genera_evento_agenda    TINYINT(1)      NOT NULL DEFAULT 0,
    requiere_documento      TINYINT(1)      NOT NULL DEFAULT 0,
    icono                   VARCHAR(40)     NULL,
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tipos_act_codigo (estudio_id, codigo),
    CONSTRAINT fk_tipos_act_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_tipos_act_categoria CHECK (categoria IN
        ('ESCRITO','AUDIENCIA','DILIGENCIA','NOTIFICACION','COMUNICACION','ADMINISTRATIVO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- tipos_plazo — desplegable "Tipo de Plazo": Contestacion / Probatorio / Recurso
-- -----------------------------------------------------------------------------
CREATE TABLE tipos_plazo (
    id                      SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    estudio_id              INT UNSIGNED    NOT NULL,
    codigo                  VARCHAR(30)     NOT NULL,
    nombre                  VARCHAR(100)    NOT NULL,
    dias_por_defecto        SMALLINT UNSIGNED NULL,
    tipo_computo_defecto    VARCHAR(15)     NOT NULL DEFAULT 'HABILES',
    nivel_riesgo_defecto    VARCHAR(10)     NOT NULL DEFAULT 'ALTO',
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tipos_plazo_codigo (estudio_id, codigo),
    CONSTRAINT fk_tipos_plazo_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_tipos_plazo_computo CHECK (tipo_computo_defecto IN ('HABILES','CALENDARIO')),
    CONSTRAINT ck_tipos_plazo_riesgo  CHECK (nivel_riesgo_defecto IN
        ('BAJO','MEDIO','ALTO','CRITICO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =============================================================================
-- calendario_judicial — LA TABLA SIN LA CUAL NO EXISTE EL CONTROL DE PLAZOS
-- =============================================================================
-- Sin feriados ni suspensiones, tipo_computo='HABILES' es una etiqueta sin
-- calculo detras. Las dos versiones anteriores del proyecto no la tenian y por
-- eso 8 de cada 10 plazos "habiles" estaban mal calculados.
--
-- FLUJO DE APROBACION (decision del equipo):
--   FERIADOS (predecibles, publicados por norma): los carga el Administrador
--     una vez al año, o vienen en la semilla. Riesgo bajo.
--   SUSPENSIONES DE PLAZOS (Res. Adm. del PJ, huelgas, paros): aparecen sin
--     aviso y mueven TODOS los plazos del estudio. Las REGISTRA quien las
--     detecta (Asistente legal o Abogado, que reciben las notificaciones), pero
--     NO surten efecto hasta que un SOCIO o el ADMINISTRADOR las APRUEBA.
--
-- Regla dura: CalculadoraPlazos solo considera filas con estado = 'APROBADO'.
-- Una fila PROPUESTA no altera ningun calculo.
-- =============================================================================
CREATE TABLE calendario_judicial (
    id                      INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    estudio_id              INT UNSIGNED    NULL COMMENT 'NULL = feriado oficial nacional',
    fecha_inicio            DATE            NOT NULL,
    fecha_fin               DATE            NOT NULL COMMENT 'Igual a fecha_inicio si es un solo dia',
    tipo                    VARCHAR(30)     NOT NULL,
    descripcion             VARCHAR(200)    NOT NULL,
    norma_referencia        VARCHAR(150)    NULL COMMENT 'Res. Adm. N.o ... que la sustenta',
    ambito                  VARCHAR(80)     NULL COMMENT 'NULL = nacional; si no, distrito judicial',
    -- Flujo de aprobacion
    estado                  VARCHAR(15)     NOT NULL DEFAULT 'PROPUESTO',
    usuario_registro_id     BIGINT UNSIGNED NOT NULL COMMENT 'Quien la detecto y cargo',
    usuario_aprobacion_id   BIGINT UNSIGNED NULL COMMENT 'Socio o Administrador',
    fecha_aprobacion        DATETIME(3)     NULL,
    motivo_rechazo          VARCHAR(250)    NULL,
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    fecha_modificacion      DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    -- El indice que consulta CalculadoraPlazos en cada calculo
    KEY ix_calendario_vigente (estado, fecha_inicio, fecha_fin),
    KEY ix_calendario_estudio (estudio_id),
    KEY ix_calendario_pendientes (estado, fecha_creacion),
    CONSTRAINT fk_calendario_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_calendario_registro
        FOREIGN KEY (usuario_registro_id) REFERENCES usuarios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_calendario_aprobacion
        FOREIGN KEY (usuario_aprobacion_id) REFERENCES usuarios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_calendario_tipo CHECK (tipo IN
        ('FERIADO_NACIONAL','FERIADO_REGIONAL','DIA_NO_LABORABLE',
         'SUSPENSION_PLAZOS','HUELGA_JUDICIAL')),
    CONSTRAINT ck_calendario_estado CHECK (estado IN
        ('PROPUESTO','APROBADO','RECHAZADO')),
    CONSTRAINT ck_calendario_rango CHECK (fecha_fin >= fecha_inicio),
    CONSTRAINT ck_calendario_rechazo CHECK (estado <> 'RECHAZADO' OR motivo_rechazo IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Solo las filas APROBADO afectan el computo de plazos.';

-- -----------------------------------------------------------------------------
-- etiquetas — §10 exige etiquetar documentos. Tabla puente en V6.
-- -----------------------------------------------------------------------------
CREATE TABLE etiquetas (
    id                      SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    estudio_id              INT UNSIGNED    NOT NULL,
    nombre                  VARCHAR(50)     NOT NULL,
    color_hex               CHAR(7)         NOT NULL DEFAULT '#6B7280',
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_etiquetas_nombre (estudio_id, nombre),
    CONSTRAINT fk_etiquetas_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_etiquetas_color CHECK (color_hex REGEXP '^#[0-9A-Fa-f]{6}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- configuracion — parametros del sistema, clave/valor.
-- NO se usa para catalogos: los estados y tipos tienen sus propias tablas
-- porque necesitan color, orden e integridad referencial. Guardar
-- 'ELABORAR,EN_PROGRESO,REVISION' como CSV aqui obliga a parsear cadenas en Java.
-- -----------------------------------------------------------------------------
CREATE TABLE configuracion (
    id                      SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    estudio_id              INT UNSIGNED    NOT NULL,
    clave                   VARCHAR(100)    NOT NULL,
    valor                   TEXT            NOT NULL,
    tipo_dato               VARCHAR(15)     NOT NULL DEFAULT 'TEXTO',
    descripcion             VARCHAR(250)    NULL,
    editable_por_usuario    TINYINT(1)      NOT NULL DEFAULT 1,
    fecha_modificacion      DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    usuario_modificacion_id BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_configuracion_clave (estudio_id, clave),
    CONSTRAINT fk_configuracion_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_configuracion_usuario
        FOREIGN KEY (usuario_modificacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_configuracion_tipo CHECK (tipo_dato IN
        ('TEXTO','NUMERO','BOOLEANO','JSON','FECHA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
