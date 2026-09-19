-- =============================================================================
-- LEXIA — Semilla de DESARROLLO
-- =============================================================================
-- ¡¡ESTE ARCHIVO NUNCA SE EJECUTA EN PRODUCCION!!
-- Flyway lo aisla por perfil:
--   application-dev.yml  -> flyway.locations: classpath:db/migration
--   application-prod.yml -> flyway.locations: classpath:db/migration
--                           flyway.ignore-migration-patterns: repeatable:*
--
-- El prefijo R__ lo hace REPETIBLE: Flyway lo re-ejecuta cuando cambia su
-- contenido. Por eso empieza vaciando lo que el mismo escribio: ejecutarlo dos
-- veces deja la base exactamente igual que ejecutarlo una vez.
--
-- FECHAS RELATIVAS — corrigiendo un error de la version anterior de esta semilla
-- ---------------------------------------------------------------------------
-- La semilla anterior fijaba fechas absolutas ('2026-09-07') para que el
-- Dashboard mostrara los numeros exactos del mockup. Dos dias despues ya no
-- cuadraba: los plazos que "vencian hoy" pasaban a vencidos y las tarjetas
-- cambiaban solas. Aqui TODO se calcula contra CURRENT_DATE, asi que el
-- Dashboard da los mismos numeros hoy, mañana y dentro de un año.
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- Limpieza idempotente
-- -----------------------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE auditoria;
TRUNCATE TABLE notificaciones;
TRUNCATE TABLE comentarios;
TRUNCATE TABLE documento_etiquetas;
TRUNCATE TABLE documento_versiones;
TRUNCATE TABLE documentos;
TRUNCATE TABLE evento_participantes;
TRUNCATE TABLE eventos_agenda;
TRUNCATE TABLE subtareas;
TRUNCATE TABLE tareas;
TRUNCATE TABLE alertas;
TRUNCATE TABLE plazos;
TRUNCATE TABLE resoluciones;
TRUNCATE TABLE actuaciones;
TRUNCATE TABLE expediente_contrapartes;
TRUNCATE TABLE expediente_equipo;
TRUNCATE TABLE expedientes;
TRUNCATE TABLE contrapartes;
TRUNCATE TABLE cliente_contactos;
TRUNCATE TABLE clientes;
TRUNCATE TABLE configuracion;
TRUNCATE TABLE etiquetas;
TRUNCATE TABLE calendario_judicial;
TRUNCATE TABLE tipos_plazo;
TRUNCATE TABLE tipos_actuacion;
TRUNCATE TABLE estados_expediente;
TRUNCATE TABLE organos_jurisdiccionales;
TRUNCATE TABLE materias;
TRUNCATE TABLE especialidades;
TRUNCATE TABLE usuarios_roles;
TRUNCATE TABLE usuarios;
TRUNCATE TABLE estudios;
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- ESTUDIO
-- =============================================================================
INSERT INTO estudios (id, razon_social, nombre_comercial, ruc, direccion, telefono, email) VALUES
(1, 'Estudio Ramirez Abogados S.A.C.', 'Estudio Ramirez Abogados', '20521478963',
   'Av. Larco 1234, Urb. Los Jardines, Trujillo', '044-201548', 'contacto@lexia.com');

-- =============================================================================
-- ROLES, PERMISOS Y SU MATRIZ: ya NO viven aqui.
-- Pasaron a V12__roles_y_permisos_del_sistema.sql, porque produccion (que no
-- carga esta semilla) los necesita igual. Por eso tampoco se truncan arriba:
-- esta semilla ya no es su duena. Un permiso nuevo va SOLO en una migracion.
-- Los usuarios de abajo se asignan a los roles por id (1..8), que V12 fija.

-- =============================================================================
-- USUARIOS — nombres del prototipo, para que las demos coincidan
-- password_hash: BCrypt cost 12 de la contrasena de DESARROLLO 'Lexia.Dev.2026',
-- igual para los 11 usuarios. Sirve para probar el login en local; todos tienen
-- debe_cambiar_password = 1. Esta semilla no corre en produccion: el perfil prod
-- no incluye db/seed en flyway.locations.
-- =============================================================================
INSERT INTO usuarios (id, estudio_id, tipo_usuario, codigo_usuario, nombres, apellidos,
                      tipo_documento, numero_documento, email, password_hash, telefono,
                      cargo, numero_colegiatura, estado, debe_cambiar_password) VALUES
( 1,1,'INTERNO','U-0001','Sofia',     'Ramirez Delgado', 'DNI','44112233','sofia.ramirez@lexia.com',
    '$2a$12$6kg/Qlbe39nKhdxrNi4BAe7N7URnAFo7xnwucHXE2.kaetLjOimLK','949112233','Socia principal','CAL-45821','ACTIVO',1),
( 2,1,'INTERNO','U-0002','Ana',       'Morales Vega',    'DNI','44223344','ana.morales@lexia.com',
    '$2a$12$6kg/Qlbe39nKhdxrNi4BAe7N7URnAFo7xnwucHXE2.kaetLjOimLK','949223344','Abogada','CAL-51203','ACTIVO',1),
( 3,1,'INTERNO','U-0003','Luis',      'Torres Quiroz',   'DNI','44334455','luis.torres@lexia.com',
    '$2a$12$6kg/Qlbe39nKhdxrNi4BAe7N7URnAFo7xnwucHXE2.kaetLjOimLK','949334455','Abogado','CAL-49877','ACTIVO',1),
( 4,1,'INTERNO','U-0004','Maria',     'Lopez Sanchez',   'DNI','44445566','maria.lopez@lexia.com',
    '$2a$12$6kg/Qlbe39nKhdxrNi4BAe7N7URnAFo7xnwucHXE2.kaetLjOimLK','949445566','Abogada auxiliar','CAL-58990','ACTIVO',1),
( 5,1,'INTERNO','U-0005','Ana Maria', 'Fernandez Salas', 'DNI','44556677','anamaria.fernandez@lexia.com',
    '$2a$12$6kg/Qlbe39nKhdxrNi4BAe7N7URnAFo7xnwucHXE2.kaetLjOimLK','949556677','Abogada','CAL-47310','ACTIVO',1),
( 6,1,'INTERNO','U-0006','Juan',      'Perez Rojas',     'DNI','44667788','juan.perez@lexia.com',
    '$2a$12$6kg/Qlbe39nKhdxrNi4BAe7N7URnAFo7xnwucHXE2.kaetLjOimLK','949667788','Abogado','CAL-46002','ACTIVO',1),
( 7,1,'INTERNO','U-0007','Carlos',    'Mendoza Rios',    'DNI','44778899','carlos.mendoza@lexia.com',
    '$2a$12$6kg/Qlbe39nKhdxrNi4BAe7N7URnAFo7xnwucHXE2.kaetLjOimLK','949778899','Administrador de sistemas',NULL,'ACTIVO',1),
( 8,1,'INTERNO','U-0008','Rosa',      'Jimenez Vargas',  'DNI','44889900','rosa.jimenez@lexia.com',
    '$2a$12$6kg/Qlbe39nKhdxrNi4BAe7N7URnAFo7xnwucHXE2.kaetLjOimLK','949889900','Asistente legal',NULL,'ACTIVO',1),
( 9,1,'INTERNO','U-0009','Roberto',   'Diaz Camacho',    'DNI','44990011','roberto.diaz@lexia.com',
    '$2a$12$6kg/Qlbe39nKhdxrNi4BAe7N7URnAFo7xnwucHXE2.kaetLjOimLK','949990011','Contador',NULL,'ACTIVO',1),
(10,1,'INTERNO','U-0010','Lucia',     'Castillo Reyes',  'DNI','44001122','lucia.castillo@lexia.com',
    '$2a$12$6kg/Qlbe39nKhdxrNi4BAe7N7URnAFo7xnwucHXE2.kaetLjOimLK','949001122','Recepcionista',NULL,'ACTIVO',1);

-- Sofia es SOCIA y ABOGADA a la vez: el caso real que justifica los roles M:N.
-- Como SOCIA tiene CALENDARIO_APROBAR, que es el permiso que cierra el flujo
-- de aprobacion de suspensiones de plazos.
INSERT INTO usuarios_roles (usuario_id, rol_id) VALUES
(1,2),(1,3), (2,3), (3,3), (4,4), (5,3), (6,3), (7,1), (8,5), (9,6), (10,7);

-- =============================================================================
-- CATALOGOS
-- =============================================================================
INSERT INTO especialidades (id, estudio_id, codigo, nombre, color_hex, orden) VALUES
(1,1,'CIVIL','Civil','#7C3AED',1), (2,1,'FAMILIA','Familia','#8B5CF6',2),
(3,1,'LABORAL','Laboral','#A78BFA',3), (4,1,'PENAL','Penal','#C4B5FD',4),
(5,1,'CONSTITUCIONAL','Constitucional','#38BDF8',5), (6,1,'ADMINISTRATIVO','Administrativo','#4ADE80',6);

