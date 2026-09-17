-- =============================================================================
-- LEXIA — V10: Biblioteca juridica  (RF-12 / material de consulta del estudio)
-- =============================================================================
-- La entidad RecursoJuridico se subio sin su tabla. Con `ddl-auto: validate`
-- eso impide arrancar el backend por completo ("Schema-validation: missing
-- table"), no solo el modulo de biblioteca. Funcionaba en la maquina de quien
-- lo escribio porque la tabla estaba creada a mano, fuera de Flyway.
--
-- QUE ES ESTA TABLA Y QUE NO ES: material de consulta del estudio
-- (jurisprudencia, doctrina, legislacion, modelos, plantillas). NO son los
-- documentos de un expediente — esos viven en `documentos` (V6) y cuelgan de
-- `expediente_id NOT NULL` por la regla de oro. Por eso aqui `expediente_codigo`
-- es texto libre opcional y no una FK: la mayoria de los recursos no pertenece
-- a ningun caso.
-- =============================================================================

CREATE TABLE recursos_juridicos (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid_publico            CHAR(36)        NOT NULL DEFAULT (UUID()),
    estudio_id              INT UNSIGNED    NOT NULL,
    titulo                  VARCHAR(250)    NOT NULL,
    subtitulo_descripcion   TEXT            NULL,
    tipo                    VARCHAR(20)     NOT NULL,
    categoria               VARCHAR(30)     NULL,
    -- INT UNSIGNED y no SMALLINT aunque un anio quepa de sobra: la entidad lo
    -- mapea como Integer, y SMALLINT haria fallar `ddl-auto: validate` por
    -- desajuste de tipo JDBC. Dos bytes de mas a cambio de una trampa menos.
    anio                    INT UNSIGNED    NULL,
    expediente_codigo       VARCHAR(50)     NULL
                            COMMENT 'Referencia suelta a un expediente. NO es FK: ver cabecera',
    nombre_archivo          VARCHAR(255)    NULL,
    ruta_archivo            VARCHAR(500)    NULL
                            COMMENT 'Ruta en disco. Ningun binario en MySQL (regla 7)',
    extension               VARCHAR(10)     NULL,
    tamanio_bytes           BIGINT UNSIGNED NULL,
    -- Estado del ciclo de vida del recurso. Distinto de `activo`, que es el
    -- borrado logico: un recurso ARCHIVADO sigue existiendo y se puede
    -- consultar; uno con activo = 0 esta borrado.
    estado                  VARCHAR(15)     NOT NULL DEFAULT 'ACTIVO',
    -- auditoria (las cinco columnas: por eso la entidad SI hereda de
    -- EntidadAuditable, a diferencia de estudios o calendario_judicial)
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_creacion_id     BIGINT UNSIGNED NULL,
    fecha_modificacion      DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    usuario_modificacion_id BIGINT UNSIGNED NULL,
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_recursos_juridicos_uuid (uuid_publico),
    KEY ix_recursos_juridicos_estudio (estudio_id, estado, activo),
    KEY ix_recursos_juridicos_tipo (estudio_id, tipo, categoria),
    KEY ix_recursos_juridicos_titulo (estudio_id, titulo),
    CONSTRAINT fk_recursos_juridicos_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_recursos_juridicos_creador
        FOREIGN KEY (usuario_creacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_recursos_juridicos_modificador
        FOREIGN KEY (usuario_modificacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    -- Codigos en MAYUSCULAS, no el texto visible. La etiqueta que ve el
    -- usuario ("Derecho Civil") la arma el frontend: asi se puede renombrar
    -- sin migrar filas, igual que roles.codigo vs roles.nombre.
    CONSTRAINT ck_recursos_juridicos_tipo CHECK (tipo IN
        ('JURISPRUDENCIA','LEGISLACION','DOCTRINA','CONTRATO',
         'MODELO','PLANTILLA','FORMATO','LIBRO')),
    CONSTRAINT ck_recursos_juridicos_categoria CHECK (categoria IS NULL OR categoria IN
        ('DERECHO_CIVIL','DERECHO_PENAL','DERECHO_LABORAL','DERECHO_CONSTITUCIONAL',
         'DERECHO_TRIBUTARIO','DERECHO_SOCIAL','DERECHO_MIGRATORIO',
         'DERECHO_ADMINISTRATIVO','PROCESAL','GENERAL')),
    CONSTRAINT ck_recursos_juridicos_estado CHECK (estado IN ('ACTIVO','ARCHIVADO')),
    CONSTRAINT ck_recursos_juridicos_anio CHECK (anio IS NULL OR anio BETWEEN 1800 AND 2200)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Material de consulta del estudio. Los documentos del expediente van en `documentos`.';

-- -----------------------------------------------------------------------------
-- Permisos del modulo
-- -----------------------------------------------------------------------------
-- OJO: estos INSERT se repiten en R__seed_desarrollo.sql a proposito, no por
-- descuido. La semilla hace TRUNCATE de `permisos` y `roles_permisos` antes de
-- recargar, y las repetibles corren DESPUES de las versionadas — asi que en
-- desarrollo la semilla borraria lo que se inserte solo aqui. En produccion la
-- semilla no corre (el perfil prod no incluye db/seed), asi que sin estas
-- lineas los permisos no existirian nunca alli.
INSERT INTO permisos (id, codigo, modulo, descripcion) VALUES
(30, 'BIBLIOTECA_LEER',      'BIBLIOTECA', 'Consultar y descargar material de la biblioteca juridica'),
(31, 'BIBLIOTECA_GESTIONAR', 'BIBLIOTECA', 'Cargar, editar y eliminar material de la biblioteca juridica');

-- Lectura: los siete roles internos. El rol CLIENTE (portal) queda fuera a
-- proposito: la biblioteca es material de trabajo del estudio, no del caso del
-- cliente.
INSERT INTO roles_permisos (rol_id, permiso_id) VALUES
(1,30),(2,30),(3,30),(4,30),(5,30),(6,30),(7,30);

-- Gestion: Administrador, Socio y Abogado. Un asistente consulta plantillas,
-- pero decidir que entra a la biblioteca del estudio es criterio profesional.
INSERT INTO roles_permisos (rol_id, permiso_id) VALUES
(1,31),(2,31),(3,31);

-- =============================================================================
-- VERIFICACION
-- =============================================================================
-- 1. La tabla existe y tiene sus restricciones  (esperado: 1 y 4)
SELECT
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recursos_juridicos')
        AS tabla_esperado_1,
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recursos_juridicos'
        AND CONSTRAINT_TYPE = 'CHECK')
        AS checks_esperado_4;

-- 2. Los permisos quedaron asignados  (esperado: 7 y 3)
SELECT
    (SELECT COUNT(*) FROM roles_permisos WHERE permiso_id = 30) AS lectura_esperado_7,
    (SELECT COUNT(*) FROM roles_permisos WHERE permiso_id = 31) AS gestion_esperado_3;

-- 3. PRUEBAS NEGATIVAS — las tres deben ser rechazadas por un CHECK
-- (a) tipo con el texto visible en vez del codigo
-- INSERT INTO recursos_juridicos (estudio_id, titulo, tipo) VALUES (1,'X','Jurisprudencia');
-- (b) categoria inventada
-- INSERT INTO recursos_juridicos (estudio_id, titulo, tipo, categoria)
--   VALUES (1,'X','DOCTRINA','Derecho Espacial');
-- (c) anio imposible
-- INSERT INTO recursos_juridicos (estudio_id, titulo, tipo, anio) VALUES (1,'X','LIBRO',99);
