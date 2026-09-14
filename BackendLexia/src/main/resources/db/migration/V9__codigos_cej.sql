-- =============================================================================
-- LEXIA — V9: Coordenadas CEJ del organo y numeracion de resoluciones
--             (RF-05, RF-15 / HU-05)
-- =============================================================================
-- Dos correcciones al modelo, ambas detectadas contrastando la semilla contra
-- expedientes reales de la Corte Superior de Justicia de La Libertad.
--
-- EL FORMATO, PARA QUIEN LLEGUE NUEVO AL PROYECTO
-- ------------------------------------------------
-- El numero CEJ tiene SIETE segmentos y cada uno significa algo:
--
--   01452 - 2026 - 0 - 1601 - JR - FC - 03
--     |      |     |    |      |    |    +-- ordinal del organo: 3er Juzgado de Familia
--     |      |     |    |      |    +------- especialidad: CI civil, FC familia civil,
--     |      |     |    |      |             FT familia tutelar, LA laboral, PE penal,
--     |      |     |    |      |             CO constitucional
--     |      |     |    |      +------------ tipo de organo: JR juzgado especializado,
--     |      |     |    |                    JP juzgado de paz letrado, SP sala superior,
--     |      |     |    |                    JM mixto
--     |      |     |    +------------------- distrito judicial: La Libertad = 1601
--     |      |     +------------------------ cuaderno: 0 principal, otro incidental
--     |      +------------------------------ anio de ingreso
--     +------------------------------------- correlativo del organo ese anio
--
-- 1601 es La Libertad (Trujillo), que es donde litiga el estudio.
-- 1801 es LIMA: si aparece en algun dato de ejemplo, esta copiado de un manual.
--
-- 1. EL NUMERO CEJ NO TENIA DE DONDE SALIR.
--    `expedientes.numero_expediente` guarda la cadena completa
--    (01452-2026-0-1601-JR-FC-03) pero de sus siete segmentos solo dos estaban
--    modelados: el correlativo y el anio. Los otros cinco — incidente, distrito
--    judicial, tipo de organo, especialidad y numero de organo — no vivian en
--    ninguna columna, asi que el numero y el `organo_id` podian contradecirse
--    sin que nada lo detectara. Y se contradecian: 6 de los 9 expedientes de la
--    semilla apuntaban a un juzgado y declaraban otro en el codigo.
--
--    Aqui se le dan al organo sus coordenadas CEJ para que la aplicacion pueda
--    COMPONER el numero al aperturar un expediente, en vez de teclearlo.
--
--    OJO, y esto es lo que impide convertirlo en un CHECK: el numero CEJ se
--    asigna al interponer la demanda y NO CAMBIA NUNCA, ni cuando el caso sube
--    en apelacion a una Sala. Validar el numero contra el organo ACTUAL daria
--    falso positivo en todo expediente apelado. Por eso la aplicacion compone y
--    ADVIERTE, nunca bloquea — igual que ya decia el comentario de V4.
--
-- 2. LAS RESOLUCIONES SE IDENTIFICABAN POR SU ETIQUETA.
--    `uq_resoluciones_numero` recaia sobre `numero_resolucion`, que es texto
--    libre ('Resolucion N.o 0021'). Eso significa que 'Resolucion N.o 21',
--    'Resolucion N.o 0021' y 'RESOLUCION 21' conviven como tres resoluciones
--    distintas del mismo expediente; que el orden es lexicografico; y que
--    calcular "la siguiente es la 22" exige parsear cadenas.
--
--    Es la misma decision que el proyecto ya tomo para las versiones de
--    documentos (docs/05, correccion C-5): el numero es un entero y la etiqueta
--    la arma el frontend. Se aplica aqui tambien.
--
-- NOTA SOBRE V4: su comentario de cabecera pone como ejemplo
-- '01452-2026-0-1801-JR-FT-02', con el distrito judicial de LIMA. El correcto
-- para este estudio es 1601 (La Libertad). No se corrige alli porque V4 ya esta
-- aplicada y Flyway valida su checksum: editarla impediria arrancar. El desglose
-- bueno del formato vive en CLAUDE.md, seccion 6.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Coordenadas CEJ del organo jurisdiccional
-- -----------------------------------------------------------------------------
-- SMALLINT UNSIGNED y no TINYINT UNSIGNED para `numero_organo`: un juzgado cabe
-- de sobra en TINYINT, pero ese tipo obliga a anotar la entidad con
-- @JdbcTypeCode(SqlTypes.TINYINT) o `ddl-auto: validate` rechaza el arranque.
-- Un byte de mas a cambio de una trampa menos para quien escriba la entidad.
ALTER TABLE organos_jurisdiccionales
    ADD COLUMN distrito_judicial_codigo CHAR(4) NULL
        COMMENT 'Codigo CEJ de la Corte Superior. La Libertad = 1601, Lima = 1801',
    ADD COLUMN codigo_tipo_organo VARCHAR(2) NULL
        COMMENT 'JR juzgado especializado, JP juzgado de paz letrado, SP sala superior, JM mixto',
    ADD COLUMN codigo_especialidad VARCHAR(2) NULL
        COMMENT 'CI civil, FC familia civil, FT familia tutelar, LA laboral, PE penal, CO constitucional',
    ADD COLUMN numero_organo SMALLINT UNSIGNED NULL
        COMMENT 'Ordinal del organo dentro de su especialidad: el 3er Juzgado de Familia es 3',

    ADD CONSTRAINT ck_organos_distrito_codigo CHECK (
        distrito_judicial_codigo IS NULL OR distrito_judicial_codigo REGEXP '^[0-9]{4}$'),
    ADD CONSTRAINT ck_organos_tipo_cej CHECK (
        codigo_tipo_organo IS NULL OR codigo_tipo_organo IN ('JR','JP','SP','JM')),
    ADD CONSTRAINT ck_organos_especialidad_cej CHECK (
        codigo_especialidad IS NULL OR codigo_especialidad REGEXP '^[A-Z]{2}$'),
    ADD CONSTRAINT ck_organos_numero CHECK (
        numero_organo IS NULL OR numero_organo > 0),

    -- Las cuatro van juntas o ninguna: con media coordenada no se compone nada,
    -- y una fila a medias es peor que una vacia porque aparenta estar lista.
    -- NULL es legitimo y frecuente: una fiscalia lleva carpeta fiscal y una
    -- entidad administrativa no tiene numeracion judicial.
    ADD CONSTRAINT ck_organos_cej_completo CHECK (
           (distrito_judicial_codigo IS NULL     AND codigo_tipo_organo IS NULL
        AND codigo_especialidad      IS NULL     AND numero_organo      IS NULL)
        OR (distrito_judicial_codigo IS NOT NULL AND codigo_tipo_organo IS NOT NULL
        AND codigo_especialidad      IS NOT NULL AND numero_organo      IS NOT NULL));