INSERT INTO materias (id, especialidad_id, nombre, via_procedimental) VALUES
( 1,2,'Alimentos','Sumarisimo'),
( 2,2,'Tenencia y regimen de visitas','Sumarisimo'),
( 3,2,'Divorcio por causal','Conocimiento'),
( 4,1,'Indemnizacion por danos y perjuicios','Conocimiento'),
( 5,1,'Obligacion de dar suma de dinero','Ejecutivo'),
( 6,1,'Desalojo por ocupacion precaria','Sumarisimo'),
( 7,1,'Rescision de contrato de compraventa','Abreviado'),
( 8,3,'Despido arbitrario','Ordinario laboral'),
( 9,3,'Pago de beneficios sociales','Abreviado laboral'),
(10,4,'Hurto agravado','Proceso comun'),
(11,5,'Accion de amparo','Unico'),
(12,6,'Impugnacion de resolucion administrativa','Especial');

-- Las cuatro columnas CEJ (V9) permiten COMPONER el numero de expediente al
-- aperturarlo, en vez de teclearlo. El distrito judicial de La Libertad es 1601;
-- 1801 es Lima y no corresponde a este estudio.
-- Una fiscalia lleva carpeta fiscal y una entidad administrativa no tiene
-- numeracion judicial: por eso esas dos van con las cuatro columnas en NULL.
INSERT INTO organos_jurisdiccionales (id, estudio_id, tipo, nombre, distrito_judicial, instancia,
                                      distrito_judicial_codigo, codigo_tipo_organo,
                                      codigo_especialidad, numero_organo) VALUES
(1,1,'JUZGADO','2do Juzgado Civil de Trujillo','La Libertad','PRIMERA','1601','JR','CI',2),
(2,1,'SALA','1ra Sala Penal de Apelaciones de La Libertad','La Libertad','SEGUNDA','1601','SP','PE',1),
(3,1,'SALA','Sala Constitucional de La Libertad','La Libertad','SEGUNDA','1601','SP','CO',1),
(4,1,'JUZGADO','3er Juzgado de Familia de Trujillo','La Libertad','PRIMERA','1601','JR','FC',3),
(5,1,'FISCALIA','1ra Fiscalia Provincial Penal Corporativa de Trujillo','La Libertad','PRIMERA',
   NULL,NULL,NULL,NULL),
(6,1,'JUZGADO','Juzgado Especializado de Trabajo de Trujillo','La Libertad','PRIMERA','1601','JR','LA',1),
(7,1,'ENTIDAD_ADMINISTRATIVA','Municipalidad Provincial de Trujillo','La Libertad',NULL,
   NULL,NULL,NULL,NULL),
-- Los dos que faltaban. Sin ellos, el expediente penal quedaba colgado de una
-- fiscalia y el de amparo de una Sala, ambos con numeros CEJ que decian 'JR'.
(8,1,'JUZGADO','Juzgado Constitucional de Trujillo','La Libertad','PRIMERA','1601','JR','CO',1),
(9,1,'JUZGADO','1er Juzgado de Investigacion Preparatoria de Trujillo','La Libertad','PRIMERA',
   '1601','JR','PE',1);

INSERT INTO estados_expediente (id, estudio_id, codigo, nombre, color_hex, es_estado_final, cuenta_como_activo, orden) VALUES
( 1,1,'CONSULTA','Consulta','#94A3B8',0,0,1),   ( 2,1,'PROSPECTO','Prospecto','#A78BFA',0,0,2),
( 3,1,'EVALUACION','Evaluacion','#818CF8',0,0,3),( 4,1,'ACTIVO','Activo','#16A34A',0,1,4),
( 5,1,'TRAMITE','En tramite','#D97706',0,1,5),   ( 6,1,'PENDIENTE','Pendiente','#F59E0B',0,1,6),
( 7,1,'SUSPENDIDO','Suspendido','#FB923C',0,0,7),( 8,1,'ARCHIVADO','Archivado','#6B7280',1,0,8),
( 9,1,'CONCLUIDO','Concluido','#475569',1,0,9),  (10,1,'GANADO','Ganado','#059669',1,0,10),
(11,1,'PERDIDO','Perdido','#DC2626',1,0,11),     (12,1,'DERIVADO','Derivado','#0EA5E9',1,0,12);

INSERT INTO tipos_actuacion (id, estudio_id, codigo, nombre, categoria,
                             genera_plazo, genera_tarea, genera_evento_agenda, requiere_documento) VALUES
( 1,1,'PRESENTACION_DEMANDA','Presentacion de demanda','ESCRITO',0,1,0,1),
( 2,1,'REDACCION_ESCRITO','Redaccion de escrito','ESCRITO',0,1,0,1),
( 3,1,'CONTESTACION','Contestacion de demanda','ESCRITO',0,1,0,1),
( 4,1,'RECURSO_APELACION','Recurso de apelacion','ESCRITO',1,1,0,1),
( 5,1,'NOTIFICACION','Notificacion recibida','NOTIFICACION',1,1,0,0),
( 6,1,'AUDIENCIA','Audiencia','AUDIENCIA',0,1,1,0),
( 7,1,'AUDIENCIA_TESTIMONIAL','Audiencia testimonial','AUDIENCIA',0,1,1,0),
( 8,1,'DILIGENCIA','Diligencia','DILIGENCIA',0,1,1,0),
( 9,1,'INSPECCION_OCULAR','Inspeccion ocular','DILIGENCIA',0,1,1,1),
(10,1,'LLAMADA','Llamada telefonica','COMUNICACION',0,0,0,0),
(11,1,'REUNION_CLIENTE','Reunion con cliente','COMUNICACION',0,0,1,0),
(12,1,'TRAMITE_ADMIN','Tramite administrativo','ADMINISTRATIVO',0,1,0,0);

INSERT INTO tipos_plazo (id, estudio_id, codigo, nombre, dias_por_defecto, tipo_computo_defecto, nivel_riesgo_defecto) VALUES
(1,1,'CONTESTACION','Contestacion de demanda',30,'HABILES','CRITICO'),
(2,1,'PROBATORIO','Etapa probatoria',20,'HABILES','ALTO'),
(3,1,'RECURSO_APELACION','Recurso de apelacion',5,'HABILES','CRITICO'),
(4,1,'CASACION','Recurso de casacion',10,'HABILES','CRITICO'),
(5,1,'SUBSANACION','Subsanacion de observacion',3,'HABILES','ALTO'),
(6,1,'PAGO_TASA','Pago de tasa o arancel',15,'CALENDARIO','MEDIO');

-- -----------------------------------------------------------------------------
-- CALENDARIO JUDICIAL
-- Feriados nacionales del Peru para el año en curso y el siguiente, generados
-- de forma relativa para que la semilla no caduque. Cargados por el
-- Administrador (usuario 7) y APROBADOS por la Socia (usuario 1).
-- Semana Santa se omite a proposito: es movil y la carga el estudio cada año.
-- -----------------------------------------------------------------------------
SET @anio := YEAR(CURRENT_DATE);

INSERT INTO calendario_judicial
    (estudio_id, fecha_inicio, fecha_fin, tipo, descripcion, ambito,
     estado, usuario_registro_id, usuario_aprobacion_id, fecha_aprobacion)
SELECT NULL, d, d, 'FERIADO_NACIONAL', txt, NULL, 'APROBADO', 7, 1, NOW(3)
FROM (
    SELECT MAKEDATE(a.anio,1)                              AS d, 'Ano Nuevo' AS txt FROM (SELECT @anio anio UNION SELECT @anio+1) a
    UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio,'-05-01'),'%Y-%m-%d'),'Dia del Trabajo'        FROM (SELECT @anio anio UNION SELECT @anio+1) a
    UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio,'-06-07'),'%Y-%m-%d'),'Batalla de Arica'       FROM (SELECT @anio anio UNION SELECT @anio+1) a
    UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio,'-06-29'),'%Y-%m-%d'),'San Pedro y San Pablo'  FROM (SELECT @anio anio UNION SELECT @anio+1) a
    UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio,'-07-23'),'%Y-%m-%d'),'Dia de la Fuerza Aerea' FROM (SELECT @anio anio UNION SELECT @anio+1) a
    UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio,'-07-28'),'%Y-%m-%d'),'Fiestas Patrias'        FROM (SELECT @anio anio UNION SELECT @anio+1) a
    UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio,'-07-29'),'%Y-%m-%d'),'Fiestas Patrias'        FROM (SELECT @anio anio UNION SELECT @anio+1) a
    UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio,'-08-06'),'%Y-%m-%d'),'Batalla de Junin'       FROM (SELECT @anio anio UNION SELECT @anio+1) a
    UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio,'-08-30'),'%Y-%m-%d'),'Santa Rosa de Lima'     FROM (SELECT @anio anio UNION SELECT @anio+1) a
    UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio,'-10-08'),'%Y-%m-%d'),'Combate de Angamos'     FROM (SELECT @anio anio UNION SELECT @anio+1) a
    UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio,'-11-01'),'%Y-%m-%d'),'Todos los Santos'       FROM (SELECT @anio anio UNION SELECT @anio+1) a
    UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio,'-12-08'),'%Y-%m-%d'),'Inmaculada Concepcion'  FROM (SELECT @anio anio UNION SELECT @anio+1) a
    UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio,'-12-09'),'%Y-%m-%d'),'Batalla de Ayacucho'    FROM (SELECT @anio anio UNION SELECT @anio+1) a
    UNION ALL SELECT STR_TO_DATE(CONCAT(a.anio,'-12-25'),'%Y-%m-%d'),'Navidad'                FROM (SELECT @anio anio UNION SELECT @anio+1) a
) f;

