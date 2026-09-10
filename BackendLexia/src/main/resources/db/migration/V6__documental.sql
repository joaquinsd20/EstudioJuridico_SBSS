-- =============================================================================
-- LEXIA — V6: Gestion documental, comentarios y notificaciones
--             (RF-12 / HU-12)
-- =============================================================================
-- EL BINARIO NO VA EN LA BASE DE DATOS. `ruta_almacenamiento` apunta al file
-- system del servidor. Guardar PDFs en MySQL infla los respaldos, degrada el
-- rendimiento y hace que cada listado arrastre megabytes.
-- El hash_sha256 permite probar que un documento presentado no fue alterado.
-- =============================================================================

CREATE TABLE documentos (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid_publico            CHAR(36)        NOT NULL DEFAULT (UUID()),
    expediente_id           BIGINT UNSIGNED NOT NULL COMMENT 'REGLA DE ORO',
    actuacion_id            BIGINT UNSIGNED NULL,
    tarea_id                BIGINT UNSIGNED NULL,
    nombre                  VARCHAR(250)    NOT NULL,
    descripcion             TEXT            NULL,
    categoria               VARCHAR(25)     NOT NULL,
    -- Flujo de aprobacion del §9 / HU-11
    estado                  VARCHAR(20)     NOT NULL DEFAULT 'BORRADOR',
    version_vigente_id      BIGINT UNSIGNED NULL COMMENT 'FK a la fila, no un entero suelto',
    es_confidencial         TINYINT(1)      NOT NULL DEFAULT 0,
    en_papelera             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '§10: papelera y recuperacion',
    fecha_papelera          DATETIME(3)     NULL,
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_creacion_id     BIGINT UNSIGNED NULL,
    fecha_modificacion      DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    usuario_modificacion_id BIGINT UNSIGNED NULL,
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_documentos_uuid (uuid_publico),
    KEY ix_documentos_expediente (expediente_id, en_papelera, activo),
    KEY ix_documentos_estado (estado, expediente_id),
    KEY ix_documentos_actuacion (actuacion_id),
    KEY ix_documentos_tarea (tarea_id),
    FULLTEXT KEY ft_documentos_nombre (nombre, descripcion),
    CONSTRAINT fk_documentos_expediente
        FOREIGN KEY (expediente_id) REFERENCES expedientes (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_documentos_actuacion
        FOREIGN KEY (actuacion_id) REFERENCES actuaciones (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_documentos_tarea
        FOREIGN KEY (tarea_id) REFERENCES tareas (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_documentos_creador
        FOREIGN KEY (usuario_creacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_documentos_categoria CHECK (categoria IN
        ('DEMANDA','CONTESTACION','RECURSO','ESCRITO','RESOLUCION','NOTIFICACION','CARGO',
         'CONTRATO','PODER','PRUEBA','INFORME','MULTIMEDIA','CORREO','CLIENTE','OTRO')),
    CONSTRAINT ck_documentos_estado CHECK (estado IN
        ('BORRADOR','EN_REVISION','OBSERVADO','APROBADO','FIRMADO','PRESENTADO','CERRADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- documento_versiones — §10 / HU-12: quien modifico, cuando, numero de version,
-- observaciones y version vigente. El UNIQUE es lo que faltaba en las versiones
-- anteriores: sin el se pueden crear dos "version 2" del mismo documento.
--
-- numero_version es entero (1, 2, 3). La HU-12 habla de "v1.0, v1.1": eso es
-- una ETIQUETA de presentacion que arma el frontend, no el dato almacenado.
-- Un entero garantiza el orden y la unicidad; un decimal no.
-- -----------------------------------------------------------------------------
CREATE TABLE documento_versiones (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    documento_id            BIGINT UNSIGNED NOT NULL,
    numero_version          INT UNSIGNED    NOT NULL,
    nombre_archivo          VARCHAR(250)    NOT NULL,
    ruta_almacenamiento     VARCHAR(500)    NOT NULL COMMENT 'Disco o bucket, NUNCA la BD',
    extension               VARCHAR(10)     NOT NULL,
    mime_type               VARCHAR(100)    NULL,
    tamano_bytes            BIGINT UNSIGNED NOT NULL,
    hash_sha256             CHAR(64)        NOT NULL COMMENT 'Integridad probatoria',
    usuario_carga_id        BIGINT UNSIGNED NOT NULL,
    fecha_carga             DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    observaciones           VARCHAR(500)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_versiones_numero (documento_id, numero_version),
    KEY ix_versiones_documento (documento_id, numero_version DESC),
    KEY ix_versiones_hash (hash_sha256),
    CONSTRAINT fk_versiones_documento
        FOREIGN KEY (documento_id) REFERENCES documentos (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_versiones_usuario
        FOREIGN KEY (usuario_carga_id) REFERENCES usuarios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_versiones_numero CHECK (numero_version > 0),
    CONSTRAINT ck_versiones_tamano CHECK (tamano_bytes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- FK circular, resuelta despues de crear ambas tablas
ALTER TABLE documentos
    ADD CONSTRAINT fk_documentos_version_vigente
        FOREIGN KEY (version_vigente_id) REFERENCES documento_versiones (id)
        ON DELETE SET NULL ON UPDATE CASCADE;

-- -----------------------------------------------------------------------------
-- documento_etiquetas (M:N) — la tabla puente que faltaba: sin ella la tabla
-- `etiquetas` esta poblada pero es inalcanzable.
-- -----------------------------------------------------------------------------
CREATE TABLE documento_etiquetas (
    documento_id            BIGINT UNSIGNED   NOT NULL,
    etiqueta_id             SMALLINT UNSIGNED NOT NULL,
    fecha_asignacion        DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_asignacion_id   BIGINT UNSIGNED   NULL,
    PRIMARY KEY (documento_id, etiqueta_id),
    KEY ix_doc_etiquetas_etiqueta (etiqueta_id),
    CONSTRAINT fk_doc_etiquetas_documento
        FOREIGN KEY (documento_id) REFERENCES documentos (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_doc_etiquetas_etiqueta
        FOREIGN KEY (etiqueta_id) REFERENCES etiquetas (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_doc_etiquetas_usuario
        FOREIGN KEY (usuario_asignacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- Cierre de la cadena de oro: Plazo -> Documento y Resolucion -> Documento
-- -----------------------------------------------------------------------------
ALTER TABLE plazos
    ADD CONSTRAINT fk_plazos_documento
        FOREIGN KEY (documento_resultante_id) REFERENCES documentos (id)
        ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE resoluciones
    ADD CONSTRAINT fk_resoluciones_documento
        FOREIGN KEY (documento_id) REFERENCES documentos (id)
        ON DELETE SET NULL ON UPDATE CASCADE;

-- -----------------------------------------------------------------------------
-- comentarios — el §9 los pide sobre tareas; tambien son utiles sobre
-- expediente y documento. Se admiten los tres anclajes.
-- -----------------------------------------------------------------------------
CREATE TABLE comentarios (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    expediente_id           BIGINT UNSIGNED NULL,
    tarea_id                BIGINT UNSIGNED NULL,
    documento_id            BIGINT UNSIGNED NULL,
    usuario_id              BIGINT UNSIGNED NOT NULL,
    texto                   TEXT            NOT NULL,
    comentario_padre_id     BIGINT UNSIGNED NULL COMMENT 'Respuestas en hilo',
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    fecha_modificacion      DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY ix_comentarios_expediente (expediente_id, fecha_creacion DESC),
    KEY ix_comentarios_tarea (tarea_id, fecha_creacion DESC),
    KEY ix_comentarios_documento (documento_id),
    KEY ix_comentarios_usuario (usuario_id),
    KEY ix_comentarios_padre (comentario_padre_id),
    CONSTRAINT fk_comentarios_expediente
        FOREIGN KEY (expediente_id) REFERENCES expedientes (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_comentarios_tarea
        FOREIGN KEY (tarea_id) REFERENCES tareas (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_comentarios_documento
        FOREIGN KEY (documento_id) REFERENCES documentos (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_comentarios_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_comentarios_padre
        FOREIGN KEY (comentario_padre_id) REFERENCES comentarios (id)
        ON DELETE CASCADE ON UPDATE CASCADE
    -- REGLA NO EXPRESABLE EN LA BASE: "todo comentario debe colgar de al menos
    -- un expediente, tarea o documento". Las tres columnas son FK con
    -- ON DELETE CASCADE y MySQL 8 no admite un CHECK sobre ellas (error 3823).
    -- Se valida en ComentarioService.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- notificaciones — alimenta la campanita del header. Lleva a donde va el clic.
-- -----------------------------------------------------------------------------
CREATE TABLE notificaciones (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    usuario_id              BIGINT UNSIGNED NOT NULL,
    tipo                    VARCHAR(30)     NOT NULL,
    titulo                  VARCHAR(150)    NOT NULL,
    mensaje                 VARCHAR(500)    NOT NULL,
    severidad               VARCHAR(15)     NOT NULL DEFAULT 'INFO',
    -- Navegacion al hacer clic
    entidad_origen          VARCHAR(40)     NULL COMMENT 'plazos, tareas, resoluciones...',
    registro_id             BIGINT UNSIGNED NULL,
    expediente_id           BIGINT UNSIGNED NULL,
    url_destino             VARCHAR(250)    NULL,
    leida                   TINYINT(1)      NOT NULL DEFAULT 0,
    fecha_lectura           DATETIME(3)     NULL,
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_notificaciones_usuario (usuario_id, leida, fecha_creacion DESC),
    KEY ix_notificaciones_expediente (expediente_id),
    CONSTRAINT fk_notificaciones_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_notificaciones_expediente
        FOREIGN KEY (expediente_id) REFERENCES expedientes (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT ck_notificaciones_severidad CHECK (severidad IN
        ('INFO','EXITO','ADVERTENCIA','CRITICA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
