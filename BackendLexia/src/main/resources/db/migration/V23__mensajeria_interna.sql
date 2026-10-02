-- =============================================================================
-- LEXIA — V23: mensajeria interna entre usuarios del estudio.
-- =============================================================================
-- Un `LEEME.md` de esta carpeta advierte de que dos migraciones con el mismo
-- numero abortan el arranque de TODAS las instancias. La V22 esta ocupada, asi
-- que esta va a V23. Antes de crearla, comprobar que V23 no existe ya en otra
-- rama.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Un CONVERSACION entre dos usuarios, no un buzon de mensajes sueltos.
-- -----------------------------------------------------------------------------
-- La alternativa era guardar cada mensaje con emisor y destinatario y regroupar
-- en cada consulta. Se descarta por dos motivos concretos:
--
--   1. "Mis mensajes con fulano" obliga a un OR sobre dos columnas y, con
--      indice, MySQL lo resuelve con dos rangos y una union. Con el par en su
--      propia tabla la busqueda es un unico WHERE sobre una clave unica.
--   2. "No hablo con nadie mas" es una condicion sobre la fila, no sobre los
--      mensajes que la contiene: se puede responder a un mensaje sin tocar los
--      demas. En la otra forma habia que ir uno por uno reasignando.
--
-- emisor_id y destinatario_id se ordenan SIEMPRE menor primero, y eso lo
-- impone el CHECK de abajo. Asi el par (menor, mayor) es UNICO y da igual en que
-- orden se mire: un par (3,7) y un (7,3) son la misma conversacion. Si se
-- guardara el orden real de cada mensaje, la unicidad no se podria expresar y
-- cada respuesta crearia una conversacion gemela.
-- -----------------------------------------------------------------------------
CREATE TABLE conversaciones (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    -- menor usuario_id
    usuario_a_id        BIGINT UNSIGNED NOT NULL,
    -- mayor usuario_id
    usuario_b_id        BIGINT UNSIGNED NOT NULL,

    -- El ultimo mensaje, desnormalizado.
    --
    -- Se guarda en la conversacion y no se busca con "el MAX(fecha) del grupo"
    -- porque el listado de la bandeja necesita el ultimo mensaje de CADA
    -- conversacion a la vez. Calcularlo con MAX por grupo obliga a un barrido de
    -- toda la tabla de mensajes para pintar una lista, y esa consulta crece
    -- con el historico entero en vez de con el numero de conversaciones.
    ultimo_mensaje_id   BIGINT UNSIGNED NULL,
    ultimo_mensaje_texto VARCHAR(500) NULL,
    fecha_ultimo_mensaje DATETIME(3) NULL,

    -- Cuantos mensajes ha enviado cada uno y hasta donde ha leido. `leido_hasta`
    -- apunta al ultimo id que ha visto, no a un contador: asi el "no leidos" se
    -- resuelve con un MAX, y no hace falta un UPDATE por cada lectura (lo que
    -- en una bandeja que se refresca cada minuto es una escritura por usuario
    -- cada minuto, sobre una tabla que no lo necesita).
    leido_a_por_id      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    leido_b_por_id      BIGINT UNSIGNED NOT NULL DEFAULT 0,

    fecha_creacion      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    fecha_modificacion  DATETIME(3) NULL ON UPDATE CURRENT_TIMESTAMP(3),
    activo              TINYINT(1) NOT NULL DEFAULT 1,

    PRIMARY KEY (id),

    -- La unicidad del par, que es lo que hace que "responder" no cree una
    -- conversacion nueva. `usuario_a_id, usuario_b_id` es justo el indice de
    -- esta busqueda, asi que no hace falta un indice aparte.
    CONSTRAINT uq_conversaciones_par UNIQUE (usuario_a_id, usuario_b_id),

    -- Orden canonico del par. Sin esto, un par guardado invertido pasaria el
    -- UNIQUE y crearia el duplicado que el UNIQUE pretendia evitar.
    CONSTRAINT ck_conversaciones_orden CHECK (usuario_a_id < usuario_b_id),

    CONSTRAINT fk_conversaciones_a FOREIGN KEY (usuario_a_id) REFERENCES usuarios (id),
    CONSTRAINT fk_conversaciones_b FOREIGN KEY (usuario_b_id) REFERENCES usuarios (id)
) ENGINE = InnoDB;