-- Ejemplo del flujo de aprobacion: una suspension de plazos que el Asistente
-- legal (usuario 8) detecto y registro, pendiente de que la Socia la apruebe.
-- Al estar en PROPUESTO, fn_es_dia_habil la IGNORA: no altera ningun calculo.
INSERT INTO calendario_judicial
    (estudio_id, fecha_inicio, fecha_fin, tipo, descripcion, norma_referencia, ambito,
     estado, usuario_registro_id)
VALUES
(1, DATE_ADD(CURRENT_DATE, INTERVAL 20 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 21 DAY),
    'SUSPENSION_PLAZOS',
    'Suspension de plazos por jornada de capacitacion del Poder Judicial',
    'Res. Adm. N.o 000-2026-CE-PJ (pendiente de verificacion)', 'La Libertad',
    'PROPUESTO', 8);

INSERT INTO etiquetas (id, estudio_id, nombre, color_hex) VALUES
(1,1,'Urgente','#DC2626'), (2,1,'Prioritario','#EA580C'), (3,1,'En espera','#F59E0B'),
(4,1,'Prueba clave','#7C3AED'), (5,1,'Cargo de presentacion','#0EA5E9'),
(6,1,'Confidencial','#1F2937'), (7,1,'Documento del cliente','#16A34A');

INSERT INTO configuracion (estudio_id, clave, valor, tipo_dato, descripcion) VALUES
(1,'dominio_correo_interno','lexia.com','TEXTO','Dominio exigido a los usuarios INTERNO. Lo valida UsuarioService'),
(1,'alertas_dias_previos','[15,10,7,5,3,1,0]','JSON','Dias de aviso previo por defecto (RF-10)'),
(1,'alertas_dias_posteriores','[-1,-3]','JSON','Avisos posteriores al vencimiento'),
(1,'plazo_colchon_interno_dias','2','NUMERO','Anticipacion de la fecha interna de revision'),
(1,'dashboard_ventana_por_vencer','3','NUMERO','Dias de la tarjeta "Plazos por vencer"'),
(1,'dashboard_ventana_audiencias','7','NUMERO','Dias de la tarjeta "Audiencias proximas"'),
(1,'sesion_inactividad_minutos','30','NUMERO','Cierre de sesion por inactividad (RNF-05)'),
(1,'intentos_login_maximos','5','NUMERO','Bloqueo de cuenta por intentos fallidos'),
(1,'archivo_tamano_maximo_mb','50','NUMERO','Tamano maximo por archivo cargado'),
(1,'doble_factor_obligatorio','false','BOOLEANO','2FA desactivado en el MVP (HU-01)'),
(1,'backup_automatico','true','BOOLEANO','Respaldo automatico diario (RNF-10)');

-- =============================================================================
-- CLIENTES
-- =============================================================================
INSERT INTO clientes (id, estudio_id, tipo_persona, tipo_documento, numero_documento,
                      nombres, apellido_paterno, apellido_materno, razon_social,
                      email, telefono_principal, direccion, distrito, provincia, departamento,
                      estado, origen_captacion, situacion_financiera, abogado_responsable_id,
                      fecha_ultimo_contacto) VALUES
(1,1,'NATURAL','DNI','44219876','Ana Maria','Fernandez','Salas',NULL,
   'anamaria.fsalas@correo.com','987654321','Av. Husares de Junin 456','Trujillo','Trujillo','La Libertad',
   'ACTIVO','REFERIDO','ESTABLE',1, DATE_SUB(NOW(3), INTERVAL 3 DAY)),
(2,1,'JURIDICA','RUC','20481234567',NULL,NULL,NULL,'Banco del Peru S.A.A.',
   'legal@bancodelperu.pe','044-481000','Jr. Pizarro 480','Trujillo','Trujillo','La Libertad',
   'ACTIVO','LICITACION','SOLIDA',2, DATE_SUB(NOW(3), INTERVAL 10 DAY)),
(3,1,'NATURAL','DNI','41556230','Ania','Morales','Vega',NULL,
   'ania.morales@correo.com','976112233','Urb. El Golf Mz D Lt 12','Victor Larco','Trujillo','La Libertad',
   'ACTIVO','WEB','REGULAR',3, DATE_SUB(NOW(3), INTERVAL 6 DAY)),
(4,1,'NATURAL','DNI','45890123','Luis Alberto','Torres','Quiroz',NULL,
   'luis.torres.q@correo.com','965223344','Av. America Sur 2130','Trujillo','Trujillo','La Libertad',
   'ACTIVO','RECOMENDACION','ESTABLE',3, DATE_SUB(NOW(3), INTERVAL 4 DAY)),
(5,1,'NATURAL','DNI','43217788','Sofia','Fernandez','Rojas',NULL,
   'sofia.frojas@correo.com','954334455','Calle Bolivar 145','Trujillo','Trujillo','La Libertad',
   'INACTIVO','REFERIDO','REGULAR',1, DATE_SUB(NOW(3), INTERVAL 110 DAY)),
(6,1,'JURIDICA','RUC','20602345678',NULL,NULL,NULL,'Constructora Andes S.A.C.',
   'contacto@andes.com.pe','044-556677','Av. Nicolas de Pierola 890','Trujillo','Trujillo','La Libertad',
   'ACTIVO','REFERIDO','SOLIDA',6, DATE_SUB(NOW(3), INTERVAL 5 DAY)),
(7,1,'NATURAL','DNI','40998877','Juan Carlos','Lopez','Ramos',NULL,
   'juancarlos.lopez@correo.com','943445566','Jr. Independencia 320','Trujillo','Trujillo','La Libertad',
   'ACTIVO','WEB','RIESGO',2, DATE_SUB(NOW(3), INTERVAL 14 DAY)),
(8,1,'JURIDICA','RUC','20553456789',NULL,NULL,NULL,'Transportes Victoria E.I.R.L.',
   'operaciones@tvictoria.pe','044-667788','Panamericana Norte Km 558','Moche','Trujillo','La Libertad',
   'PROSPECTO','REFERIDO',NULL,2, DATE_SUB(NOW(3), INTERVAL 2 DAY));

-- Usuario del portal del cliente: correo externo, rol CLIENTE.
-- Esto es imposible en las versiones anteriores del esquema.
INSERT INTO usuarios (id, estudio_id, tipo_usuario, nombres, apellidos, email, password_hash,
                      estado, cliente_id, debe_cambiar_password) VALUES
(11,1,'CLIENTE','Ana Maria','Fernandez Salas','anamaria.fsalas@correo.com',
   '$2a$12$6kg/Qlbe39nKhdxrNi4BAe7N7URnAFo7xnwucHXE2.kaetLjOimLK','ACTIVO',1,1);
INSERT INTO usuarios_roles (usuario_id, rol_id) VALUES (11,8);

INSERT INTO cliente_contactos (cliente_id, nombre_completo, tipo_documento, numero_documento, cargo,
                               email, telefono, es_representante_legal, puede_recibir_documentos, numero_poder) VALUES
(2,'Ricardo Alvarez Puente','DNI','09887766','Gerente Legal','ralvarez@bancodelperu.pe','999222333',1,1,'P-2024-11872'),
(2,'Patricia Luna Mendez','DNI','10554433','Jefa de Cobranzas','pluna@bancodelperu.pe','999222444',0,1,NULL),
(6,'Sergio Montes Caceres','DNI','42110099','Gerente General','smontes@andes.com.pe','977111000',1,1,'P-2023-04521'),
(8,'Diana Reyes Castillo','DNI','46778899','Jefa de Operaciones','dreyes@tvictoria.pe','966111222',1,0,NULL),
(1,'Miguel Fernandez Salas','DNI','44219877','Hermano autorizado',NULL,'987654322',0,1,NULL);

INSERT INTO contrapartes (id, estudio_id, tipo_persona, tipo_documento, numero_documento,
                          nombre_completo, abogado_contrario, empresa_vinculada) VALUES
