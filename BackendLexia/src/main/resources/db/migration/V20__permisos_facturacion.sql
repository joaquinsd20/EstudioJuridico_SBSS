-- =============================================================================
-- LEXIA — V20: Permisos del modulo de Facturacion.
-- =============================================================================
-- V19 creo las tablas de honorarios y movimientos, pero no sus permisos, asi
-- que los 9 endpoints de /api/v1/facturacion no podian anotarse con
-- @PreAuthorize y quedaban abiertos a cualquier usuario autenticado: un
-- recepcionista o un cliente del portal leia los honorarios de todo el estudio.
-- Comprobado antes de esta migracion: GET /api/v1/facturacion devolvia 200 a
-- juan.perez@lexia.com (ABOGADO).
--
-- El rol CONTABILIDAD se describe desde V12 como "Honorarios y pagos" y sin
-- embargo no tenia ni un permiso de dinero, porque ninguno existia.
--
-- Quien NO recibe nada, a proposito: ABOGADO y ASISTENTE_LEGAL (V12 les asigna
-- trabajo juridico, no finanzas), ABOGADO_AUXILIAR ("Sin finanzas ni
-- confidenciales"), RECEPCIONISTA y CLIENTE. Si el estudio decide que un
-- abogado vea los honorarios de sus propios expedientes, es una linea mas en
-- una migracion siguiente, no un cambio de codigo.
-- =============================================================================

-- Sin ids fijos, igual que V17: los de V12 llegan hasta 31 y fijarlos aqui
-- chocaria con cualquier migracion paralela.
INSERT INTO permisos (codigo, modulo, descripcion) VALUES
('FACTURACION_LEER',      'FACTURACION', 'Consultar honorarios, resumen financiero y catalogos'),
('FACTURACION_REGISTRAR', 'FACTURACION', 'Registrar honorarios, movimientos y pagos'),
('FACTURACION_EDITAR',    'FACTURACION', 'Modificar un honorario ya registrado'),
('FACTURACION_EMITIR',    'FACTURACION', 'Emitir el comprobante de un honorario')
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion), modulo = VALUES(modulo);

-- Administrador, Socio y Contabilidad: los cuatro permisos.
-- V12 da a ADMINISTRADOR "todos los permisos" con un CROSS JOIN, pero eso solo
-- alcanza a los que existian cuando corrio: hay que nombrarlo aqui.
INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permisos p ON p.codigo IN ('FACTURACION_LEER', 'FACTURACION_REGISTRAR',
                                  'FACTURACION_EDITAR', 'FACTURACION_EMITIR')
 WHERE r.codigo IN ('ADMINISTRADOR', 'SOCIO', 'CONTABILIDAD')
ON DUPLICATE KEY UPDATE rol_id = roles_permisos.rol_id;

-- =============================================================================
-- VERIFICACION (regla 11 de CLAUDE.md)
-- =============================================================================
-- Los 4 permisos existen y estan en el modulo FACTURACION.
--   SELECT COUNT(*) FROM permisos WHERE modulo = 'FACTURACION';
--   -- esperado: 4
--
-- Exactamente 3 roles los tienen, con los 4 permisos cada uno.
--   SELECT r.codigo, COUNT(*) permisos
--     FROM roles_permisos rp
--     JOIN roles r    ON r.id = rp.rol_id
--     JOIN permisos p ON p.id = rp.permiso_id
--    WHERE p.modulo = 'FACTURACION'
--    GROUP BY r.codigo ORDER BY r.codigo;
--   -- esperado: ADMINISTRADOR 4, CONTABILIDAD 4, SOCIO 4
--
-- PRUEBA NEGATIVA: ningun rol operativo ni el portal tocan el dinero.
--   SELECT COUNT(*)
--     FROM roles_permisos rp
--     JOIN roles r    ON r.id = rp.rol_id
--     JOIN permisos p ON p.id = rp.permiso_id
--    WHERE p.modulo = 'FACTURACION'
--      AND r.codigo IN ('ABOGADO', 'ABOGADO_AUXILIAR', 'ASISTENTE_LEGAL',
--                       'RECEPCIONISTA', 'CLIENTE');
--   -- esperado: 0
