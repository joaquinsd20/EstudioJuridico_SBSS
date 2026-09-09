-- =============================================================================
-- LEXIA — V1: Seguridad (estudio, roles, permisos, usuarios)
-- =============================================================================
-- Motor    : MySQL 8.0.16 o superior. Por debajo de esa version MySQL acepta la
--            sintaxis CHECK y la IGNORA EN SILENCIO. Este esquema apoya reglas de
--            negocio en CHECK, asi que 8.0.16 es un requisito, no una sugerencia.
-- Charset  : utf8mb4 / utf8mb4_0900_ai_ci (tildes, ñ y cualquier caracter)
-- Motor tbl: InnoDB (transacciones y llaves foraneas)
--
-- Convencion acordada por el equipo:
--   PK  = id                      (convencion por defecto de Hibernate)
--   FK  = <tabla_singular>_id     (usuario_id, expediente_id, cliente_id)
-- Asi las entidades JPA se escriben con @Id Long id sin @Column.
--
-- Esta migracion NO crea la base de datos. La base la crea el DBA una vez:
--   CREATE DATABASE lexia_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- Ninguna migracion de LEXIA contiene DROP DATABASE.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- estudios — raiz del tenant. Hoy existe un unico registro (id = 1).
-- Se incluye desde el dia 1 porque agregar la columna despues obliga a migrar
-- todas las tablas y todas las consultas.
-- -----------------------------------------------------------------------------
CREATE TABLE estudios (
    id                      INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    uuid_publico            CHAR(36)        NOT NULL DEFAULT (UUID()),
    razon_social            VARCHAR(200)    NOT NULL,
    nombre_comercial        VARCHAR(150)    NULL,
    ruc                     CHAR(11)        NOT NULL,
    direccion               VARCHAR(250)    NULL,
    telefono                VARCHAR(30)     NULL,
    email                   VARCHAR(150)    NULL,
    logo_url                VARCHAR(500)    NULL,
    zona_horaria            VARCHAR(50)     NOT NULL DEFAULT 'America/Lima',
    moneda_base             CHAR(3)         NOT NULL DEFAULT 'PEN',
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    fecha_modificacion      DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_estudios_uuid (uuid_publico),
    UNIQUE KEY uq_estudios_ruc  (ruc),
    CONSTRAINT ck_estudios_ruc CHECK (ruc REGEXP '^[0-9]{11}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Raiz multi-tenant. Un solo registro hoy.';

-- -----------------------------------------------------------------------------
-- roles
--
-- IMPORTANTE — `codigo` vs `nombre`:
--   `codigo` es INMUTABLE y es lo unico que se usa en @PreAuthorize.
--   `nombre` es la etiqueta visible y el Administrador puede editarla.
-- Derivar la authority de Spring del nombre visible (como hacia la version
-- anterior del proyecto) significa que renombrar un rol desde la pantalla de
-- Configuracion revoca permisos en silencio. Por eso van separados.
--
-- nivel_acceso: numero ALTO = MAS acceso. Escala de 10 en 10 para intercalar.
-- -----------------------------------------------------------------------------
CREATE TABLE roles (
    id                      SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo                  VARCHAR(30)     NOT NULL COMMENT 'INMUTABLE. Se usa en @PreAuthorize',
    nombre                  VARCHAR(80)     NOT NULL COMMENT 'Editable. Solo para mostrar',
    descripcion             VARCHAR(250)    NULL,
    nivel_acceso            SMALLINT UNSIGNED NOT NULL DEFAULT 10,
    es_sistema              TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '1 = no se puede eliminar',
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_roles_codigo (codigo),
    CONSTRAINT ck_roles_nivel  CHECK (nivel_acceso BETWEEN 0 AND 100),
    -- COLLATE explicito: la collation por defecto (utf8mb4_0900_ai_ci) es
    -- case-insensitive y dejaria pasar 'abogado'. El codigo debe ser MAYUSCULAS.
    CONSTRAINT ck_roles_codigo
        CHECK (codigo COLLATE utf8mb4_0900_as_cs REGEXP '^[A-Z][A-Z_]*$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- permisos — convencion <ENTIDAD>_<ACCION>
-- Los roles no llevan permisos codificados en Java: se resuelven contra esta
-- tabla, para que el Administrador los ajuste desde Configuracion sin recompilar.
-- -----------------------------------------------------------------------------
CREATE TABLE permisos (
    id                      SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo                  VARCHAR(60)     NOT NULL,
    modulo                  VARCHAR(40)     NOT NULL COMMENT 'Agrupa la pantalla de Configuracion',
    descripcion             VARCHAR(250)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_permisos_codigo (codigo),
    KEY ix_permisos_modulo (modulo),
    CONSTRAINT ck_permisos_codigo
        CHECK (codigo COLLATE utf8mb4_0900_as_cs REGEXP '^[A-Z][A-Z_]*$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- usuarios
--
-- Esta tabla se carga en CADA request autenticado (UserDetailsService), asi que
-- solo contiene lo que la autenticacion necesita. Los datos laborales del
-- empleado (salario, CV, domicilio, fecha de nacimiento) NO van aqui: iran en
-- `empleado_datos` cuando exista el modulo de RR.HH. Mantenerlos separados
-- evita que un SELECT de usuarios exponga los sueldos del estudio (Ley 29733).
--
-- tipo_usuario distingue al personal del estudio de los usuarios del portal del
-- cliente. Es lo que permite que el rol CLIENTE del §3 exista: un cliente tiene
-- su propio correo, no uno del dominio del estudio.
-- El dominio interno NO se fija en un CHECK: vive en configuracion
-- ('dominio_correo_interno' = 'lexia.com') y lo valida UsuarioService. Asi
-- cambiar de dominio es un cambio de configuracion y no una migracion.
-- -----------------------------------------------------------------------------
CREATE TABLE usuarios (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid_publico            CHAR(36)        NOT NULL DEFAULT (UUID()),
    estudio_id              INT UNSIGNED    NOT NULL,
    tipo_usuario            VARCHAR(10)     NOT NULL DEFAULT 'INTERNO',
    codigo_usuario          VARCHAR(20)     NULL COMMENT 'Codigo interno del estudio',
    nombres                 VARCHAR(100)    NOT NULL,
    apellidos               VARCHAR(100)    NOT NULL,
    nombre_completo         VARCHAR(201)    GENERATED ALWAYS AS
                            (CONCAT(nombres, ' ', apellidos)) STORED,
    tipo_documento          VARCHAR(10)     NULL,
    numero_documento        VARCHAR(20)     NULL,
    email                   VARCHAR(150)    NOT NULL,
    password_hash           VARCHAR(255)    NOT NULL COMMENT 'BCrypt cost 12',
    telefono                VARCHAR(30)     NULL,
    cargo                   VARCHAR(100)    NULL COMMENT 'Texto bajo el nombre en el sidebar',
    numero_colegiatura      VARCHAR(30)     NULL COMMENT 'CAL / ICA. Solo abogados',
    foto_url                VARCHAR(500)    NULL COMMENT 'Ruta o URL. NUNCA el binario',
    estado                  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVO',
    -- 2FA: columnas listas, funcionalidad desactivada en el MVP (ver HU-01)
    tiene_doble_factor      TINYINT(1)      NOT NULL DEFAULT 0,
    secreto_2fa             VARCHAR(255)    NULL COMMENT 'Semilla TOTP cifrada. Sin uso en el MVP',
    intentos_fallidos       TINYINT UNSIGNED NOT NULL DEFAULT 0,
    fecha_bloqueo           DATETIME(3)     NULL,
    ultimo_acceso           DATETIME(3)     NULL,
    debe_cambiar_password   TINYINT(1)      NOT NULL DEFAULT 1,
    -- Enlace al cliente para el portal. La FK se agrega en V3.
    cliente_id              BIGINT UNSIGNED NULL COMMENT 'Solo si tipo_usuario = CLIENTE',
    -- auditoria
    fecha_creacion          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_creacion_id     BIGINT UNSIGNED NULL,
    fecha_modificacion      DATETIME(3)     NULL ON UPDATE CURRENT_TIMESTAMP(3),
    usuario_modificacion_id BIGINT UNSIGNED NULL,
    activo                  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_usuarios_uuid   (uuid_publico),
    UNIQUE KEY uq_usuarios_email  (email),
    UNIQUE KEY uq_usuarios_codigo (estudio_id, codigo_usuario),
    KEY ix_usuarios_estudio_estado (estudio_id, estado, activo),
    KEY ix_usuarios_nombre (nombre_completo),
    KEY ix_usuarios_cliente (cliente_id),
    CONSTRAINT fk_usuarios_estudio
        FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_usuarios_tipo   CHECK (tipo_usuario IN ('INTERNO','CLIENTE')),
    CONSTRAINT ck_usuarios_estado CHECK (estado IN ('ACTIVO','INACTIVO','BLOQUEADO')),
    CONSTRAINT ck_usuarios_email  CHECK (email LIKE '%_@_%.__%'),
    CONSTRAINT ck_usuarios_doc    CHECK (tipo_documento IS NULL
                                    OR tipo_documento IN ('DNI','CE','PASAPORTE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- El indicador "En linea" del sidebar NO es una columna: es estado efimero.
-- Se resuelve con ultimo_acceso y un umbral de 5 minutos.

-- -----------------------------------------------------------------------------
-- usuarios_roles (M:N)
-- Un usuario puede tener varios roles: en un estudio chico el socio fundador
-- tambien litiga y necesita SOCIO y ABOGADO a la vez.
-- -----------------------------------------------------------------------------
CREATE TABLE usuarios_roles (
    usuario_id              BIGINT UNSIGNED   NOT NULL,
    rol_id                  SMALLINT UNSIGNED NOT NULL,
    fecha_asignacion        DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    usuario_asignacion_id   BIGINT UNSIGNED   NULL,
    PRIMARY KEY (usuario_id, rol_id),
    KEY ix_usuarios_roles_rol (rol_id),
    CONSTRAINT fk_usuarios_roles_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_usuarios_roles_rol
        FOREIGN KEY (rol_id) REFERENCES roles (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_usuarios_roles_asignador
        FOREIGN KEY (usuario_asignacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- roles_permisos (M:N)
-- -----------------------------------------------------------------------------
CREATE TABLE roles_permisos (
    rol_id                  SMALLINT UNSIGNED NOT NULL,
    permiso_id              SMALLINT UNSIGNED NOT NULL,
    PRIMARY KEY (rol_id, permiso_id),
    KEY ix_roles_permisos_permiso (permiso_id),
    CONSTRAINT fk_roles_permisos_rol
        FOREIGN KEY (rol_id) REFERENCES roles (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_roles_permisos_permiso
        FOREIGN KEY (permiso_id) REFERENCES permisos (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- FK de auditoria sobre usuarios (la tabla ya existe en este punto)
-- -----------------------------------------------------------------------------
ALTER TABLE usuarios
    ADD CONSTRAINT fk_usuarios_creador
        FOREIGN KEY (usuario_creacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    ADD CONSTRAINT fk_usuarios_modificador
        FOREIGN KEY (usuario_modificacion_id) REFERENCES usuarios (id)
        ON DELETE SET NULL ON UPDATE CASCADE;
