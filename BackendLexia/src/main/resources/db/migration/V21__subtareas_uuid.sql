-- =============================================================================
-- LEXIA — V21: identificador publico para las subtareas.
-- =============================================================================
-- `subtareas` es la unica tabla del nucleo sin `uuid_publico`, y el modulo de
-- tareas necesita marcar una subtarea como completada desde el navegador. Sin
-- uuid habria que exponer el BIGINT, que la regla de la API prohibe.
--
-- OJO con la forma de hacerlo. Lo natural seria:
--
--     ALTER TABLE subtareas ADD COLUMN uuid_publico CHAR(36) NOT NULL DEFAULT (UUID());
--
-- y eso FALLA sobre una tabla que ya tiene filas:
--
--     ERROR 1674 (HY000): Statement is unsafe because it uses a system function
--     that may return a different value on the replica.
--
-- Comprobado contra MySQL 8.0.46 con binlog_format=ROW. Por eso va en cuatro
-- pasos: columna nullable, relleno con UPDATE (ese si se permite), y despues el
-- NOT NULL con su DEFAULT y el indice unico. Las tablas de V1..V20 pueden usar
-- DEFAULT (UUID()) porque lo declaran en el CREATE TABLE, cuando todavia no hay
-- ninguna fila que rellenar.
-- =============================================================================

ALTER TABLE subtareas
    ADD COLUMN uuid_publico CHAR(36) NULL AFTER id;

UPDATE subtareas SET uuid_publico = UUID() WHERE uuid_publico IS NULL;

ALTER TABLE subtareas
    MODIFY COLUMN uuid_publico CHAR(36) NOT NULL DEFAULT (UUID());

ALTER TABLE subtareas
    ADD UNIQUE KEY uq_subtareas_uuid (uuid_publico);

-- =============================================================================
-- VERIFICACION (regla 11 de CLAUDE.md)
-- =============================================================================
-- Todas las subtareas tienen un uuid, y todos distintos.
--   SELECT COUNT(*) total, COUNT(uuid_publico) con_uuid,
--          COUNT(DISTINCT uuid_publico) distintos
--     FROM subtareas;
--   -- esperado: los tres numeros iguales
--
-- Una subtarea nueva recibe el suyo sin que la aplicacion lo escriba.
--   INSERT INTO subtareas (tarea_id, titulo, orden)
--     SELECT id, 'prueba de uuid', 99 FROM tareas LIMIT 1;
--   SELECT uuid_publico FROM subtareas WHERE titulo = 'prueba de uuid';
--   -- esperado: un uuid, no NULL ni cadena vacia
--   DELETE FROM subtareas WHERE titulo = 'prueba de uuid';
--
-- PRUEBA NEGATIVA: no se pueden repetir.
--   INSERT INTO subtareas (tarea_id, uuid_publico, titulo, orden)
--     SELECT tarea_id, uuid_publico, 'duplicada', 98 FROM subtareas LIMIT 1;
--   -- esperado: ERROR 1062 (Duplicate entry) por uq_subtareas_uuid