(1,1,'ENTIDAD_PUBLICA','RUC','20154321098','Municipalidad Provincial de Trujillo','Dr. Hernan Salas Vega',NULL),
(2,1,'JURIDICA','RUC','20100210909','Seguros La Positiva S.A.','Dra. Carmen Iglesias','Grupo La Positiva'),
(3,1,'JURIDICA','RUC','20601122334','Inversiones del Norte S.A.C.',NULL,'Grupo Norte'),
(4,1,'NATURAL','DNI','42113355','Carlos Quiroz Mendoza','Dr. Alberto Rios',NULL),
(5,1,'JURIDICA','RUC','20487654321','Distribuidora Lima Products S.A.C.',NULL,NULL),
(6,1,'ENTIDAD_PUBLICA','RUC','20131373237','Ministerio de Salud',NULL,NULL);

-- =============================================================================
-- EXPEDIENTES
-- =============================================================================
INSERT INTO expedientes (id, estudio_id, codigo_interno, numero_expediente, numero_correlativo, anio,
                         cliente_id, especialidad_id, materia_id, organo_id, estado_id,
                         etapa_procesal, instancia, abogado_responsable_id, socio_supervisor_id,
                         nivel_riesgo, cuantia, fecha_inicio, fecha_ingreso_estudio,
                         fecha_ultima_actuacion, es_confidencial, resumen) VALUES
( 1,1,'EXP-0142',CONCAT('01452-',@anio,'-0-1601-JR-FC-03'),'01452',@anio,1,2,1,4,4,
   'Postulatoria','PRIMERA',1,1,'CRITICO',18000.00,
   DATE_SUB(CURRENT_DATE, INTERVAL 210 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 215 DAY),
   DATE_SUB(NOW(3), INTERVAL 3 DAY),0,'Demanda de alimentos a favor de menor.'),
( 2,1,'EXP-0238',CONCAT('02388-',@anio-1,'-0-1601-JR-CI-02'),'02388',@anio-1,7,1,4,1,5,
   'Probatoria','PRIMERA',2,1,'ALTO',250000.00,
   DATE_SUB(CURRENT_DATE, INTERVAL 440 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 445 DAY),
   DATE_SUB(NOW(3), INTERVAL 5 DAY),0,'Indemnizacion por danos derivada de accidente de transito.'),
( 3,1,'EXP-0332',CONCAT('03321-',@anio-2,'-0-1601-JR-FC-03'),'03321',@anio-2,3,2,1,4,5,
   'Impugnatoria','PRIMERA',3,1,'ALTO',24000.00,
   DATE_SUB(CURRENT_DATE, INTERVAL 660 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 665 DAY),
   DATE_SUB(NOW(3), INTERVAL 4 DAY),0,'Alimentos. Apelacion de la sentencia de primera instancia.'),
( 4,1,'EXP-0774',CONCAT('07745-',@anio,'-0-1601-JR-FC-03'),'07745',@anio,4,2,2,4,4,
   'Probatoria','PRIMERA',5,1,'ALTO',0.00,
   DATE_SUB(CURRENT_DATE, INTERVAL 140 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 142 DAY),
   DATE_SUB(NOW(3), INTERVAL 7 DAY),0,'Tenencia y regimen de visitas. Informe psicologico pendiente.'),
( 5,1,'EXP-0052',CONCAT('00521-',@anio,'-0-1601-JR-FC-03'),'00521',@anio,5,2,2,4,8,
   'Ejecucion','PRIMERA',1,1,'BAJO',0.00,
   DATE_SUB(CURRENT_DATE, INTERVAL 240 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 243 DAY),
   DATE_SUB(NOW(3), INTERVAL 110 DAY),0,'Tenencia. Conciliado y archivado.'),
( 6,1,'EXP-0239',CONCAT('02382-',@anio-1,'-0-1601-JR-CI-02'),'02382',@anio-1,2,1,5,1,8,
   'Ejecucion','PRIMERA',2,1,'BAJO',95000.00,
   DATE_SUB(CURRENT_DATE, INTERVAL 550 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 555 DAY),
   DATE_SUB(NOW(3), INTERVAL 200 DAY),0,'Obligacion de dar suma de dinero. Sentencia ejecutada.'),
( 7,1,'EXP-0452',CONCAT('04521-',@anio,'-0-1601-JR-LA-01'),'04521',@anio,6,3,8,6,4,
   'Postulatoria','PRIMERA',6,1,'MEDIO',145000.00,
   DATE_SUB(CURRENT_DATE, INTERVAL 100 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 103 DAY),
   DATE_SUB(NOW(3), INTERVAL 6 DAY),0,'Despido arbitrario. Reposicion y remuneraciones devengadas.'),
( 8,1,'EXP-0033',CONCAT('00332-',@anio,'-0-1601-JR-PE-01'),'00332',@anio,4,4,10,9,5,
   'Probatoria','PRIMERA',3,1,'CRITICO',0.00,
   DATE_SUB(CURRENT_DATE, INTERVAL 185 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 188 DAY),
   DATE_SUB(NOW(3), INTERVAL 5 DAY),0,'Defensa tecnica en investigacion preparatoria por hurto agravado.'),
( 9,1,'EXP-0088',CONCAT('00885-',@anio,'-0-1601-JR-CO-01'),'00885',@anio,7,5,11,8,4,
   'Postulatoria','PRIMERA',4,1,'ALTO',0.00,
   DATE_SUB(CURRENT_DATE, INTERVAL 85 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 88 DAY),
   DATE_SUB(NOW(3), INTERVAL 2 DAY),1,'Accion de amparo. EXPEDIENTE CONFIDENCIAL.'),
(10,1,'EXP-0091',NULL,NULL,@anio,8,6,12,7,3,
   NULL,'ADMINISTRATIVA',2,1,'BAJO',32000.00,
   DATE_SUB(CURRENT_DATE, INTERVAL 8 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 8 DAY),
   NULL,0,'Evaluacion de impugnacion de multa municipal. Aun sin numero de expediente.');

INSERT INTO expediente_equipo (expediente_id, usuario_id, rol_en_expediente, puede_editar, usuario_asignacion_id) VALUES
(1,1,'RESPONSABLE',1,1),(1,2,'APOYO',1,1),(1,3,'APOYO',1,1),(1,8,'ASISTENTE',1,1),
(2,2,'RESPONSABLE',1,1),(2,4,'APOYO',1,2),(2,8,'ASISTENTE',1,2),
(3,3,'RESPONSABLE',1,1),(3,8,'ASISTENTE',1,3),
(4,5,'RESPONSABLE',1,1),(4,4,'APOYO',1,5),
(5,1,'RESPONSABLE',1,1),
(6,2,'RESPONSABLE',1,1),
(7,6,'RESPONSABLE',1,1),(7,8,'ASISTENTE',1,6),
(8,3,'RESPONSABLE',1,1),(8,1,'SUPERVISOR',1,1),
(9,4,'RESPONSABLE',1,1),(9,1,'SUPERVISOR',1,1),
(10,2,'RESPONSABLE',1,1);

-- El expediente 2 tiene DOS contrapartes: litisconsorcio pasivo
INSERT INTO expediente_contrapartes (expediente_id, contraparte_id, rol_procesal) VALUES
(1,4,'DEMANDADO'), (2,2,'DEMANDADO'), (2,4,'LITISCONSORTE'), (3,4,'DEMANDADO'),
(6,5,'DEMANDADO'), (7,3,'DEMANDADO'), (9,6,'DEMANDADO'), (10,1,'DEMANDADO');

-- =============================================================================
-- ACTUACIONES
-- =============================================================================
INSERT INTO actuaciones (id, expediente_id, tipo_actuacion_id, fecha_hora, asunto, descripcion,
                         usuario_responsable_id, estado, resultado, organo_id, proxima_accion,
                         tiempo_minutos, es_facturable, origen, requiere_verificacion) VALUES
( 1,1, 1, DATE_SUB(NOW(), INTERVAL 210 DAY),'Presentacion de demanda de alimentos',
    'Demanda presentada por mesa de partes electronica.',1,'COMPLETADO','Admitida a tramite',4,
    'Esperar notificacion a la parte demandada',180,1,'MANUAL',0),
( 2,1, 5, DATE_SUB(NOW(), INTERVAL 10 DAY),'Notificacion de resolucion',
    'Resolucion que corre traslado de la demanda y fija plazo de contestacion.',1,'COMPLETADO',
    'Notificacion valida',4,'Calcular plazo y preparar escrito',30,0,'MONITOR_JUDICIAL',1),
( 3,1, 2, DATE_SUB(NOW(), INTERVAL 3 DAY),'Redaccion de escrito de absolucion',
    'Elaboracion del escrito de absolucion de traslado.',1,'EN_TRAMITE',NULL,NULL,
    'Presentar antes del vencimiento',240,1,'MANUAL',0),
( 4,2, 5, DATE_SUB(NOW(), INTERVAL 25 DAY),'Notificacion de auto de saneamiento',
    'Se declara saneado el proceso y se fija plazo probatorio.',2,'COMPLETADO','Notificacion valida',1,
    'Ofrecer medios probatorios',45,0,'MONITOR_JUDICIAL',1),
( 5,2, 2, DATE_SUB(NOW(), INTERVAL 5 DAY),'Escrito de ofrecimiento de medios probatorios',
    'Se ofrecio pericia de danos y declaracion testimonial.',2,'COMPLETADO','Presentado fuera de plazo',1,
    'Solicitar reposicion del plazo',300,1,'MANUAL',0),
