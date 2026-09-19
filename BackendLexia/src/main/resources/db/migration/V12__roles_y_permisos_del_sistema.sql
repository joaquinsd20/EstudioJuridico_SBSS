-- =============================================================================
-- LEXIA — V12: roles y permisos del sistema
-- =============================================================================
-- Hasta aqui los 8 roles, los 31 permisos y su matriz vivian SOLO en
-- R__seed_desarrollo.sql. El perfil prod no carga db/seed, asi que una
-- instalacion de produccion arrancaba sin un solo rol: todo endpoint respondia
-- 403 y nadie podia administrar nada.
--
-- No son datos de prueba: el codigo depende de ellos (`@PreAuthorize` usa
-- `permisos.codigo` y `roles.codigo`). Son parte del esquema, y por eso pasan a
-- una migracion versionada y SALEN de la semilla. Una sola fuente de verdad.
--
-- IDEMPOTENTE a proposito. En las bases de desarrollo ya existentes estas filas
-- ya estan (las puso la semilla) y la migracion no debe fallar ni duplicar. Se
-- usa ON DUPLICATE KEY UPDATE y no INSERT IGNORE: IGNORE tambien convierte en
-- aviso una violacion de CHECK o de FK, y una errata aqui pasaria en silencio.
--
-- Los ids se mantienen explicitos: la semilla asigna roles a usuarios por id
-- (usuarios_roles), y V10 ya uso los permisos 30 y 31.
--
-- La matriz se genero a partir de la semilla, no transcrita a mano. Ver la
-- consulta de verificacion al final: 123 pares.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ROLES — codigo INMUTABLE (lo usa @PreAuthorize); nombre editable desde la app
-- nivel_acceso: numero ALTO = MAS acceso
-- -----------------------------------------------------------------------------
INSERT INTO roles (id, codigo, nombre, descripcion, nivel_acceso, es_sistema) VALUES
(1, 'ADMINISTRADOR', 'Administrador', 'Acceso total: configuracion, usuarios, permisos, seguridad, respaldos', 100, 1),
(2, 'SOCIO', 'Socio / Director', 'Supervision general, plazos criticos, aprobaciones, finanzas', 80, 1),
(3, 'ABOGADO', 'Abogado', 'Expedientes asignados, actuaciones, tareas, documentos y agenda', 60, 1),
(4, 'ABOGADO_AUXILIAR', 'Abogado auxiliar', 'Solo expedientes autorizados. Sin finanzas ni confidenciales', 40, 1),
(5, 'ASISTENTE_LEGAL', 'Asistente legal', 'Documentos, notificaciones, agenda, tareas y soporte', 30, 1),
(6, 'CONTABILIDAD', 'Contabilidad', 'Honorarios y pagos. Sin contenido juridico reservado', 30, 1),
(7, 'RECEPCIONISTA', 'Recepcionista', 'Registro de clientes potenciales y recepcion de documentos', 20, 0),
(8, 'CLIENTE', 'Cliente (portal)', 'Estado de su expediente, documentos autorizados y proximas actuaciones', 10, 1)
ON DUPLICATE KEY UPDATE id = id;

