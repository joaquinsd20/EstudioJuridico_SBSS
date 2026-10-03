-- =============================================================================
-- LEXIA — V24: indices de escalabilidad de agenda y plazos.
-- =============================================================================
-- Estos seis indices se agregaron en #52 DENTRO de V16 (agenda) y V18 (plazos),
-- que ya estaban aplicadas en todas las bases. Flyway guarda el checksum de cada
-- migracion aplicada, asi que el arranque de quien las tuviera abortaba con:
--
--     Migration checksum mismatch for migration version 16
--     Migration checksum mismatch for migration version 18
--
-- Se restauran V16 y V18 a su contenido original y los indices pasan aqui. Regla
-- 3 de CLAUDE.md: un indice tambien es un cambio, y una migracion publicada no se
-- edita.
--
-- IDEMPOTENTE A PROPOSITO. Hay dos clases de base:
--   - la de quien aplico V16/V18 YA editadas (los indices existen);
--   - las demas (los indices no existen).
-- MySQL no tiene ADD KEY IF NOT EXISTS, asi que cada indice se crea solo si
-- information_schema.statistics no lo encuentra. `DO 0` es la rama "ya existe".
--
-- Cada indice repite el mismo bloque a mano en vez de usar un procedimiento
-- almacenado: Flyway lo parsea bien y no deja un objeto suelto en la base.
-- =============================================================================

-- ---------------------------------------------------------------- eventos_agenda
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE eventos_agenda ADD KEY ix_eventos_rango (estudio_id, activo, fecha_inicio, fecha_fin, estado)',
    'DO 0')
  FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'eventos_agenda' AND index_name = 'ix_eventos_rango');
PREPARE paso FROM @ddl;
EXECUTE paso;
DEALLOCATE PREPARE paso;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE eventos_agenda ADD KEY ix_eventos_tipo_estado_fecha (estudio_id, tipo, estado, fecha_inicio)',
    'DO 0')
  FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'eventos_agenda' AND index_name = 'ix_eventos_tipo_estado_fecha');
PREPARE paso FROM @ddl;
EXECUTE paso;
DEALLOCATE PREPARE paso;

-- ---------------------------------------------------------------- evento_participantes
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE evento_participantes ADD KEY ix_participantes_usuario_evento (usuario_id, evento_id)',
    'DO 0')
  FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'evento_participantes' AND index_name = 'ix_participantes_usuario_evento');
PREPARE paso FROM @ddl;
EXECUTE paso;
DEALLOCATE PREPARE paso;

-- ---------------------------------------------------------------- plazos
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE plazos ADD KEY ix_plazos_notificacion (fecha_notificacion, activo, expediente_id)',
    'DO 0')
  FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'plazos' AND index_name = 'ix_plazos_notificacion');
PREPARE paso FROM @ddl;
EXECUTE paso;
DEALLOCATE PREPARE paso;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE plazos ADD KEY ix_plazos_tipo_estado_venc (tipo_plazo_id, estado, fecha_vencimiento)',
    'DO 0')
  FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'plazos' AND index_name = 'ix_plazos_tipo_estado_venc');
PREPARE paso FROM @ddl;
EXECUTE paso;
DEALLOCATE PREPARE paso;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE plazos ADD KEY ix_plazos_riesgo_estado_venc (nivel_riesgo, estado, fecha_vencimiento)',
    'DO 0')
  FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'plazos' AND index_name = 'ix_plazos_riesgo_estado_venc');
PREPARE paso FROM @ddl;
EXECUTE paso;
DEALLOCATE PREPARE paso;

-- =============================================================================
-- VERIFICACION (regla 11 de CLAUDE.md)
-- =============================================================================
-- Existen los seis indices, uno por nombre.
--   SELECT table_name, index_name
--     FROM information_schema.statistics
--    WHERE table_schema = DATABASE()
--      AND index_name IN ('ix_eventos_rango', 'ix_eventos_tipo_estado_fecha',
--                         'ix_participantes_usuario_evento', 'ix_plazos_notificacion',
--                         'ix_plazos_tipo_estado_venc', 'ix_plazos_riesgo_estado_venc')
--    GROUP BY table_name, index_name;
--   -- esperado: 6 filas
--
-- PRUEBA DE IDEMPOTENCIA: volver a ejecutar este archivo a mano no debe fallar ni
-- duplicar nada (un indice repetido seria ERROR 1061 Duplicate key name).
--   -- esperado: sin error, y la consulta de arriba sigue dando 6 filas