( 6,3, 5, DATE_SUB(NOW(), INTERVAL 18 DAY),'Notificacion de sentencia de primera instancia',
    'Sentencia adversa parcialmente. Corresponde apelacion.',3,'COMPLETADO','Notificacion valida',4,
    'Interponer recurso de apelacion',40,0,'MONITOR_JUDICIAL',0),
( 7,3, 4, DATE_SUB(NOW(), INTERVAL 4 DAY),'Recurso de apelacion',
    'Redaccion del recurso de apelacion contra la sentencia.',3,'EN_TRAMITE',NULL,4,
    'Presentar el recurso',200,1,'MANUAL',0),
( 8,4, 5, DATE_SUB(NOW(), INTERVAL 28 DAY),'Notificacion de auto que admite pericia',
    'El juzgado admite la pericia y fija plazo para el pliego de preguntas.',5,'COMPLETADO',
    'Notificacion valida',4,'Elaborar pliego interrogatorio',25,0,'MONITOR_JUDICIAL',0),
( 9,4, 6, DATE_SUB(NOW(), INTERVAL 7 DAY),'Audiencia de fijacion de puntos controvertidos',
    'Se fijaron los puntos controvertidos.',5,'COMPLETADO','Realizada',4,'Preparar pericia',120,1,'MANUAL',0),
(10,7, 5, DATE_SUB(NOW(), INTERVAL 45 DAY),'Notificacion de traslado de contestacion',
    'La demandada contesta la demanda laboral.',6,'COMPLETADO','Notificacion valida',6,
    'Absolver el traslado',35,0,'MONITOR_JUDICIAL',0),
(11,7, 2, DATE_SUB(NOW(), INTERVAL 6 DAY),'Escrito de absolucion de contestacion',
    'Escrito listo, pendiente de firma del socio.',6,'EN_TRAMITE',NULL,6,'Firmar y presentar',260,1,'MANUAL',0),
(12,8, 5, DATE_SUB(NOW(), INTERVAL 5 DAY),'Notificacion de disposicion fiscal',
    'Disposicion que amplia la investigacion. Cedula con fecha observada.',3,'COMPLETADO',
    'Notificacion con observaciones',5,'Verificar fecha real de recepcion',60,0,'MONITOR_JUDICIAL',1),
(13,9, 5, DATE_SUB(NOW(), INTERVAL 2 DAY),'Notificacion de admision del amparo',
    'Se admite la demanda de amparo y se corre traslado a la procuraduria.',4,'COMPLETADO',
    'Notificacion valida',3,'Preparar escrito de apersonamiento',30,0,'MONITOR_JUDICIAL',1),
(14,10,11, DATE_SUB(NOW(), INTERVAL 2 DAY),'Reunion inicial con Transportes Victoria',
    'Evaluacion preliminar de la multa municipal.',2,'COMPLETADO','Se acepta evaluar el caso',NULL,
    'Solicitar copia del expediente administrativo',90,0,'MANUAL',0);

-- =============================================================================
-- RESOLUCIONES — 3 sin ver alimentan "Nuevas resoluciones: 3"
-- =============================================================================
INSERT INTO resoluciones (id, expediente_id, actuacion_id, numero_orden, numero_resolucion, tipo_resolucion,
                          fecha_resolucion, fecha_notificacion, sumilla, fuente, origen,
                          estado_verificacion, usuario_verificacion_id, fecha_verificacion, vista) VALUES
(1,1, 2,21,'Resolucion N.o 0021','AUTO', DATE_SUB(CURRENT_DATE, INTERVAL 14 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 10 DAY),
   'Se tiene por presentada la demanda. Traslado a la parte demandada por el plazo de ley.',
   'Portal CEJ','MONITOR_JUDICIAL','VERIFICADA',1, DATE_SUB(NOW(3), INTERVAL 10 DAY),1),
(2,2, 4,18,'Resolucion N.o 0018','AUTO', DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 25 DAY),
   'Se declara saneado el proceso y se fija plazo para medios probatorios.',
   'Portal CEJ','MONITOR_JUDICIAL','VERIFICADA',2, DATE_SUB(NOW(3), INTERVAL 25 DAY),1),
(3,3, 6, 9,'Resolucion N.o 0009','SENTENCIA', DATE_SUB(CURRENT_DATE, INTERVAL 22 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 18 DAY),
   'Se declara fundada en parte la demanda de alimentos.',
   'Casilla electronica','MONITOR_JUDICIAL','VERIFICADA',3, DATE_SUB(NOW(3), INTERVAL 18 DAY),1),
(4,8,12, 4,'Resolucion N.o 0004','DECRETO', DATE_SUB(CURRENT_DATE, INTERVAL 8 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY),
   'Se amplia el plazo de la investigacion preparatoria por sesenta dias.',
   'Mesa de partes fiscal','MONITOR_JUDICIAL','PENDIENTE',NULL,NULL,0),
(5,9,13, 2,'Resolucion N.o 0002','AUTO', DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 2 DAY),
   'Se admite a tramite la demanda de amparo.',
   'Portal CEJ','MONITOR_JUDICIAL','PENDIENTE',NULL,NULL,0),
(6,7,10,11,'Resolucion N.o 0011','AUTO', DATE_SUB(CURRENT_DATE, INTERVAL 48 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 45 DAY),
   'Se tiene por contestada la demanda y se corre traslado.',
   'Portal CEJ','MONITOR_JUDICIAL','PENDIENTE',NULL,NULL,0);

-- =============================================================================
-- PLAZOS — TODAS LAS FECHAS RELATIVAS A CURRENT_DATE
-- =============================================================================
-- Los plazos 1 a 7 son de computo CALENDARIO, para que el vencimiento caiga en
-- un desplazamiento exacto respecto de hoy y las tarjetas del Dashboard den
-- siempre lo mismo. Los plazos 8 y 9 son de computo HABILES y su vencimiento lo
-- calcula fn_calcular_vencimiento contra calendario_judicial: son los que
-- demuestran que el computo de dias habiles funciona de verdad.
--
-- Objetivo del Dashboard:
--   Plazos vencidos            = 2   (hoy-5, hoy-2)
--   Plazos por vencer (3 dias) = 5   (hoy+0, +1, +2, +3, +3)
-- =============================================================================
INSERT INTO plazos (id, expediente_id, actuacion_origen_id, resolucion_id, tipo_plazo_id, descripcion,
                    fecha_notificacion, fecha_inicio_computo, dias_plazo, tipo_computo,
                    fecha_vencimiento, fecha_vencimiento_interna,
                    usuario_responsable_id, usuario_revisor_id, estado, nivel_riesgo,
                    requiere_revision_profesional, motivo_revision, actuacion_requerida) VALUES
-- 1) VENCIDO hace 5 dias
(1,2,4,2,2,'Ofrecimiento de medios probatorios',
   DATE_SUB(CURRENT_DATE, INTERVAL 25 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 24 DAY), 20,'CALENDARIO',
   DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY),  DATE_SUB(CURRENT_DATE, INTERVAL 7 DAY),
   2,1,'VENCIDO','CRITICO',0,NULL,'Presentar escrito de ofrecimiento de pruebas'),
-- 2) VENCIDO hace 2 dias
(2,3,6,3,3,'Recurso de apelacion contra sentencia',
   DATE_SUB(CURRENT_DATE, INTERVAL 18 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 17 DAY), 16,'CALENDARIO',
   DATE_SUB(CURRENT_DATE, INTERVAL 2 DAY),  DATE_SUB(CURRENT_DATE, INTERVAL 4 DAY),
   3,1,'VENCIDO','ALTO',0,NULL,'Interponer recurso de apelacion'),
-- 3) VENCE HOY
(3,1,2,1,1,'Absolucion de traslado de la demanda',
   DATE_SUB(CURRENT_DATE, INTERVAL 10 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 9 DAY), 10,'CALENDARIO',
   CURRENT_DATE, DATE_SUB(CURRENT_DATE, INTERVAL 2 DAY),
   1,1,'EN_PREPARACION','CRITICO',0,NULL,'Presentar escrito de absolucion'),
-- 4) VENCE MAÑANA
(4,4,8,NULL,2,'Presentacion de pliego interrogatorio para pericia',
   DATE_SUB(CURRENT_DATE, INTERVAL 28 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 27 DAY), 29,'CALENDARIO',
   DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY),
   5,1,'EN_REVISION','ALTO',0,NULL,'Presentar pliego de preguntas'),
-- 5) VENCE EN 2 DIAS — marcado para revision profesional (§8 / HU-09)
(5,8,12,4,5,'Subsanacion de observacion en disposicion fiscal',
   DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 4 DAY), 7,'CALENDARIO',
   DATE_ADD(CURRENT_DATE, INTERVAL 2 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY),
   3,1,'EN_PREPARACION','CRITICO',1,
   'La cedula de notificacion presenta la fecha de recepcion enmendada y no coincide con el cargo. El computo requiere verificacion del abogado antes de darse por firme.',
   'Subsanar la observacion o solicitar nulidad de notificacion'),