-- -----------------------------------------------------------------------------
-- PERMISOS
-- -----------------------------------------------------------------------------
INSERT INTO permisos (id, codigo, modulo, descripcion) VALUES
( 1, 'CLIENTE_LEER', 'CLIENTES', 'Consultar lista y ficha de clientes'),
( 2, 'CLIENTE_CREAR', 'CLIENTES', 'Registrar clientes y verificar conflicto de intereses'),
( 3, 'CLIENTE_EDITAR', 'CLIENTES', 'Modificar datos de clientes'),
( 4, 'EXPEDIENTE_LEER', 'EXPEDIENTES', 'Consultar expedientes asignados'),
( 5, 'EXPEDIENTE_CREAR', 'EXPEDIENTES', 'Aperturar expedientes'),
( 6, 'EXPEDIENTE_EDITAR', 'EXPEDIENTES', 'Modificar la ficha del expediente'),
( 7, 'EXPEDIENTE_CONFIDENCIAL', 'EXPEDIENTES', 'Acceder a expedientes marcados como confidenciales'),
( 8, 'ACTUACION_LEER', 'ACTUACIONES', 'Consultar actuaciones'),
( 9, 'ACTUACION_CREAR', 'ACTUACIONES', 'Registrar actuaciones'),
(10, 'PLAZO_LEER', 'PLAZOS', 'Consultar plazos'),
(11, 'PLAZO_CREAR', 'PLAZOS', 'Registrar y calcular plazos'),
(12, 'PLAZO_APROBAR', 'PLAZOS', 'Aprobar un plazo listo para presentar'),
(13, 'TAREA_LEER', 'TAREAS', 'Consultar el tablero de tareas'),
(14, 'TAREA_CREAR', 'TAREAS', 'Crear y asignar tareas'),
(15, 'TAREA_APROBAR', 'TAREAS', 'Aprobar tareas en revision'),
(16, 'DOCUMENTO_LEER', 'DOCUMENTOS', 'Consultar documentos del expediente'),
(17, 'DOCUMENTO_CREAR', 'DOCUMENTOS', 'Cargar documentos y versiones'),
(18, 'DOCUMENTO_DESCARGAR', 'DOCUMENTOS', 'Descargar documentos'),
(19, 'AGENDA_LEER', 'AGENDA', 'Consultar la agenda'),
(20, 'AGENDA_CREAR', 'AGENDA', 'Programar eventos y audiencias'),
(21, 'REPORTE_LEER', 'REPORTES', 'Generar y ver reportes'),
(22, 'REPORTE_EXPORTAR', 'REPORTES', 'Exportar reportes a Excel o PDF'),
(23, 'USUARIO_GESTIONAR', 'SEGURIDAD', 'Crear y administrar usuarios'),
(24, 'ROL_GESTIONAR', 'SEGURIDAD', 'Definir roles y permisos'),
(25, 'AUDITORIA_LEER', 'SEGURIDAD', 'Consultar el registro de auditoria'),
(26, 'CONFIGURACION_EDITAR', 'SEGURIDAD', 'Modificar la configuracion del sistema'),
(27, 'DASHBOARD_LEER', 'DASHBOARD', 'Ver el panel principal'),
(28, 'CALENDARIO_PROPONER', 'CALENDARIO', 'Registrar feriados y suspensiones de plazos (quedan PROPUESTO)'),
(29, 'CALENDARIO_APROBAR', 'CALENDARIO', 'Aprobar o rechazar entradas del calendario judicial'),
(30, 'BIBLIOTECA_LEER', 'BIBLIOTECA', 'Consultar y descargar material de la biblioteca juridica'),
(31, 'BIBLIOTECA_GESTIONAR', 'BIBLIOTECA', 'Cargar, editar y eliminar material de la biblioteca juridica')
ON DUPLICATE KEY UPDATE id = id;

-- -----------------------------------------------------------------------------
-- MATRIZ ROL -> PERMISO, emparejada por codigo (regla 8 de CLAUDE.md)
-- -----------------------------------------------------------------------------
-- Administrador: todos los permisos, incluidos los que se agreguen despues en
-- esta misma migracion.
INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permisos p
 WHERE r.codigo = 'ADMINISTRADOR'
ON DUPLICATE KEY UPDATE rol_id = roles_permisos.rol_id;

-- SOCIO: 28 permisos
INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT r.id, p.id FROM roles r JOIN permisos p ON p.codigo IN (
    'CLIENTE_LEER', 'CLIENTE_CREAR', 'CLIENTE_EDITAR', 'EXPEDIENTE_LEER',
    'EXPEDIENTE_CREAR', 'EXPEDIENTE_EDITAR', 'EXPEDIENTE_CONFIDENCIAL', 'ACTUACION_LEER',
    'ACTUACION_CREAR', 'PLAZO_LEER', 'PLAZO_CREAR', 'PLAZO_APROBAR',
    'TAREA_LEER', 'TAREA_CREAR', 'TAREA_APROBAR', 'DOCUMENTO_LEER',
    'DOCUMENTO_CREAR', 'DOCUMENTO_DESCARGAR', 'AGENDA_LEER', 'AGENDA_CREAR',
    'REPORTE_LEER', 'REPORTE_EXPORTAR', 'AUDITORIA_LEER', 'DASHBOARD_LEER',
    'CALENDARIO_PROPONER', 'CALENDARIO_APROBAR', 'BIBLIOTECA_LEER', 'BIBLIOTECA_GESTIONAR')
 WHERE r.codigo = 'SOCIO'
ON DUPLICATE KEY UPDATE rol_id = roles_permisos.rol_id;

