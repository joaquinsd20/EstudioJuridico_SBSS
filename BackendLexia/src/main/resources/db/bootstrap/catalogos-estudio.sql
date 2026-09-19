-- =============================================================================
-- LEXIA — Catalogos por defecto de un estudio NUEVO
-- =============================================================================
-- NO es una migracion: esta fuera de db/migration y db/seed, asi que Flyway no
-- lo ve. Lo ejecuta InicializadorEstudio una sola vez, al arrancar contra una
-- base sin ningun estudio (produccion recien instalada), dentro de la misma
-- transaccion que crea el estudio y su administrador.
--
-- Espera tres variables de sesion ya fijadas por quien lo invoca:
--   @estudio_id      el estudio recien creado
--   @admin_id        su administrador (registra y aprueba los feriados)
--   @dominio_correo  dominio de los usuarios INTERNO, tomado del correo del admin
--
-- El contenido es el mismo que usa R__seed_desarrollo.sql, sin los ids fijos:
-- lo que el sistema necesita para poder aperturar un expediente (estado_id es
-- NOT NULL) y para que RF-08 tenga reglas. NO incluye organos jurisdiccionales:
-- son los juzgados reales con los que trabaja el estudio y organo_id es
-- opcional; los carga el propio estudio.
-- =============================================================================

INSERT INTO especialidades (estudio_id, codigo, nombre, color_hex, orden) VALUES
(@estudio_id, 'CIVIL', 'Civil', '#7C3AED', 1),
(@estudio_id, 'FAMILIA', 'Familia', '#8B5CF6', 2),
(@estudio_id, 'LABORAL', 'Laboral', '#A78BFA', 3),
(@estudio_id, 'PENAL', 'Penal', '#C4B5FD', 4),
(@estudio_id, 'CONSTITUCIONAL', 'Constitucional', '#38BDF8', 5),
(@estudio_id, 'ADMINISTRATIVO', 'Administrativo', '#4ADE80', 6);

-- Las materias cuelgan de la especialidad por su codigo, no por un id fijo.
INSERT INTO materias (especialidad_id, nombre, via_procedimental)
SELECT e.id, m.nombre, m.via
  FROM (          SELECT 'FAMILIA' AS esp, 'Alimentos' AS nombre, 'Sumarisimo' AS via
        UNION ALL SELECT 'FAMILIA', 'Tenencia y regimen de visitas', 'Sumarisimo'
        UNION ALL SELECT 'FAMILIA', 'Divorcio por causal', 'Conocimiento'
        UNION ALL SELECT 'CIVIL', 'Indemnizacion por danos y perjuicios', 'Conocimiento'
        UNION ALL SELECT 'CIVIL', 'Obligacion de dar suma de dinero', 'Ejecutivo'
        UNION ALL SELECT 'CIVIL', 'Desalojo por ocupacion precaria', 'Sumarisimo'
        UNION ALL SELECT 'CIVIL', 'Rescision de contrato de compraventa', 'Abreviado'
        UNION ALL SELECT 'LABORAL', 'Despido arbitrario', 'Ordinario laboral'
        UNION ALL SELECT 'LABORAL', 'Pago de beneficios sociales', 'Abreviado laboral'
        UNION ALL SELECT 'PENAL', 'Hurto agravado', 'Proceso comun'
        UNION ALL SELECT 'CONSTITUCIONAL', 'Accion de amparo', 'Unico'
        UNION ALL SELECT 'ADMINISTRATIVO', 'Impugnacion de resolucion administrativa', 'Especial') m
  JOIN especialidades e ON e.estudio_id = @estudio_id AND e.codigo = m.esp;

INSERT INTO estados_expediente (estudio_id, codigo, nombre, color_hex, es_estado_final, cuenta_como_activo, orden) VALUES
(@estudio_id, 'CONSULTA', 'Consulta', '#94A3B8', 0, 0, 1),
(@estudio_id, 'PROSPECTO', 'Prospecto', '#A78BFA', 0, 0, 2),
(@estudio_id, 'EVALUACION', 'Evaluacion', '#818CF8', 0, 0, 3),
(@estudio_id, 'ACTIVO', 'Activo', '#16A34A', 0, 1, 4),
(@estudio_id, 'TRAMITE', 'En tramite', '#D97706', 0, 1, 5),
(@estudio_id, 'PENDIENTE', 'Pendiente', '#F59E0B', 0, 1, 6),
(@estudio_id, 'SUSPENDIDO', 'Suspendido', '#FB923C', 0, 0, 7),
(@estudio_id, 'ARCHIVADO', 'Archivado', '#6B7280', 1, 0, 8),
(@estudio_id, 'CONCLUIDO', 'Concluido', '#475569', 1, 0, 9),
(@estudio_id, 'GANADO', 'Ganado', '#059669', 1, 0, 10),
(@estudio_id, 'PERDIDO', 'Perdido', '#DC2626', 1, 0, 11),
(@estudio_id, 'DERIVADO', 'Derivado', '#0EA5E9', 1, 0, 12);

