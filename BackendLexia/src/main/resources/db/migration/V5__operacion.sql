-- =============================================================================
-- LEXIA — V5: Operacion. Actuaciones, resoluciones, plazos, alertas,
--             tareas, subtareas y agenda.
--             (RF-06 a RF-11 / HU-06 a HU-11)
-- =============================================================================
-- Aqui vive la cadena de oro completa:
--   Expediente -> Actuacion -> Plazo / Tarea -> Documento -> Alerta -> Seguimiento
-- Todas las FK hacia expedientes son NOT NULL: ningun dato critico queda huerfano.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- actuaciones — linea de tiempo del expediente (RF-06 / HU-06)
-- -----------------------------------------------------------------------------
CREATE TABLE actuaciones (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid_publico            CHAR(36)        NOT NULL DEFAULT (UUID()),
    expediente_id           BIGINT UNSIGNED NOT NULL COMMENT 'REGLA DE ORO',
    tipo_actuacion_id       SMALLINT UNSIGNED NOT NULL,
    fecha_hora              DATETIME        NOT NULL,
    asunto                  VARCHAR(200)    NOT NULL COMMENT 'Columna "Asunto/Tarea"',
    descripcion             TEXT            NULL,
    usuario_responsable_id  BIGINT UNSIGNED NOT NULL COMMENT 'Columna "Abogado Responsable"',
    estado                  VARCHAR(20)     NOT NULL DEFAULT 'PENDIENTE',
    resultado               TEXT            NULL,
    organo_id               SMALLINT UNSIGNED NULL,
    proxima_accion          VARCHAR(250)    NULL COMMENT 'Dispara la automatizacion RF-08',
    -- Metricas: "Distribucion de Tiempo por Actividad" y "Horas Facturables"
    tiempo_minutos          INT UNSIGNED    NULL,
    es_facturable           TINYINT(1)      NOT NULL DEFAULT 1,
    -- Trazabilidad del origen del dato (§12 del PDF)
    origen                  VARCHAR(20)     NOT NULL DEFAULT 'MANUAL',
    requiere_verificacion   TINYINT(1)      NOT NULL DEFAULT 0,
    actuacion_padre_id      BIGINT UNSIGNED NULL COMMENT 'Actuaciones derivadas',
    -- auditoria
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_creacion_id     BIGINT UNSIGNED NULL,
    fecha_modificacion      DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    usuario_modificacion_id BIGINT UNSIGNED NULL,
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_actuaciones_uuid (uuid_publico),
    KEY ix_actuaciones_expediente (expediente_id, fecha_hora DESC),
    KEY ix_actuaciones_responsable (usuario_responsable_id, estado),
    KEY ix_actuaciones_tipo (tipo_actuacion_id),
    KEY ix_actuaciones_origen (origen, requiere_verificacion),
    CONSTRAINT fk_actuaciones_expediente
        FOREIGN KEY (expediente_id) REFERENCES expedientes (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_actuaciones_tipo
        FOREIGN KEY (tipo_actuacion_id) REFERENCES tipos_actuacion (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_actuaciones_responsable
        FOREIGN KEY (usuario_responsable_id) REFERENCES usuarios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_actuaciones_organo
        FOREIGN KEY (organo_id) REFERENCES organos_jurisdiccionales (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_actuaciones_padre
        FOREIGN KEY (actuacion_padre_id) REFERENCES actuaciones (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_actuaciones_creador
        FOREIGN KEY (usuario_creacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_actuaciones_estado CHECK (estado IN
        ('PENDIENTE','EN_TRAMITE','COMPLETADO','CANCELADO')),
    CONSTRAINT ck_actuaciones_origen CHECK (origen IN
        ('MANUAL','MONITOR_JUDICIAL','CORREO','CLIENTE','IA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- resoluciones — tabla propia, enlazada a la actuacion que la registro, para que
-- la linea de tiempo del expediente siga saliendo de una sola consulta.
-- `vista` alimenta la tarjeta "Nuevas resoluciones — desde la ultima revision".
-- -----------------------------------------------------------------------------
CREATE TABLE resoluciones (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid_publico            CHAR(36)        NOT NULL DEFAULT (UUID()),
    expediente_id           BIGINT UNSIGNED NOT NULL,
    actuacion_id            BIGINT UNSIGNED NULL,
    numero_resolucion       VARCHAR(30)     NOT NULL,
    tipo_resolucion         VARCHAR(25)     NULL,
    fecha_resolucion        DATE            NULL,
    fecha_notificacion      DATE            NOT NULL COMMENT 'INICIA EL COMPUTO DEL PLAZO',
    sumilla                 TEXT            NULL,
    fuente                  VARCHAR(100)    NULL COMMENT 'Portal CEJ, casilla, mesa de partes',
    origen                  VARCHAR(20)     NOT NULL DEFAULT 'MANUAL',
    estado_verificacion     VARCHAR(15)     NOT NULL DEFAULT 'PENDIENTE',
    usuario_verificacion_id BIGINT UNSIGNED NULL,
    fecha_verificacion      DATETIME(3)     NULL,
    vista                   TINYINT(1)      NOT NULL DEFAULT 0,
    documento_id            BIGINT UNSIGNED NULL COMMENT 'FK añadida en V6',
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_creacion_id     BIGINT UNSIGNED NULL,
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_resoluciones_uuid (uuid_publico),
    UNIQUE KEY uq_resoluciones_numero (expediente_id, numero_resolucion),
    KEY ix_resoluciones_expediente (expediente_id, fecha_notificacion DESC),
    KEY ix_resoluciones_nuevas (vista, fecha_notificacion DESC),
    KEY ix_resoluciones_verificacion (estado_verificacion),
    CONSTRAINT fk_resoluciones_expediente
        FOREIGN KEY (expediente_id) REFERENCES expedientes (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_resoluciones_actuacion
        FOREIGN KEY (actuacion_id) REFERENCES actuaciones (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_resoluciones_verificador
        FOREIGN KEY (usuario_verificacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_resoluciones_creador
        FOREIGN KEY (usuario_creacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_resoluciones_tipo CHECK (tipo_resolucion IS NULL OR tipo_resolucion IN
        ('AUTO','SENTENCIA','DECRETO','PROVIDENCIA','CASACION','ACUERDO','RESOLUCION_ADMIN')),
    CONSTRAINT ck_resoluciones_origen CHECK (origen IN
        ('MANUAL','MONITOR_JUDICIAL','CORREO','CLIENTE','IA')),
    CONSTRAINT ck_resoluciones_verificacion CHECK (estado_verificacion IN
        ('PENDIENTE','VERIFICADA','RECHAZADA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =============================================================================
-- plazos — MODULO CRITICO (RF-09 / HU-09)
-- =============================================================================
-- fecha_vencimiento la calcula CalculadoraPlazos contra calendario_judicial,
-- considerando SOLO las filas con estado = 'APROBADO'.
-- dias_restantes NO es columna: es DATEDIFF(fecha_vencimiento, CURRENT_DATE).
-- =============================================================================
CREATE TABLE plazos (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid_publico            CHAR(36)        NOT NULL DEFAULT (UUID()),
    expediente_id           BIGINT UNSIGNED NOT NULL COMMENT 'REGLA DE ORO',
    actuacion_origen_id     BIGINT UNSIGNED NULL COMMENT 'Trazabilidad Actuacion -> Plazo',
    resolucion_id           BIGINT UNSIGNED NULL COMMENT 'La resolucion que lo origino',
    tipo_plazo_id           SMALLINT UNSIGNED NOT NULL,
    descripcion             VARCHAR(200)    NOT NULL,
    -- Computo
    fecha_notificacion      DATE            NOT NULL,
    fecha_inicio_computo    DATE            NOT NULL
                            COMMENT 'Normalmente el dia habil siguiente a la notificacion',
    dias_plazo              SMALLINT UNSIGNED NOT NULL,
    tipo_computo            VARCHAR(15)     NOT NULL,
    fecha_vencimiento       DATE            NOT NULL COMMENT 'Fecha oficial calculada',
    fecha_vencimiento_interna DATE          NULL COMMENT 'Colchon de seguridad del estudio',
    -- Responsables
    usuario_responsable_id  BIGINT UNSIGNED NOT NULL,
    usuario_revisor_id      BIGINT UNSIGNED NULL,
    -- Estado y riesgo
    estado                  VARCHAR(25)     NOT NULL DEFAULT 'PENDIENTE_CALCULO',
    nivel_riesgo            VARCHAR(10)     NOT NULL DEFAULT 'ALTO',
    -- La red de seguridad del §8 / HU-09
    requiere_revision_profesional TINYINT(1) NOT NULL DEFAULT 0,
    motivo_revision         VARCHAR(250)    NULL,
    actuacion_requerida     VARCHAR(200)    NULL,
    documento_resultante_id BIGINT UNSIGNED NULL COMMENT 'FK añadida en V6',
    fecha_cumplimiento      DATETIME(3)     NULL,
    observaciones           TEXT            NULL,
    -- auditoria
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_creacion_id     BIGINT UNSIGNED NULL,
    fecha_modificacion      DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    usuario_modificacion_id BIGINT UNSIGNED NULL,
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_plazos_uuid (uuid_publico),
    -- EL INDICE MAS IMPORTANTE DEL SISTEMA: sostiene "Plazos vencidos" y
    -- "Plazos por vencer", que se consultan en cada login de cada usuario.
    -- MySQL no tiene INCLUDE, asi que las columnas que cubren la consulta van
    -- dentro de la propia clave.
    KEY ix_plazos_vencimiento (fecha_vencimiento, estado, activo,
                               usuario_responsable_id, expediente_id, nivel_riesgo),
    KEY ix_plazos_responsable (usuario_responsable_id, estado, fecha_vencimiento),
    KEY ix_plazos_expediente (expediente_id, fecha_vencimiento),
    KEY ix_plazos_actuacion (actuacion_origen_id),
    KEY ix_plazos_revision (requiere_revision_profesional, estado),
    CONSTRAINT fk_plazos_expediente
        FOREIGN KEY (expediente_id) REFERENCES expedientes (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_plazos_actuacion
        FOREIGN KEY (actuacion_origen_id) REFERENCES actuaciones (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_plazos_resolucion
        FOREIGN KEY (resolucion_id) REFERENCES resoluciones (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_plazos_tipo
        FOREIGN KEY (tipo_plazo_id) REFERENCES tipos_plazo (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_plazos_responsable
        FOREIGN KEY (usuario_responsable_id) REFERENCES usuarios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_plazos_revisor
        FOREIGN KEY (usuario_revisor_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_plazos_creador
        FOREIGN KEY (usuario_creacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_plazos_computo CHECK (tipo_computo IN ('HABILES','CALENDARIO')),
    CONSTRAINT ck_plazos_dias   CHECK (dias_plazo > 0),
    CONSTRAINT ck_plazos_riesgo CHECK (nivel_riesgo IN ('BAJO','MEDIO','ALTO','CRITICO')),
    CONSTRAINT ck_plazos_estado CHECK (estado IN
        ('PENDIENTE_CALCULO','CALCULADO','EN_PREPARACION','EN_REVISION','LISTO_PRESENTAR',
         'PRESENTADO','CUMPLIDO','VENCIDO','SUSPENDIDO','CANCELADO')),
    CONSTRAINT ck_plazos_fechas  CHECK (fecha_vencimiento >= fecha_inicio_computo),
    CONSTRAINT ck_plazos_interna CHECK (fecha_vencimiento_interna IS NULL
        OR fecha_vencimiento_interna <= fecha_vencimiento),
    -- Si se marca para revision profesional, hay que decir por que
    CONSTRAINT ck_plazos_motivo CHECK (requiere_revision_profesional = 0
        OR motivo_revision IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- alertas — una sola tabla con plazo opcional (RF-10 / HU-10).
-- Cubre los avisos automaticos de plazo (15/10/7/5/3/1/0 y posteriores) y los
-- avisos genericos de expediente ("cuantia alta", "conflicto de intereses").
-- -----------------------------------------------------------------------------
CREATE TABLE alertas (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    estudio_id              INT UNSIGNED    NOT NULL,
    plazo_id                BIGINT UNSIGNED NULL COMMENT 'NULL = alerta generica',
    expediente_id           BIGINT UNSIGNED NULL,
    usuario_destino_id      BIGINT UNSIGNED NOT NULL COMMENT 'Sin destinatario no hay alerta',
    tipo                    VARCHAR(25)     NOT NULL,
    dias_antes              SMALLINT        NULL
                            COMMENT '15,10,7,5,3,1,0. NEGATIVO = posterior al vencimiento',
    mensaje                 VARCHAR(255)    NOT NULL,
    severidad               VARCHAR(10)     NOT NULL DEFAULT 'MEDIA',
    fecha_programada        DATETIME(3)     NOT NULL,
    canal                   VARCHAR(15)     NOT NULL DEFAULT 'SISTEMA',
    estado                  VARCHAR(15)     NOT NULL DEFAULT 'PROGRAMADA',
    fecha_envio             DATETIME(3)     NULL,
    fecha_lectura           DATETIME(3)     NULL,
    PRIMARY KEY (id),
    -- Lo consume el @Scheduled que despacha alertas
    KEY ix_alertas_pendientes (estado, fecha_programada),
    KEY ix_alertas_destino (usuario_destino_id, estado, fecha_programada DESC),
    KEY ix_alertas_plazo (plazo_id),
    KEY ix_alertas_expediente (expediente_id),
    CONSTRAINT fk_alertas_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_alertas_plazo
        FOREIGN KEY (plazo_id) REFERENCES plazos (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_alertas_expediente
        FOREIGN KEY (expediente_id) REFERENCES expedientes (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_alertas_destino
        FOREIGN KEY (usuario_destino_id) REFERENCES usuarios (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT ck_alertas_tipo CHECK (tipo IN
        ('PLAZO_PROXIMO','PLAZO_VENCIDO','AUDIENCIA','TAREA_VENCIDA','RESOLUCION_NUEVA',
         'CUANTIA_ALTA','CONFLICTO_INTERES','CALENDARIO_PENDIENTE','GENERICA')),
    CONSTRAINT ck_alertas_severidad CHECK (severidad IN ('BAJA','MEDIA','ALTA','CRITICA')),
    CONSTRAINT ck_alertas_canal CHECK (canal IN ('SISTEMA','EMAIL','AMBOS')),
    CONSTRAINT ck_alertas_estado CHECK (estado IN
        ('PROGRAMADA','ENVIADA','LEIDA','CANCELADA'))
    -- REGLA NO EXPRESABLE EN LA BASE: "toda alerta con plazo_id debe traer
    -- dias_antes". MySQL 8 rechaza un CHECK sobre una columna que participa en
    -- una FK con accion referencial (error 3823), y plazo_id necesita
    -- ON DELETE CASCADE. Se valida en AlertaService.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- tareas — RF-11 / HU-11.
-- La tabla guarda los 9 estados del §9; el tablero los agrupa en las 4 columnas
-- del mockup aprobado:
--   Por Hacer   <- PENDIENTE
--   En Proceso  <- EN_PROCESO, OBSERVADA
--   En Revision <- EN_REVISION
--   Completado  <- APROBADA, PRESENTADA, COMPLETADA
--   (CANCELADA y VENCIDA no se muestran en el tablero, solo en la lista)
-- Asi se cumple el flujo Elaborar->Revisar->Corregir->Aprobar->Presentar->
-- Completar de la HU-11 sin contradecir los rotulos que el cliente aprobo.
-- -----------------------------------------------------------------------------
CREATE TABLE tareas (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid_publico            CHAR(36)        NOT NULL DEFAULT (UUID()),
    expediente_id           BIGINT UNSIGNED NOT NULL COMMENT 'REGLA DE ORO',
    actuacion_origen_id     BIGINT UNSIGNED NULL COMMENT 'Trazabilidad Actuacion -> Tarea',
    plazo_id                BIGINT UNSIGNED NULL COMMENT 'Tarea que materializa un plazo',
    titulo                  VARCHAR(200)    NOT NULL,
    descripcion             TEXT            NULL,
    usuario_responsable_id  BIGINT UNSIGNED NOT NULL COMMENT 'Primer avatar de la tarjeta',
    usuario_supervisor_id   BIGINT UNSIGNED NULL COMMENT 'Segundo avatar. Aprueba en EN_REVISION',
    prioridad               VARCHAR(10)     NOT NULL DEFAULT 'MEDIA',
    estado                  VARCHAR(20)     NOT NULL DEFAULT 'PENDIENTE',
    orden_tablero           INT             NOT NULL DEFAULT 0 COMMENT 'Drag & drop',
    fecha_asignacion        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    fecha_limite            DATETIME        NOT NULL,
    fecha_inicio_real       DATETIME(3)     NULL,
    fecha_completado        DATETIME(3)     NULL,
    tiempo_estimado_minutos INT UNSIGNED    NULL,
    tiempo_real_minutos     INT UNSIGNED    NULL,
    porcentaje_avance       TINYINT UNSIGNED NOT NULL DEFAULT 0,
    -- auditoria
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_creacion_id     BIGINT UNSIGNED NULL,
    fecha_modificacion      DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    usuario_modificacion_id BIGINT UNSIGNED NULL,
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tareas_uuid (uuid_publico),
    KEY ix_tareas_responsable (usuario_responsable_id, estado, fecha_limite),
    KEY ix_tareas_expediente (expediente_id, estado),
    KEY ix_tareas_tablero (estado, orden_tablero),
    KEY ix_tareas_supervisor (usuario_supervisor_id, estado),
    KEY ix_tareas_plazo (plazo_id),
    KEY ix_tareas_vencimiento (fecha_limite, estado),
    CONSTRAINT fk_tareas_expediente
        FOREIGN KEY (expediente_id) REFERENCES expedientes (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tareas_actuacion
        FOREIGN KEY (actuacion_origen_id) REFERENCES actuaciones (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_tareas_plazo
        FOREIGN KEY (plazo_id) REFERENCES plazos (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_tareas_responsable
        FOREIGN KEY (usuario_responsable_id) REFERENCES usuarios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tareas_supervisor
        FOREIGN KEY (usuario_supervisor_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_tareas_creador
        FOREIGN KEY (usuario_creacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_tareas_prioridad CHECK (prioridad IN ('BAJA','MEDIA','ALTA','URGENTE')),
    CONSTRAINT ck_tareas_estado CHECK (estado IN
        ('PENDIENTE','EN_PROCESO','EN_REVISION','OBSERVADA','APROBADA',
         'PRESENTADA','COMPLETADA','CANCELADA','VENCIDA')),
    CONSTRAINT ck_tareas_avance CHECK (porcentaje_avance BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- subtareas — checklist simple, con responsable opcional
-- -----------------------------------------------------------------------------
CREATE TABLE subtareas (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tarea_id                BIGINT UNSIGNED NOT NULL,
    titulo                  VARCHAR(200)    NOT NULL,
    completada              TINYINT(1)      NOT NULL DEFAULT 0,
    orden                   SMALLINT        NOT NULL DEFAULT 0,
    usuario_responsable_id  BIGINT UNSIGNED NULL,
    fecha_completado        DATETIME(3)     NULL,
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_subtareas_tarea (tarea_id, orden),
    CONSTRAINT fk_subtareas_tarea
        FOREIGN KEY (tarea_id) REFERENCES tareas (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_subtareas_responsable
        FOREIGN KEY (usuario_responsable_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- eventos_agenda — RF-07 / HU-07. La agenda muestra "12:00 - 18:00", asi que
-- hay hora de fin. expediente_id admite NULL: una reunion interna del estudio
-- no tiene expediente.
-- -----------------------------------------------------------------------------
CREATE TABLE eventos_agenda (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid_publico            CHAR(36)        NOT NULL DEFAULT (UUID()),
    estudio_id              INT UNSIGNED    NOT NULL,
    expediente_id           BIGINT UNSIGNED NULL,
    actuacion_id            BIGINT UNSIGNED NULL,
    plazo_id                BIGINT UNSIGNED NULL COMMENT 'Evento "Vencimiento de plazo"',
    tipo                    VARCHAR(25)     NOT NULL,
    titulo                  VARCHAR(200)    NOT NULL,
    descripcion             TEXT            NULL,
    fecha_inicio            DATETIME        NOT NULL,
    fecha_fin               DATETIME        NOT NULL,
    todo_el_dia             TINYINT(1)      NOT NULL DEFAULT 0,
    modalidad               VARCHAR(15)     NOT NULL DEFAULT 'PRESENCIAL',
    ubicacion               VARCHAR(250)    NULL,
    enlace_virtual          VARCHAR(500)    NULL,
    estado                  VARCHAR(15)     NOT NULL DEFAULT 'PROGRAMADO',
    resultado               TEXT            NULL,
    recordatorio_minutos    INT UNSIGNED    NULL,
    color_hex               CHAR(7)         NULL,
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_creacion_id     BIGINT UNSIGNED NULL,
    fecha_modificacion      DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_eventos_uuid (uuid_publico),
    KEY ix_eventos_fecha (estudio_id, fecha_inicio, estado),
    KEY ix_eventos_expediente (expediente_id, fecha_inicio),
    KEY ix_eventos_tipo (tipo, fecha_inicio),
    CONSTRAINT fk_eventos_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_eventos_expediente
        FOREIGN KEY (expediente_id) REFERENCES expedientes (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_eventos_actuacion
        FOREIGN KEY (actuacion_id) REFERENCES actuaciones (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_eventos_plazo
        FOREIGN KEY (plazo_id) REFERENCES plazos (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_eventos_creador
        FOREIGN KEY (usuario_creacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_eventos_tipo CHECK (tipo IN
        ('AUDIENCIA','REUNION_CLIENTE','VENCIMIENTO_PLAZO','LLAMADA','DILIGENCIA',
         'PRESENTACION','VIDEOCONFERENCIA','REVISION','INTERNO')),
    CONSTRAINT ck_eventos_modalidad CHECK (modalidad IN ('PRESENCIAL','VIRTUAL','HIBRIDA')),
    CONSTRAINT ck_eventos_estado CHECK (estado IN
        ('PROGRAMADO','CONFIRMADO','REPROGRAMADO','REALIZADO','CANCELADO')),
    CONSTRAINT ck_eventos_fechas CHECK (fecha_fin >= fecha_inicio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- evento_participantes (M:N) — panel "Participantes" de la agenda
-- -----------------------------------------------------------------------------
CREATE TABLE evento_participantes (
    evento_id               BIGINT UNSIGNED NOT NULL,
    usuario_id              BIGINT UNSIGNED NOT NULL,
    tipo_participacion      VARCHAR(15)     NOT NULL DEFAULT 'ASISTENTE',
    confirmado              TINYINT(1)      NOT NULL DEFAULT 0,
    fecha_confirmacion      DATETIME(3)     NULL,
    PRIMARY KEY (evento_id, usuario_id),
    KEY ix_participantes_usuario (usuario_id),
    CONSTRAINT fk_participantes_evento
        FOREIGN KEY (evento_id) REFERENCES eventos_agenda (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_participantes_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT ck_participantes_tipo CHECK (tipo_participacion IN
        ('ORGANIZADOR','ASISTENTE','OPCIONAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