-- -----------------------------------------------------------------------------
-- 2. El segmento que faltaba del numero: el cuaderno
-- -----------------------------------------------------------------------------
-- Tercer segmento del numero CEJ. '0' es el expediente principal; un valor
-- distinto identifica un cuaderno incidental (medida cautelar, apelacion
-- concedida). Ejemplo real: 06604-2008-42-1601-JR-PE-03.
ALTER TABLE expedientes
    ADD COLUMN codigo_incidente VARCHAR(2) NOT NULL DEFAULT '0'
        COMMENT 'Tercer segmento CEJ. 0 = principal, otro valor = cuaderno incidental',
    ADD CONSTRAINT ck_expedientes_incidente CHECK (codigo_incidente REGEXP '^[0-9]{1,2}$');

-- -----------------------------------------------------------------------------
-- 3. Numero de orden de la resolucion
-- -----------------------------------------------------------------------------
ALTER TABLE resoluciones
    ADD COLUMN numero_orden SMALLINT UNSIGNED NULL
        COMMENT 'Correlativo dentro del expediente. La etiqueta la arma el frontend';

-- Rescata el entero de las etiquetas ya cargadas ('Resolucion N.o 0021' -> 21).
-- En una base de produccion recien creada la tabla esta vacia y esto no afecta
-- ninguna fila; en la de desarrollo convierte las 6 filas de la semilla.
UPDATE resoluciones
   SET numero_orden = CAST(REGEXP_SUBSTR(numero_resolucion, '[0-9]+') AS UNSIGNED)
 WHERE numero_orden IS NULL
   AND numero_resolucion REGEXP '[0-9]';