-- Las cuatro columnas booleanas son el motor de RF-08: deciden que se dispara
-- al registrar cada tipo de actuacion. El estudio puede cambiarlas despues.
INSERT INTO tipos_actuacion (estudio_id, codigo, nombre, categoria,
                             genera_plazo, genera_tarea, genera_evento_agenda, requiere_documento) VALUES
(@estudio_id, 'PRESENTACION_DEMANDA', 'Presentacion de demanda', 'ESCRITO', 0, 1, 0, 1),
(@estudio_id, 'REDACCION_ESCRITO', 'Redaccion de escrito', 'ESCRITO', 0, 1, 0, 1),
(@estudio_id, 'CONTESTACION', 'Contestacion de demanda', 'ESCRITO', 0, 1, 0, 1),
(@estudio_id, 'RECURSO_APELACION', 'Recurso de apelacion', 'ESCRITO', 1, 1, 0, 1),
(@estudio_id, 'NOTIFICACION', 'Notificacion recibida', 'NOTIFICACION', 1, 1, 0, 0),
(@estudio_id, 'AUDIENCIA', 'Audiencia', 'AUDIENCIA', 0, 1, 1, 0),
(@estudio_id, 'AUDIENCIA_TESTIMONIAL', 'Audiencia testimonial', 'AUDIENCIA', 0, 1, 1, 0),
(@estudio_id, 'DILIGENCIA', 'Diligencia', 'DILIGENCIA', 0, 1, 1, 0),
(@estudio_id, 'INSPECCION_OCULAR', 'Inspeccion ocular', 'DILIGENCIA', 0, 1, 1, 1),
(@estudio_id, 'LLAMADA', 'Llamada telefonica', 'COMUNICACION', 0, 0, 0, 0),
(@estudio_id, 'REUNION_CLIENTE', 'Reunion con cliente', 'COMUNICACION', 0, 0, 1, 0),
(@estudio_id, 'TRAMITE_ADMIN', 'Tramite administrativo', 'ADMINISTRATIVO', 0, 1, 0, 0);

INSERT INTO tipos_plazo (estudio_id, codigo, nombre, dias_por_defecto, tipo_computo_defecto, nivel_riesgo_defecto) VALUES
(@estudio_id, 'CONTESTACION', 'Contestacion de demanda', 30, 'HABILES', 'CRITICO'),
(@estudio_id, 'PROBATORIO', 'Etapa probatoria', 20, 'HABILES', 'ALTO'),
(@estudio_id, 'RECURSO_APELACION', 'Recurso de apelacion', 5, 'HABILES', 'CRITICO'),
(@estudio_id, 'CASACION', 'Recurso de casacion', 10, 'HABILES', 'CRITICO'),
(@estudio_id, 'SUBSANACION', 'Subsanacion de observacion', 3, 'HABILES', 'ALTO'),
(@estudio_id, 'PAGO_TASA', 'Pago de tasa o arancel', 15, 'CALENDARIO', 'MEDIO');

INSERT INTO etiquetas (estudio_id, nombre, color_hex) VALUES
(@estudio_id, 'Urgente', '#DC2626'),
(@estudio_id, 'Prioritario', '#EA580C'),
(@estudio_id, 'En espera', '#F59E0B'),
(@estudio_id, 'Prueba clave', '#7C3AED'),
(@estudio_id, 'Cargo de presentacion', '#0EA5E9'),
(@estudio_id, 'Confidencial', '#1F2937'),
(@estudio_id, 'Documento del cliente', '#16A34A');

INSERT INTO configuracion (estudio_id, clave, valor, tipo_dato, descripcion) VALUES
(@estudio_id, 'dominio_correo_interno', @dominio_correo, 'TEXTO', 'Dominio exigido a los usuarios INTERNO. Lo valida UsuarioService'),
(@estudio_id, 'alertas_dias_previos', '[15,10,7,5,3,1,0]', 'JSON', 'Dias de aviso previo por defecto (RF-10)'),
(@estudio_id, 'alertas_dias_posteriores', '[-1,-3]', 'JSON', 'Avisos posteriores al vencimiento'),
(@estudio_id, 'plazo_colchon_interno_dias', '2', 'NUMERO', 'Anticipacion de la fecha interna de revision'),
(@estudio_id, 'dashboard_ventana_por_vencer', '3', 'NUMERO', 'Dias de la tarjeta Plazos por vencer'),
(@estudio_id, 'dashboard_ventana_audiencias', '7', 'NUMERO', 'Dias de la tarjeta Audiencias proximas'),
(@estudio_id, 'sesion_inactividad_minutos', '30', 'NUMERO', 'Cierre de sesion por inactividad (RNF-05)'),
(@estudio_id, 'intentos_login_maximos', '5', 'NUMERO', 'Bloqueo de cuenta por intentos fallidos'),
(@estudio_id, 'archivo_tamano_maximo_mb', '50', 'NUMERO', 'Tamano maximo por archivo cargado'),
(@estudio_id, 'doble_factor_obligatorio', 'false', 'BOOLEANO', '2FA desactivado en el MVP (HU-01)'),
(@estudio_id, 'backup_automatico', 'true', 'BOOLEANO', 'Respaldo automatico diario (RNF-10)');