-- 6) VENCE EN 3 DIAS
(6,7,10,6,1,'Absolucion de traslado de contestacion',
   DATE_SUB(CURRENT_DATE, INTERVAL 45 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 44 DAY), 48,'CALENDARIO',
   DATE_ADD(CURRENT_DATE, INTERVAL 3 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY),
   6,1,'LISTO_PRESENTAR','MEDIO',0,NULL,'Presentar escrito de absolucion'),
-- 7) VENCE EN 3 DIAS
(7,9,13,5,5,'Apersonamiento en el proceso de amparo',
   DATE_SUB(CURRENT_DATE, INTERVAL 2 DAY), DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), 5,'CALENDARIO',
   DATE_ADD(CURRENT_DATE, INTERVAL 3 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 2 DAY),
   4,1,'CALCULADO','ALTO',0,NULL,'Presentar escrito de apersonamiento');

-- Plazos 8 y 9: computo de DIAS HABILES calculado por la funcion contra el
-- calendario judicial. Aqui es donde se ve que la regla funciona de verdad.
INSERT INTO plazos (id, expediente_id, actuacion_origen_id, resolucion_id, tipo_plazo_id, descripcion,
                    fecha_notificacion, fecha_inicio_computo, dias_plazo, tipo_computo,
                    fecha_vencimiento, fecha_vencimiento_interna,
                    usuario_responsable_id, usuario_revisor_id, estado, nivel_riesgo)
SELECT 8, 2, 5, NULL, 2, 'Actuacion de medios probatorios admitidos',
       DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY),
       fn_siguiente_dia_habil(DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY), NULL),
       30, 'HABILES',
       fn_calcular_vencimiento(fn_siguiente_dia_habil(DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY), NULL), 30, 'HABILES', NULL),
       DATE_SUB(fn_calcular_vencimiento(fn_siguiente_dia_habil(DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY), NULL), 30, 'HABILES', NULL), INTERVAL 2 DAY),
       2, 1, 'CALCULADO', 'MEDIO';

INSERT INTO plazos (id, expediente_id, actuacion_origen_id, resolucion_id, tipo_plazo_id, descripcion,
                    fecha_notificacion, fecha_inicio_computo, dias_plazo, tipo_computo,
                    fecha_vencimiento, fecha_vencimiento_interna,
                    usuario_responsable_id, usuario_revisor_id, estado, nivel_riesgo)
SELECT 9, 3, 7, 3, 2, 'Actuacion probatoria en segunda instancia',
       DATE_SUB(CURRENT_DATE, INTERVAL 3 DAY),
       fn_siguiente_dia_habil(DATE_SUB(CURRENT_DATE, INTERVAL 3 DAY), NULL),
       60, 'HABILES',
       fn_calcular_vencimiento(fn_siguiente_dia_habil(DATE_SUB(CURRENT_DATE, INTERVAL 3 DAY), NULL), 60, 'HABILES', NULL),
       DATE_SUB(fn_calcular_vencimiento(fn_siguiente_dia_habil(DATE_SUB(CURRENT_DATE, INTERVAL 3 DAY), NULL), 60, 'HABILES', NULL), INTERVAL 2 DAY),
       3, 1, 'CALCULADO', 'BAJO';

-- =============================================================================
-- ALERTAS
-- =============================================================================
INSERT INTO alertas (estudio_id, plazo_id, expediente_id, usuario_destino_id, tipo, dias_antes,
                     mensaje, severidad, fecha_programada, canal, estado) VALUES
(1,1,2,2,'PLAZO_VENCIDO', -1,'El plazo probatorio del EXP-0238 vencio hace 5 dias.','CRITICA', DATE_SUB(NOW(3), INTERVAL 4 DAY),'AMBOS','ENVIADA'),
(1,2,3,3,'PLAZO_VENCIDO', -1,'El plazo de apelacion del EXP-0332 vencio hace 2 dias.','CRITICA', DATE_SUB(NOW(3), INTERVAL 1 DAY),'AMBOS','ENVIADA'),
(1,3,1,1,'PLAZO_PROXIMO',  1,'La absolucion de traslado del EXP-0142 vence manana.','CRITICA', DATE_SUB(NOW(3), INTERVAL 1 DAY),'AMBOS','LEIDA'),
(1,3,1,1,'PLAZO_PROXIMO',  0,'La absolucion de traslado del EXP-0142 VENCE HOY.','CRITICA', NOW(3),'AMBOS','ENVIADA'),
(1,4,4,5,'PLAZO_PROXIMO',  1,'El pliego interrogatorio del EXP-0774 vence manana.','ALTA', NOW(3),'SISTEMA','ENVIADA'),
(1,5,8,3,'PLAZO_PROXIMO',  3,'Subsanacion del EXP-0033. REQUIERE REVISION PROFESIONAL.','CRITICA', DATE_SUB(NOW(3), INTERVAL 1 DAY),'AMBOS','ENVIADA'),
(1,6,7,6,'PLAZO_PROXIMO',  3,'La absolucion del EXP-0452 vence en 3 dias.','MEDIA', NOW(3),'SISTEMA','ENVIADA'),
(1,7,9,4,'PLAZO_PROXIMO',  3,'El apersonamiento del amparo EXP-0088 vence en 3 dias.','ALTA', NOW(3),'SISTEMA','PROGRAMADA'),
(1,NULL,2,1,'CUANTIA_ALTA',NULL,'EXP-0238 supera S/ 200,000 de cuantia. Requiere supervision del socio.','ALTA', DATE_SUB(NOW(3), INTERVAL 8 DAY),'SISTEMA','LEIDA'),
-- Alerta del flujo del calendario: hay una suspension esperando aprobacion
(1,NULL,NULL,1,'CALENDARIO_PENDIENTE',NULL,
   'Hay 1 entrada del calendario judicial PROPUESTA esperando su aprobacion. Mientras no se apruebe no afecta el computo de plazos.',
   'ALTA', NOW(3),'AMBOS','ENVIADA');

-- =============================================================================
-- TAREAS — 4 en PENDIENTE alimentan "Tareas pendientes: 4"
-- =============================================================================
INSERT INTO tareas (id, expediente_id, actuacion_origen_id, plazo_id, titulo, descripcion,
                    usuario_responsable_id, usuario_supervisor_id, prioridad, estado, orden_tablero,
                    fecha_limite, tiempo_estimado_minutos, tiempo_real_minutos, porcentaje_avance) VALUES
( 1,1, 2,3,'Redaccion de escrito: Caso Fernandez','Absolucion de traslado de la demanda de alimentos.',
    1,1,'URGENTE','EN_PROCESO',1, DATE_ADD(CURRENT_DATE, INTERVAL 0 DAY) + INTERVAL 17 HOUR,300,240,80),
( 2,8,12,5,'Verificar cedula de notificacion: Caso Torres','Contrastar la fecha de la cedula con el cargo.',
    3,1,'URGENTE','EN_REVISION',1, DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY) + INTERVAL 12 HOUR,90,60,70),
( 3,7,11,6,'Firmar y presentar absolucion: Constructora Andes','Pendiente de firma del socio.',
    6,1,'ALTA','EN_REVISION',2, DATE_ADD(CURRENT_DATE, INTERVAL 3 DAY) + INTERVAL 16 HOUR,60,30,90),
( 4,4, 8,4,'Elaborar pliego interrogatorio para pericia','Preguntas para la pericia en el proceso de tenencia.',
    5,1,'ALTA','APROBADA',1, DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY) + INTERVAL 10 HOUR,180,200,100),
( 5,9,13,7,'Preparar escrito de apersonamiento en amparo','Apersonamiento y senalamiento de casilla.',
    4,1,'ALTA','PENDIENTE',1, DATE_ADD(CURRENT_DATE, INTERVAL 3 DAY) + INTERVAL 12 HOUR,120,NULL,0),
( 6,2, 5,8,'Coordinar pericia de danos con perito de parte','Fijar fecha de inspeccion del vehiculo.',
    2,1,'MEDIA','PENDIENTE',2, DATE_ADD(CURRENT_DATE, INTERVAL 12 DAY) + INTERVAL 17 HOUR,90,NULL,0),
( 7,3, 7,2,'Solicitar reposicion del plazo de apelacion','Evaluar nulidad de notificacion.',
    3,1,'URGENTE','PENDIENTE',3, DATE_ADD(CURRENT_DATE, INTERVAL 2 DAY) + INTERVAL 9 HOUR,150,NULL,0),
( 8,10,14,NULL,'Solicitar copia del expediente administrativo','Requerir copia certificada.',
    2,1,'BAJA','PENDIENTE',4, DATE_ADD(CURRENT_DATE, INTERVAL 6 DAY) + INTERVAL 17 HOUR,60,NULL,0),
