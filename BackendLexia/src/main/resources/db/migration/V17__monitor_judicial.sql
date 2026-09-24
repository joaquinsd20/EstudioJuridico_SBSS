-- =============================================================================
-- LEXIA — V17: Monitor Judicial
-- Registra expedientes monitoreados y cada consulta realizada por el estudio.
-- =============================================================================

CREATE TABLE monitor_expedientes (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid_publico        CHAR(36)        NOT NULL DEFAULT (UUID()),
    estudio_id          INT UNSIGNED    NOT NULL,
    expediente_id       BIGINT UNSIGNED NOT NULL,
    fuente_plataforma   VARCHAR(150)    NOT NULL,
    estado              VARCHAR(15)     NOT NULL DEFAULT 'ACTIVO',
    ultima_consulta     DATETIME(3)     NULL,
    fecha_creacion      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_creacion_id BIGINT UNSIGNED NULL,
    fecha_modificacion  DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    usuario_modificacion_id BIGINT UNSIGNED NULL,
    activo              TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_monitor_uuid (uuid_publico),
    UNIQUE KEY uq_monitor_expediente (estudio_id, expediente_id),
    KEY ix_monitor_estado (estudio_id, estado, activo),
    KEY ix_monitor_ultima_consulta (ultima_consulta),
    CONSTRAINT fk_monitor_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_monitor_expediente
        FOREIGN KEY (expediente_id) REFERENCES expedientes (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_monitor_creador
        FOREIGN KEY (usuario_creacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_monitor_modificador
        FOREIGN KEY (usuario_modificacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_monitor_estado CHECK (estado IN ('ACTIVO','PAUSADO','CERRADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE monitor_consultas (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid_publico            CHAR(36)        NOT NULL DEFAULT (UUID()),
    monitor_id              BIGINT UNSIGNED NOT NULL,
    expediente_id           BIGINT UNSIGNED NOT NULL,
    fecha_consulta          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_id              BIGINT UNSIGNED NOT NULL,
    fuente_plataforma       VARCHAR(150)    NOT NULL,
    hubo_movimiento         TINYINT(1)      NOT NULL DEFAULT 0,
    tipo_movimiento         VARCHAR(40)     NULL,
    nueva_resolucion        TINYINT(1)      NOT NULL DEFAULT 0,
    numero_resolucion       VARCHAR(30)     NULL,
    tipo_resolucion         VARCHAR(25)     NULL,
    fecha_resolucion        DATE            NULL,
    fecha_notificacion      DATE            NULL,
    resolucion_id           BIGINT UNSIGNED NULL,
    documento_descargado    VARCHAR(250)    NULL,
    notificacion            VARCHAR(500)    NULL,
    plazo                   VARCHAR(250)    NULL,
    observaciones           TEXT            NULL,
    origen_informacion      VARCHAR(15)     NOT NULL DEFAULT 'MANUAL',
    estado_verificacion     VARCHAR(15)     NOT NULL DEFAULT 'VERIFICADA',
    usuario_verificacion_id BIGINT UNSIGNED NULL,
    fecha_verificacion      DATETIME(3)     NULL,
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_monitor_consulta_uuid (uuid_publico),
    KEY ix_monitor_consultas_monitor (monitor_id, fecha_consulta DESC),
    KEY ix_monitor_consultas_expediente (expediente_id, fecha_consulta DESC),
    KEY ix_monitor_consultas_verificacion (estado_verificacion, fecha_consulta DESC),
    KEY ix_monitor_consultas_fecha (fecha_consulta),
    CONSTRAINT fk_monitor_consulta_monitor
        FOREIGN KEY (monitor_id) REFERENCES monitor_expedientes (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_monitor_consulta_expediente
        FOREIGN KEY (expediente_id) REFERENCES expedientes (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_monitor_consulta_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_monitor_consulta_resolucion
        FOREIGN KEY (resolucion_id) REFERENCES resoluciones (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_monitor_consulta_verificador
        FOREIGN KEY (usuario_verificacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_monitor_consulta_origen CHECK (origen_informacion IN
        ('MANUAL','DESCARGADA','CORREO','CLIENTE','IA')),
    CONSTRAINT ck_monitor_consulta_verificacion CHECK (estado_verificacion IN
        ('PENDIENTE','VERIFICADA','RECHAZADA')),
    CONSTRAINT ck_monitor_tipo_resolucion CHECK (tipo_resolucion IS NULL OR tipo_resolucion IN
        ('AUTO','SENTENCIA','DECRETO','PROVIDENCIA','CASACION','ACUERDO','RESOLUCION_ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Permisos del modulo. No se usan ids fijos para evitar colisiones futuras.
INSERT INTO permisos (codigo, modulo, descripcion) VALUES
('MONITOR_LEER', 'MONITOR_JUDICIAL', 'Consultar expedientes y movimientos del monitor judicial'),
('MONITOR_REGISTRAR', 'MONITOR_JUDICIAL', 'Agregar expedientes y registrar/verificar consultas judiciales')
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion), modulo = VALUES(modulo);

-- Administrador, Socio, Abogado, Abogado auxiliar y Asistente legal pueden consultar.
INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permisos p ON p.codigo = 'MONITOR_LEER'
 WHERE r.codigo IN ('ADMINISTRADOR','SOCIO','ABOGADO','ABOGADO_AUXILIAR','ASISTENTE_LEGAL')
ON DUPLICATE KEY UPDATE rol_id = roles_permisos.rol_id;

-- Los mismos roles pueden registrar. El backend sigue limitando los expedientes
-- visibles a los que correspondan a cada usuario.
INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permisos p ON p.codigo = 'MONITOR_REGISTRAR'
 WHERE r.codigo IN ('ADMINISTRADOR','SOCIO','ABOGADO','ABOGADO_AUXILIAR','ASISTENTE_LEGAL')
ON DUPLICATE KEY UPDATE rol_id = roles_permisos.rol_id;