-- ABOGADO: 23 permisos
INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT r.id, p.id FROM roles r JOIN permisos p ON p.codigo IN (
    'CLIENTE_LEER', 'CLIENTE_CREAR', 'CLIENTE_EDITAR', 'EXPEDIENTE_LEER',
    'EXPEDIENTE_CREAR', 'EXPEDIENTE_EDITAR', 'ACTUACION_LEER', 'ACTUACION_CREAR',
    'PLAZO_LEER', 'PLAZO_CREAR', 'TAREA_LEER', 'TAREA_CREAR',
    'TAREA_APROBAR', 'DOCUMENTO_LEER', 'DOCUMENTO_CREAR', 'DOCUMENTO_DESCARGAR',
    'AGENDA_LEER', 'AGENDA_CREAR', 'REPORTE_LEER', 'DASHBOARD_LEER',
    'CALENDARIO_PROPONER', 'BIBLIOTECA_LEER', 'BIBLIOTECA_GESTIONAR')
 WHERE r.codigo = 'ABOGADO'
ON DUPLICATE KEY UPDATE rol_id = roles_permisos.rol_id;

-- ABOGADO_AUXILIAR: 14 permisos
INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT r.id, p.id FROM roles r JOIN permisos p ON p.codigo IN (
    'CLIENTE_LEER', 'EXPEDIENTE_LEER', 'ACTUACION_LEER', 'ACTUACION_CREAR',
    'PLAZO_LEER', 'PLAZO_CREAR', 'TAREA_LEER', 'TAREA_CREAR',
    'DOCUMENTO_LEER', 'DOCUMENTO_CREAR', 'AGENDA_LEER', 'DASHBOARD_LEER',
    'CALENDARIO_PROPONER', 'BIBLIOTECA_LEER')
 WHERE r.codigo = 'ABOGADO_AUXILIAR'
ON DUPLICATE KEY UPDATE rol_id = roles_permisos.rol_id;

-- ASISTENTE_LEGAL: 13 permisos
INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT r.id, p.id FROM roles r JOIN permisos p ON p.codigo IN (
    'CLIENTE_LEER', 'EXPEDIENTE_LEER', 'ACTUACION_LEER', 'TAREA_LEER',
    'TAREA_CREAR', 'DOCUMENTO_LEER', 'DOCUMENTO_CREAR', 'DOCUMENTO_DESCARGAR',
    'AGENDA_LEER', 'AGENDA_CREAR', 'DASHBOARD_LEER', 'CALENDARIO_PROPONER',
    'BIBLIOTECA_LEER')
 WHERE r.codigo = 'ASISTENTE_LEGAL'
ON DUPLICATE KEY UPDATE rol_id = roles_permisos.rol_id;

-- CONTABILIDAD: 6 permisos
INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT r.id, p.id FROM roles r JOIN permisos p ON p.codigo IN (
    'CLIENTE_LEER', 'EXPEDIENTE_LEER', 'REPORTE_LEER', 'REPORTE_EXPORTAR',
    'DASHBOARD_LEER', 'BIBLIOTECA_LEER')
 WHERE r.codigo = 'CONTABILIDAD'
ON DUPLICATE KEY UPDATE rol_id = roles_permisos.rol_id;

-- RECEPCIONISTA: 5 permisos
INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT r.id, p.id FROM roles r JOIN permisos p ON p.codigo IN (
    'CLIENTE_LEER', 'CLIENTE_CREAR', 'AGENDA_LEER', 'DASHBOARD_LEER',
    'BIBLIOTECA_LEER')
 WHERE r.codigo = 'RECEPCIONISTA'
ON DUPLICATE KEY UPDATE rol_id = roles_permisos.rol_id;

-- CLIENTE: 3 permisos
INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT r.id, p.id FROM roles r JOIN permisos p ON p.codigo IN (
    'EXPEDIENTE_LEER', 'DOCUMENTO_LEER', 'DOCUMENTO_DESCARGAR')
 WHERE r.codigo = 'CLIENTE'
ON DUPLICATE KEY UPDATE rol_id = roles_permisos.rol_id;

-- =============================================================================
-- VERIFICACION (esperado: 8, 31, 123)
-- =============================================================================
SELECT (SELECT COUNT(*) FROM roles)          AS roles_esperado_8,
       (SELECT COUNT(*) FROM permisos)       AS permisos_esperado_31,
       (SELECT COUNT(*) FROM roles_permisos) AS pares_esperado_123;

-- Por rol (esperado: ADMINISTRADOR 31, SOCIO 28, ABOGADO 23, ABOGADO_AUXILIAR 14,
-- ASISTENTE_LEGAL 13, CONTABILIDAD 6, RECEPCIONISTA 5, CLIENTE 3)
SELECT r.codigo, COUNT(rp.permiso_id) AS permisos
  FROM roles r LEFT JOIN roles_permisos rp ON rp.rol_id = r.id
 GROUP BY r.id, r.codigo ORDER BY r.id;