( 9,1, 1,NULL,'Recopilar boletas de pago del demandado','Sustento de la capacidad economica.',
    8,1,'MEDIA','COMPLETADA',1, DATE_SUB(CURRENT_DATE, INTERVAL 12 DAY) + INTERVAL 17 HOUR,120,110,100),
(10,2, 5,1,'Presentar escrito de ofrecimiento de pruebas','Presentado fuera de plazo.',
    2,1,'URGENTE','VENCIDA',0, DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY) + INTERVAL 17 HOUR,300,300,100);

INSERT INTO subtareas (tarea_id, titulo, completada, orden, fecha_completado) VALUES
(1,'Revisar la resolucion notificada',1,1, DATE_SUB(NOW(3), INTERVAL 5 DAY)),
(1,'Redactar fundamentos de hecho',1,2, DATE_SUB(NOW(3), INTERVAL 4 DAY)),
(1,'Redactar fundamentos de derecho',1,3, DATE_SUB(NOW(3), INTERVAL 3 DAY)),
(1,'Adjuntar medios probatorios',0,4,NULL),
(1,'Firmar y presentar por mesa de partes',0,5,NULL),
(2,'Solicitar copia del cargo de recepcion',1,1, DATE_SUB(NOW(3), INTERVAL 2 DAY)),
(2,'Contrastar fecha de cedula vs cargo',1,2, DATE_SUB(NOW(3), INTERVAL 1 DAY)),
(2,'Consultar con el socio si corresponde nulidad',0,3,NULL),
(5,'Verificar casilla electronica del estudio',0,1,NULL),
(5,'Redactar el apersonamiento',0,2,NULL),
(7,'Revisar plazo de reposicion aplicable',0,1,NULL),
(9,'Solicitar boletas al empleador',1,1, DATE_SUB(NOW(3), INTERVAL 20 DAY)),
(9,'Anexar al expediente',1,2, DATE_SUB(NOW(3), INTERVAL 13 DAY));

-- =============================================================================
-- AGENDA — 1 sola AUDIENCIA en los proximos 7 dias
-- =============================================================================
INSERT INTO eventos_agenda (id, estudio_id, expediente_id, actuacion_id, plazo_id, tipo, titulo,
                            descripcion, fecha_inicio, fecha_fin, modalidad, ubicacion, enlace_virtual,
                            estado, recordatorio_minutos, color_hex) VALUES
(1,1,1,NULL,NULL,'AUDIENCIA','Audiencia unica: Caso Fernandez','Audiencia unica del proceso de alimentos.',
   DATE_ADD(CURRENT_DATE, INTERVAL 4 DAY) + INTERVAL 9 HOUR, DATE_ADD(CURRENT_DATE, INTERVAL 4 DAY) + INTERVAL 11 HOUR,
   'PRESENCIAL','Sala 302, 3er Juzgado de Familia de Trujillo',NULL,'CONFIRMADO',1440,'#7C3AED'),
(2,1,1,NULL,NULL,'REUNION_CLIENTE','Reunion con Ana Maria Fernandez','Repaso del escrito antes de presentarlo.',
   CURRENT_DATE + INTERVAL 11 HOUR, CURRENT_DATE + INTERVAL 12 HOUR,
   'VIRTUAL',NULL,'https://meet.google.com/lexia-fernandez','CONFIRMADO',30,'#F59E0B'),
(3,1,1,NULL,3,'VENCIMIENTO_PLAZO','Vence: absolucion de traslado EXP-0142',NULL,
   CURRENT_DATE + INTERVAL 15 HOUR, CURRENT_DATE + INTERVAL 15 HOUR + INTERVAL 30 MINUTE,
   'VIRTUAL',NULL,NULL,'PROGRAMADO',120,'#DC2626'),
(4,1,7,NULL,NULL,'PRESENTACION','Presentar absolucion: Constructora Andes',NULL,
   DATE_ADD(CURRENT_DATE, INTERVAL 3 DAY) + INTERVAL 10 HOUR, DATE_ADD(CURRENT_DATE, INTERVAL 3 DAY) + INTERVAL 11 HOUR,
   'PRESENCIAL','Mesa de partes, Juzgado de Trabajo',NULL,'PROGRAMADO',60,'#0EA5E9'),
(5,1,8,NULL,NULL,'DILIGENCIA','Declaracion en sede fiscal: Caso Torres','Declaracion del investigado.',
   DATE_ADD(CURRENT_DATE, INTERVAL 9 DAY) + INTERVAL 8 HOUR, DATE_ADD(CURRENT_DATE, INTERVAL 9 DAY) + INTERVAL 10 HOUR,
   'PRESENCIAL','1ra Fiscalia Provincial Penal de Trujillo',NULL,'PROGRAMADO',1440,'#8B5CF6'),
(6,1,2,NULL,NULL,'AUDIENCIA','Audiencia de pruebas: Caso Lopez','Actuacion de medios probatorios.',
   DATE_ADD(CURRENT_DATE, INTERVAL 16 DAY) + INTERVAL 10 HOUR, DATE_ADD(CURRENT_DATE, INTERVAL 16 DAY) + INTERVAL 13 HOUR,
   'PRESENCIAL','Sala 110, 2do Juzgado Civil de Trujillo',NULL,'PROGRAMADO',1440,'#7C3AED'),
(7,1,4,9,NULL,'AUDIENCIA','Audiencia de puntos controvertidos','Audiencia ya realizada.',
   DATE_SUB(CURRENT_DATE, INTERVAL 7 DAY) + INTERVAL 10 HOUR, DATE_SUB(CURRENT_DATE, INTERVAL 7 DAY) + INTERVAL 12 HOUR,
   'PRESENCIAL','Sala 302, 3er Juzgado de Familia de Trujillo',NULL,'REALIZADO',NULL,'#7C3AED'),
(8,1,NULL,NULL,NULL,'INTERNO','Reunion semanal del equipo','Revision de plazos criticos de la semana.',
   CURRENT_DATE + INTERVAL 8 HOUR, CURRENT_DATE + INTERVAL 8 HOUR + INTERVAL 45 MINUTE,
   'PRESENCIAL','Sala de reuniones, piso 3',NULL,'CONFIRMADO',15,'#6B7280');

INSERT INTO evento_participantes (evento_id, usuario_id, tipo_participacion, confirmado) VALUES
(1,1,'ORGANIZADOR',1),(1,8,'ASISTENTE',1),
(2,1,'ORGANIZADOR',1),(2,2,'OPCIONAL',0),
(4,6,'ORGANIZADOR',1),
(5,3,'ORGANIZADOR',1),(5,1,'ASISTENTE',0),
(6,2,'ORGANIZADOR',1),(6,4,'ASISTENTE',1),
(7,5,'ORGANIZADOR',1),(7,4,'ASISTENTE',1),
(8,1,'ORGANIZADOR',1),(8,2,'ASISTENTE',1),(8,3,'ASISTENTE',1),(8,5,'ASISTENTE',1),(8,6,'ASISTENTE',1);

-- =============================================================================
-- DOCUMENTOS
-- =============================================================================
INSERT INTO documentos (id, expediente_id, actuacion_id, tarea_id, nombre, descripcion, categoria, estado, es_confidencial) VALUES
(1,1, 1,NULL,'Demanda de alimentos - Fernandez','Escrito de demanda presentado','DEMANDA','PRESENTADO',0),
(2,1, 2,NULL,'Resolucion N.o 0021','Auto que corre traslado de la demanda','RESOLUCION','PRESENTADO',0),
(3,1, 3,   1,'Absolucion de traslado - Fernandez','Escrito en elaboracion','ESCRITO','EN_REVISION',0),
(4,2, 4,NULL,'Resolucion N.o 0018','Auto de saneamiento procesal','RESOLUCION','PRESENTADO',0),
(5,2, 5,NULL,'Ofrecimiento de medios probatorios','Presentado fuera de plazo','ESCRITO','PRESENTADO',0),
(6,3, 6,NULL,'Sentencia de primera instancia','Sentencia parcialmente fundada','RESOLUCION','PRESENTADO',0),
(7,3, 7,NULL,'Recurso de apelacion - Morales','Recurso en elaboracion','RECURSO','BORRADOR',0),
(8,7,11,   3,'Absolucion de contestacion - Andes','Listo para firma del socio','ESCRITO','APROBADO',0),
(9,9,13,NULL,'Resolucion N.o 0002 - Amparo','Auto que admite a tramite','RESOLUCION','PRESENTADO',1);

INSERT INTO documento_versiones (id, documento_id, numero_version, nombre_archivo, ruta_almacenamiento,
                                 extension, mime_type, tamano_bytes, hash_sha256, usuario_carga_id, observaciones) VALUES
( 1,1,1,'demanda_alimentos_v1.pdf','/almacen/exp-0142/demanda_alimentos_v1.pdf','pdf','application/pdf',245760,
    'a3f5c1d8e29b47f60c1a5e83d47b9021fe4c6a8b3d90527e1f4a6c8b0d3e5f79',1,'Version presentada'),