-- Por si alguna etiqueta no tenia digitos: se empuja al final en vez de
-- bloquear la migracion. Queda visible como un numero absurdo, que es
-- preferible a un NULL silencioso o a un despliegue que no arranca.
UPDATE resoluciones SET numero_orden = 999 WHERE numero_orden IS NULL;

ALTER TABLE resoluciones
    MODIFY COLUMN numero_orden SMALLINT UNSIGNED NOT NULL
        COMMENT 'Correlativo dentro del expediente. La etiqueta la arma el frontend',
    -- La identidad pasa del texto al entero. `numero_resolucion` sigue existiendo
    -- y sigue siendo NOT NULL: es la etiqueta literal tal como la escribio el
    -- juzgado, y eso tiene valor documental. Lo que ya no hace es identificar.
    DROP INDEX uq_resoluciones_numero,
    ADD UNIQUE KEY uq_resoluciones_orden (expediente_id, numero_orden),
    ADD CONSTRAINT ck_resoluciones_orden CHECK (numero_orden > 0);

-- =============================================================================
-- VERIFICACION
-- =============================================================================
-- 4.1 Las cuatro coordenadas CEJ existen  (esperado: 4)
SELECT COUNT(*) AS columnas_cej_esperado_4
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'organos_jurisdiccionales'
   AND COLUMN_NAME IN ('distrito_judicial_codigo','codigo_tipo_organo',
                       'codigo_especialidad','numero_organo');

-- 4.2 La identidad de la resolucion es el entero, no la etiqueta (esperado: 1 y 0)
SELECT
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resoluciones'
        AND INDEX_NAME = 'uq_resoluciones_orden')  AS indice_nuevo_esperado_1,
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resoluciones'
        AND INDEX_NAME = 'uq_resoluciones_numero') AS indice_viejo_esperado_0;

-- 4.3 Ninguna resolucion quedo sin numero de orden util  (esperado: 0)
SELECT COUNT(*) AS resoluciones_sin_orden_esperado_0
  FROM resoluciones WHERE numero_orden IS NULL OR numero_orden = 999;

-- 4.4 El numero CEJ que compone cada organo, contra el guardado.
--     Los expedientes apelados pueden diferir legitimamente: el numero es el de
--     origen y el organo es el actual. Por eso esto se LEE, no se da por fallado.
SELECT e.codigo_interno, e.numero_expediente AS guardado,
       CONCAT_WS('-', e.numero_correlativo, e.anio, e.codigo_incidente,
                 o.distrito_judicial_codigo, o.codigo_tipo_organo,
                 o.codigo_especialidad, LPAD(o.numero_organo, 2, '0')) AS compuesto,
       o.nombre AS organo
  FROM expedientes e
  JOIN organos_jurisdiccionales o ON o.id = e.organo_id
 WHERE e.numero_expediente IS NOT NULL
   AND o.distrito_judicial_codigo IS NOT NULL
 ORDER BY e.id;

-- 4.5 PRUEBAS NEGATIVAS — las tres deben ser rechazadas
-- (a) coordenada CEJ a medias
-- UPDATE organos_jurisdiccionales SET distrito_judicial_codigo = '1601' WHERE id = 5;
-- (b) tipo de organo inventado
-- UPDATE organos_jurisdiccionales SET codigo_tipo_organo = 'XX' WHERE id = 1;
-- (c) dos resoluciones con el mismo orden en un expediente
-- INSERT INTO resoluciones (expediente_id, numero_orden, numero_resolucion, fecha_notificacion)
--   VALUES (1, 21, 'Resolucion duplicada', CURRENT_DATE);