-- -----------------------------------------------------------------------------
-- Feriados nacionales del anio en curso y el siguiente. Sin ellos, todo plazo
-- en dias HABILES se calcula mal desde el primer dia: es la razon de ser de
-- calendario_judicial. Son globales (estudio_id NULL), asi que se omiten los
-- que ya existan. Semana Santa NO va: es movil y se carga cada anio, igual que
-- en la semilla. Los anios siguientes los carga el Administrador.
-- -----------------------------------------------------------------------------
INSERT INTO calendario_judicial
    (estudio_id, fecha_inicio, fecha_fin, tipo, descripcion, ambito,
     estado, usuario_registro_id, usuario_aprobacion_id, fecha_aprobacion)
SELECT NULL, f.d, f.d, 'FERIADO_NACIONAL', f.txt, NULL, 'APROBADO', @admin_id, @admin_id, NOW(3)
  FROM (
                  SELECT MAKEDATE(a.anio, 1) AS d, 'Ano Nuevo' AS txt FROM (SELECT YEAR(CURRENT_DATE) AS anio UNION SELECT YEAR(CURRENT_DATE) + 1) a
        UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio, '-05-01'), '%Y-%m-%d'), 'Dia del Trabajo'        FROM (SELECT YEAR(CURRENT_DATE) AS anio UNION SELECT YEAR(CURRENT_DATE) + 1) a
        UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio, '-06-07'), '%Y-%m-%d'), 'Batalla de Arica'       FROM (SELECT YEAR(CURRENT_DATE) AS anio UNION SELECT YEAR(CURRENT_DATE) + 1) a
        UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio, '-06-29'), '%Y-%m-%d'), 'San Pedro y San Pablo'  FROM (SELECT YEAR(CURRENT_DATE) AS anio UNION SELECT YEAR(CURRENT_DATE) + 1) a
        UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio, '-07-23'), '%Y-%m-%d'), 'Dia de la Fuerza Aerea' FROM (SELECT YEAR(CURRENT_DATE) AS anio UNION SELECT YEAR(CURRENT_DATE) + 1) a
        UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio, '-07-28'), '%Y-%m-%d'), 'Fiestas Patrias'        FROM (SELECT YEAR(CURRENT_DATE) AS anio UNION SELECT YEAR(CURRENT_DATE) + 1) a
        UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio, '-07-29'), '%Y-%m-%d'), 'Fiestas Patrias'        FROM (SELECT YEAR(CURRENT_DATE) AS anio UNION SELECT YEAR(CURRENT_DATE) + 1) a
        UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio, '-08-06'), '%Y-%m-%d'), 'Batalla de Junin'       FROM (SELECT YEAR(CURRENT_DATE) AS anio UNION SELECT YEAR(CURRENT_DATE) + 1) a
        UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio, '-08-30'), '%Y-%m-%d'), 'Santa Rosa de Lima'     FROM (SELECT YEAR(CURRENT_DATE) AS anio UNION SELECT YEAR(CURRENT_DATE) + 1) a
        UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio, '-10-08'), '%Y-%m-%d'), 'Combate de Angamos'     FROM (SELECT YEAR(CURRENT_DATE) AS anio UNION SELECT YEAR(CURRENT_DATE) + 1) a
        UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio, '-11-01'), '%Y-%m-%d'), 'Todos los Santos'       FROM (SELECT YEAR(CURRENT_DATE) AS anio UNION SELECT YEAR(CURRENT_DATE) + 1) a
        UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio, '-12-08'), '%Y-%m-%d'), 'Inmaculada Concepcion'  FROM (SELECT YEAR(CURRENT_DATE) AS anio UNION SELECT YEAR(CURRENT_DATE) + 1) a
        UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio, '-12-09'), '%Y-%m-%d'), 'Batalla de Ayacucho'    FROM (SELECT YEAR(CURRENT_DATE) AS anio UNION SELECT YEAR(CURRENT_DATE) + 1) a
        UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio, '-12-25'), '%Y-%m-%d'), 'Navidad'                FROM (SELECT YEAR(CURRENT_DATE) AS anio UNION SELECT YEAR(CURRENT_DATE) + 1) a
       ) f
 WHERE NOT EXISTS (SELECT 1 FROM calendario_judicial c
                    WHERE c.estudio_id IS NULL AND c.tipo = 'FERIADO_NACIONAL'
                      AND c.fecha_inicio = f.d);