( 2,2,1,'resolucion_0021.pdf','/almacen/exp-0142/resolucion_0021.pdf','pdf','application/pdf',118234,
    'b7e2a94c1f38d06b5a2e7c40f19d83b6ce5a207d4f81b93e6a0c2d5f487b1e30',8,'Descargada del portal CEJ'),
( 3,3,1,'absolucion_v1.docx','/almacen/exp-0142/absolucion_v1.docx',
    'docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',87432,
    'c1d4f80a25e97b36c8a1f5d20b74e9c36f0a8d5b2e714c96a3f0d8b5e2c714a9',1,'Borrador inicial'),
( 4,3,2,'absolucion_v2.docx','/almacen/exp-0142/absolucion_v2.docx',
    'docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',94120,
    'd8b3e15c47a06f92d1c5a8e30b64f7d91a2c806e5f3b471a9d0c6e2f84b15a37',1,'Incorpora observaciones del socio'),
( 5,4,1,'resolucion_0018.pdf','/almacen/exp-0238/resolucion_0018.pdf','pdf','application/pdf',132900,
    'e5a1c73f92b48d06e2f7a5c81d3b60947fa2c5e8b1d04739f6a8c2e5b0d17f43',8,'Descargada del portal CEJ'),
( 6,5,1,'ofrecimiento_pruebas.pdf','/almacen/exp-0238/ofrecimiento_pruebas.pdf','pdf','application/pdf',201455,
    'f2c8b60d15a37e94c0d8b3f5a1e62c704b9d3f81a5c07e2b6d4a9f30c8e15b27',2,'Presentado fuera de plazo'),
( 7,6,1,'sentencia_primera.pdf','/almacen/exp-0332/sentencia_primera.pdf','pdf','application/pdf',356780,
    '0a7d3f92c548b16e0f3a8c25d71b649e3f0a5c8b2d46e17390c5a8f2b6d04e13',3,'Descargada de casilla electronica'),
( 8,7,1,'apelacion_v1.docx','/almacen/exp-0332/apelacion_v1.docx',
    'docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',76540,
    '1b8e4a05d629c73f1a4b9d36e82c750f4a1b6d9c3e57f28401d6b9e3f7c25a08',3,'Borrador'),
( 9,8,1,'absolucion_andes_v1.docx','/almacen/exp-0452/absolucion_andes_v1.docx',
    'docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',102340,
    '2c9f5b16e73a084d2b5c0e47f93d861a5b2c7e0d4f68a39512e7c0a4b8d36f19',6,'Aprobado por el socio'),
(10,9,1,'resolucion_0002_amparo.pdf','/almacen/exp-0088/resolucion_0002_amparo.pdf','pdf','application/pdf',145670,
    '3d0a6c27f84b195e3c6d1f58a04e972b6c3d8f1e5a79b4a623f8d1b5c9e47a20',4,'Documento confidencial');

UPDATE documentos SET version_vigente_id = 1  WHERE id = 1;
UPDATE documentos SET version_vigente_id = 2  WHERE id = 2;
UPDATE documentos SET version_vigente_id = 4  WHERE id = 3;   -- la v2 es la vigente
UPDATE documentos SET version_vigente_id = 5  WHERE id = 4;
UPDATE documentos SET version_vigente_id = 6  WHERE id = 5;
UPDATE documentos SET version_vigente_id = 7  WHERE id = 6;
UPDATE documentos SET version_vigente_id = 8  WHERE id = 7;
UPDATE documentos SET version_vigente_id = 9  WHERE id = 8;
UPDATE documentos SET version_vigente_id = 10 WHERE id = 9;

-- Cierre de la cadena de oro
UPDATE plazos       SET documento_resultante_id = 5 WHERE id = 1;
UPDATE plazos       SET documento_resultante_id = 8 WHERE id = 6;
UPDATE resoluciones SET documento_id = 2 WHERE id = 1;
UPDATE resoluciones SET documento_id = 4 WHERE id = 2;
UPDATE resoluciones SET documento_id = 6 WHERE id = 3;
UPDATE resoluciones SET documento_id = 9 WHERE id = 5;

INSERT INTO documento_etiquetas (documento_id, etiqueta_id, usuario_asignacion_id) VALUES
(3,1,1),(3,2,1),(7,1,3),(8,5,6),(9,6,4),(1,5,1),(5,3,2);

-- =============================================================================
-- COMENTARIOS Y NOTIFICACIONES
-- =============================================================================
INSERT INTO comentarios (expediente_id, tarea_id, documento_id, usuario_id, texto) VALUES
(1,NULL,NULL,1,'La resolucion fija plazo de 10 dias. Vence hoy, hay que presentar.'),
(NULL,1,NULL,1,'Revise el punto 4.2 sobre la capacidad economica. Falta sustentar con las boletas.'),
(NULL,2,NULL,1,'Si la fecha de la cedula no coincide con el cargo, corresponde solicitar la nulidad. No des el plazo por firme.'),
(2,NULL,NULL,2,'El escrito de pruebas se presento fuera de plazo. Evaluando reposicion.'),
(2,NULL,NULL,1,'Documenta el motivo del retraso. Va al informe de incidencias del estudio.'),
(3,NULL,NULL,3,'El plazo de apelacion vencio. Revisando si la notificacion tuvo defecto.'),
(NULL,NULL,3,1,'La version 2 ya incorpora las observaciones. Falta adjuntar los medios probatorios.');

INSERT INTO notificaciones (usuario_id, tipo, titulo, mensaje, severidad, entidad_origen, registro_id, expediente_id, url_destino, leida) VALUES
(1,'PLAZO','Plazo vence hoy','La absolucion de traslado del EXP-0142 vence hoy.','CRITICA','plazos',3,1,'/plazos/3',0),
(2,'PLAZO','Plazo vencido','El plazo probatorio del EXP-0238 esta vencido.','CRITICA','plazos',1,2,'/plazos/1',0),
(3,'PLAZO','Plazo vencido','El plazo de apelacion del EXP-0332 esta vencido.','CRITICA','plazos',2,3,'/plazos/2',0),
(3,'RESOLUCION','Nueva resolucion','Disposicion fiscal pendiente de verificacion en EXP-0033.','ADVERTENCIA','resoluciones',4,8,'/resoluciones/4',0),
(4,'RESOLUCION','Nueva resolucion','Se admitio a tramite el amparo EXP-0088.','INFO','resoluciones',5,9,'/resoluciones/5',0),
(6,'RESOLUCION','Nueva resolucion','Traslado de la contestacion en EXP-0452.','INFO','resoluciones',6,7,'/resoluciones/6',0),
(1,'CALENDARIO','Calendario pendiente de aprobacion','Rosa Jimenez registro una suspension de plazos que espera su aprobacion.','ADVERTENCIA','calendario_judicial',NULL,NULL,'/configuracion/calendario',0),
(5,'TAREA','Tarea aprobada','El pliego interrogatorio del EXP-0774 fue aprobado.','EXITO','tareas',4,4,'/tareas/4',1);

-- =============================================================================
-- AUDITORIA — muestra del formato
-- =============================================================================
INSERT INTO auditoria (estudio_id, usuario_id, entidad, registro_id, expediente_id, accion,
                       valores_anteriores, valores_nuevos, descripcion, ip_origen, exito) VALUES
(1,1,'usuarios',1,NULL,'LOGIN',NULL,NULL,'Inicio de sesion exitoso','190.116.44.12',1),
(1,1,'expedientes',1,1,'CREAR',NULL,
   JSON_OBJECT('codigo_interno','EXP-0142','cliente_id',1,'estado_id',4),
   'Apertura del expediente de alimentos','190.116.44.12',1),
(1,1,'plazos',3,1,'CREAR',NULL,
   JSON_OBJECT('dias_plazo',10,'tipo_computo','CALENDARIO'),
   'Registro del plazo de absolucion de traslado','190.116.44.12',1),
(1,2,'plazos',1,2,'ACTUALIZAR',
   JSON_OBJECT('estado','EN_PREPARACION'), JSON_OBJECT('estado','VENCIDO'),
   'El sistema marca el plazo como vencido',NULL,1),
(1,8,'calendario_judicial',
   (SELECT id FROM calendario_judicial WHERE tipo = 'SUSPENSION_PLAZOS' ORDER BY id LIMIT 1),
   NULL,'CREAR',NULL,
   JSON_OBJECT('tipo','SUSPENSION_PLAZOS','estado','PROPUESTO'),
   'Suspension de plazos registrada, pendiente de aprobacion del Socio','190.116.44.44',1),
(1,4,'documentos',10,9,'DESCARGAR',NULL,NULL,
   'Descarga de documento confidencial del expediente de amparo','190.116.44.71',1),
-- HU-02: los intentos denegados tambien se registran
(1,10,'expedientes',9,9,'ACCESO_DENEGADO',NULL,NULL,
   'La Recepcionista intento abrir un expediente confidencial sin asignacion','190.116.44.90',0);