-- La bandeja: las conversaciones del usuario ordenadas por actividad. Sin este
-- indice, "mis conversaciones" seria un filesort sobre toda la tabla.
CREATE INDEX ix_conversaciones_usuario ON conversaciones (
    usuario_a_id, fecha_ultimo_mensaje DESC
);

CREATE INDEX ix_conversaciones_usuario_b ON conversaciones (
    usuario_b_id, fecha_ultimo_mensaje DESC
);

-- -----------------------------------------------------------------------------
-- Los mensajes.
-- -----------------------------------------------------------------------------
CREATE TABLE mensajes (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversacion_id BIGINT UNSIGNED NOT NULL,
    emisor_id       BIGINT UNSIGNED NOT NULL,
    cuerpo          TEXT NOT NULL,

    -- Cuándo lo vio el otro. NULL = sin leer. Permite el "leído" sin quitar
    -- nada de la lista: los mensajes no se borran al leerse.
    leido_por_id    BIGINT UNSIGNED NULL,
    fecha_lectura   DATETIME(3) NULL,

    fecha_creacion  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    -- El mensaje va con la conversacion: si un usuario se da de baja y sus
    -- conversaciones se desactivan, sus mensajes se conservan. Sin cascada, un
    -- ON DELETE CASCADE perderia el historico entero de un estudio entero.
    CONSTRAINT fk_mensajes_conversacion FOREIGN KEY (conversacion_id)
        REFERENCES conversaciones (id) ON DELETE CASCADE,
    CONSTRAINT fk_mensajes_emisor FOREIGN KEY (emisor_id) REFERENCES usuarios (id),

    PRIMARY KEY (id),

    -- El scroll de un chat: "los mensajes de esta conversacion, en orden".
    CONSTRAINT ck_mensajes_cuerpo CHECK (CHAR_LENGTH(TRIM(cuerpo)) > 0)
) ENGINE = InnoDB;

CREATE INDEX ix_mensajes_conversacion ON mensajes (conversacion_id, id);

-- -----------------------------------------------------------------------------
-- Permisos.
-- -----------------------------------------------------------------------------
-- `MENSAJERIA_LEER` y `MENSAJERIA_ENVIAR` van separados a proposito. Puede
-- querer un estudio que sus abogados SIMATICEN el acceso, por ejemplo, y que
-- solo la recepcion use el modulo. Con un unico permiso no se puede expresar.
INSERT INTO permisos (codigo, modulo, descripcion) VALUES
    ('MENSAJERIA_LEER',  'MENSAJERIA', 'Ver conversaciones y su lista de mensajes'),
    ('MENSAJERIA_ENVIAR', 'MENSAJERIA', 'Enviar mensajes internos');

-- A todos los roles internos, menos al portal del cliente.
--
-- CLIENTE NO entra: un cliente no es parte del equipo, y el modulo entero asume
-- que los dos interlocutores son internos del mismo estudio (ver la nota de
-- `conversaciones`). Darlo solo a los internos es lo que hace que la frontera de
-- `tipo_usuario` no tenga que comprobarse en cada consulta.
--
-- Los codigos son los de `V12__roles_y_permisos_del_sistema`, NO nombres de
-- pantalla: si se escribieran "Asistente legal" o "Practicante" el INSERT no
-- encontraria el rol y la migracion pasaria en silencio, dejando el modulo
-- sin permisos asignados y sin avisar. Por eso se enumeran los 7 internos en
-- vez de usar un `WHERE es_sistema = 1`: asi el listado se ve completo y se
-- puede comparar de un vistazo con la tabla de roles.
INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permisos p ON p.codigo IN ('MENSAJERIA_LEER', 'MENSAJERIA_ENVIAR')
 WHERE r.codigo IN ('ADMINISTRADOR', 'SOCIO', 'ABOGADO', 'ABOGADO_AUXILIAR',
                    'ASISTENTE_LEGAL', 'CONTABILIDAD', 'RECEPCIONISTA')
   AND NOT EXISTS (
       SELECT 1 FROM roles_permisos rp
         WHERE rp.rol_id = r.id AND rp.permiso_id = p.id
   );
