-- =============================================================================
-- LEXIA — Semilla de DESARROLLO: Biblioteca Juridica y Monitor Judicial
-- =============================================================================
-- Dos tablas que V10 y V17 crean pero que NINGUNA semilla rellenaba:
--
--   recursos_juridicos   -> la pestaña de Biblioteca Juridica salia vacia
--   monitor_expedientes  -> el Monitor Judicial salia vacio
--   monitor_consultas    -> el historial de consultas salia vacio
--
-- R__seed_desarrollo.sql llena 34 tablas y estas tres no estan entre ellas, asi
-- que el modulo funcionaba pero no tenia nada que pintar. Aqui van los datos.
--
-- POR QUE LLEVA EL PREFIJO `zz_`  (esto no es capricho, lee esto si renombras)
-- -----------------------------------------------------------------------------
-- Las migraciones repetibles de Flyway se ejecutan ordenadas por su descripcion,
-- que es el nombre completo despues de `R__`. Sin ese prefijo, este archivo se
-- llamaría `R__seed_biblioteca_monitor.sql` y ordenaria ANTES que
-- `R__seed_desarrollo.sql` (b < d), ejecutandose primero.
--
-- Eso romperia el script: la semilla principal TRUNCATEa `expedientes`,
-- `usuarios` y `estudios`, y aqui insertamos filas con FOREIGN KEY contra esas
-- tres. Salir antes que ella dejaria las nuestras huerfanas en una base nueva.
--
-- Con `zz_` la descripcion `seed_zz_biblioteca_monitor` ordena despues de
-- `seed_desarrollo` (d < z) y las tablas padre ya existen pobladas. Si cambias
-- este nombre, comprueba ese orden antes de subir.
--
-- IDEMPOTENTE
-- -----------------------------------------------------------------------------
-- Como toda repetible, se puede ejecutar muchas veces: vacia sus tres tablas
-- antes de escribir, asi que una segunda corrida deja exactamente el mismo
-- estado. Solo corre en desarrollo: el perfil `prod` no carga `classpath:db/seed`
-- en `flyway.locations` (ver application-dev.yml L28).
--
-- QUE TOMA DEL RESTO DE LA BASE (y por que no se inventa IDs)
-- -----------------------------------------------------------------------------
--   estudio_id      = 1     R__seed_desarrollo.sql inserta un unico estudio
--   expediente_id   = 1..9  ids explicitos de la semilla principal
--   usuario_id      = 1..8  ids explicitos de la semilla principal
--   resolucion_id   = 1..6  ids explicitos de la semilla principal
--
-- Usamos los mismos IDs que ella y no subconsultas, porque es el mismo patron
-- que ya usa esa semilla para todo lo demas. Si algun dia la semilla principal
-- dejara de usar IDs explicitos, hay que migrar estas tres tablas a subconsultas
-- en el mismo commit.
--
-- ADVERTENCIA: al aplicarse borra la fila de prueba que exista en las tres
-- tablas (una de Biblioteca subida a mano y un Monitor ya desactivado).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Limpieza idempotente — hijo antes que padre
-- -----------------------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE monitor_consultas;
TRUNCATE TABLE monitor_expedientes;
TRUNCATE TABLE recursos_juridicos;
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- BIBLIOTECA JURIDICA — 15 recursos de consulta
-- =============================================================================
-- Rellena las cuatro tarjetas de `GET /api/v1/biblioteca/estadisticas`:
--   recursosTotales  = 15 filas
--   recursosActivos  = 14      (una ARCHIVADO: cuenta en total, no en activos)
--   conArchivo       = 10      (exige nombre_archivo <> '', si no da 0 y el
--                               filtro "con archivo" no encuentra nada)
--   totalCategorias  = 9       (COUNT(DISTINCT categoria) IGNORA los NULL,
--                               por eso todas las filas llevan categoria)
--
-- Las 10 filas con nombre_archivo apuntan a PDFs reales bajo
-- storage/biblioteca/BIBLIOTECA/recurso-<id>/v001_<hash>.pdf; sin ese archivo
-- el recurso se lista pero "Ver PDF" da 404. `tamanio_bytes` va con el tamano
-- real de ese archivo, porque el frontend lo pinta en el panel de detalle.
-- Las 5 restantes son solo metadata, para que el filtro "Con archivo" tenga con
-- que probar las dos ramas.
--
-- El PDF NO lo genera esta semilla y NO esta en el repo: la regla 7 de
-- CLAUDE.md prohibe binarios en git, y .gitignore cubre `storage/` por eso.
-- Son archivos de disco, igual que los documentos que suben los usuarios; en un
-- clone nuevo esas 10 filas existen pero "Ver PDF" da 404 hasta que se vuelvan
-- a generar. OJO con la carpeta: la ruta se resuelve contra el directorio de
-- trabajo del JVM, y en este equipo se arranca desde DOS sitios distintos — la
-- raiz del repo (VS Code) y BackendLexia/ (la tarea programada lexia-back), por
-- eso estan en las dos. Si un dia el arranque se unifica, borra la que sobre.
--
-- tipo y categoria van en CODIGO, no en el texto visible: los CHECK de V10
-- rechazan 'Jurisprudencia' o 'Derecho Civil'. Las etiquetas las arma el
-- frontend. Ver V10__biblioteca_juridica.sql L64-72.
INSERT INTO recursos_juridicos
    (id, estudio_id, titulo, subtitulo_descripcion, tipo, categoria, anio,
     expediente_codigo, nombre_archivo, ruta_archivo, extension, tamanio_bytes,
     estado, fecha_creacion, usuario_creacion_id, activo) VALUES

-- --- JURISPRUDENCIA ---------------------------------------------------------
( 1,1,'Caso Fernandez c/ Santisteban sobre alimentos y pension',
    'Casacion N. 4521-2024, Sala Civil Permanente. Fija el criterio sobre la '
    'acreditacion de necesidad del alimentista cuando el alimentante declara '
    'ingresos menores a los reales.',
    'JURISPRUDENCIA','DERECHO_CIVIL',2024,'EXP-0142',
    'casacion-4521-2024-alimentos.pdf',
    'biblioteca-v1/BIBLIOTECA/recurso-1/v001_a3f91c0e77b2d541.pdf','pdf',1248,
    'ACTIVO',DATE_SUB(NOW(3), INTERVAL 45 DAY),1,1),

( 2,1,'Jurisprudencia sobre plazo de prueba en procesos de conocimiento',
    'Compilacion de resoluciones del primer semestre que declaran improcedente '
    'la reposicion por ofrecimiento extemporaneo de medios probatorios.',
    'JURISPRUDENCIA','PROCESAL',2025,'EXP-0332',
    NULL,NULL,NULL,NULL,
    'ACTIVO',DATE_SUB(NOW(3), INTERVAL 32 DAY),3,1),

-- --- LEGISLACION ------------------------------------------------------------
( 3,1,'TUO del D. Leg. 728 - Ley Procesal del Trabajo',
    'Texto unificado vigente, con las modificaciones de la L. 31539 que rigen '
    'desde abril de 2023.',
    'LEGISLACION','DERECHO_LABORAL',2023,NULL,
    'legislacion-tuo-ds-728-lpt.pdf',
    'biblioteca-v1/BIBLIOTECA/recurso-3/v001_6b20e4d9c81a7f33.pdf','pdf',1121,
    'ACTIVO',DATE_SUB(NOW(3), INTERVAL 90 DAY),6,1),

( 4,1,'Codigo Procesal Civil - Libro II: medios probatorios',
    'Extracto anotado de los articulos 233 a 301, con la casacion que fija el '
    'criterio de admisibilidad de la prueba de oficio.',
    'LEGISLACION','DERECHO_CIVIL',2022,NULL,
    'codigo-procesal-civil-libro-ii.pdf',
    'biblioteca-v1/BIBLIOTECA/recurso-4/v001_c71d9e2a4f8b0615.pdf','pdf',1090,
    'ACTIVO',DATE_SUB(NOW(3), INTERVAL 120 DAY),1,1),

(15,1,'Ley N. 27469 - Ley de Migraciones y su reglamento',
    'Norma base y D.S. 001-2018-MTC con los plazos de permanencia y los '
    'supuestos de residencia temporal por razones humanitarias.',
    'LEGISLACION','DERECHO_MIGRATORIO',2026,NULL,
    NULL,NULL,NULL,NULL,
    'ACTIVO',DATE_SUB(NOW(3), INTERVAL 18 DAY),5,1),

-- --- DOCTRINA ---------------------------------------------------------------
( 5,1,'La tutela efectiva del demandado en el proceso contencioso',
    'Capitulo 3 del tratado de Derecho Procesal Constitucional sobre el '
    'debido proceso como garantia del contradicción.',
    'DOCTRINA','DERECHO_CONSTITUCIONAL',2021,NULL,
    'tutela-efectiva-demandado.pdf',
    'biblioteca-v1/BIBLIOTECA/recurso-5/v001_f04b8c6d2397ea10.pdf','pdf',1136,
    'ACTIVO',DATE_SUB(NOW(3), INTERVAL 200 DAY),8,1),

( 6,1,'Teoria general de la responsabilidad civil extracontractual',
    'Material de formacion interna: el nexo causal y la culpa en los procesos '
    'de indemnizacion por danos.',
    'DOCTRINA','GENERAL',2020,NULL,
    NULL,NULL,NULL,NULL,
    'ACTIVO',DATE_SUB(NOW(3), INTERVAL 240 DAY),5,1),

-- --- CONTRATOS --------------------------------------------------------------
( 7,1,'Contrato de compraventa de inmueble con reserva de dominio',
    'Modelo aprobado por el estudio, con la clausula de plazo penal alineada a '
    'la jurisprudencia de la Sala Civil.',
    'CONTRATO','DERECHO_CIVIL',2026,'EXP-0238',
    'contrato-compraventa-reserva-dominio.pdf',
    'biblioteca-v1/BIBLIOTECA/recurso-7/v001_18ae67c309f54b2e.pdf','pdf',1135,
    'ACTIVO',DATE_SUB(NOW(3), INTERVAL 12 DAY),1,1),

( 8,1,'Contrato de trabajo a plazo fijo - clausulas 2026',
    'Incluye la modalidad de teletrabajo y la retribucion de capacitacion '
    'obligatoria conforme al D. Leg. 1567.',
    'CONTRATO','DERECHO_LABORAL',2026,'EXP-0452',
    'contrato-trabajo-plazo-fijo-2026.pdf',
    'biblioteca-v1/BIBLIOTECA/recurso-8/v001_9d5072bc4a18e6f7.pdf','pdf',1119,
    'ACTIVO',DATE_SUB(NOW(3), INTERVAL 8 DAY),6,1),

-- --- MODELOS ---------------------------------------------------------------
( 9,1,'Modelo de escrito de apelacion contra sentencia de primera instancia',
    'Estructura completa con la sustentacion de agravio y el pedimento '
    'expreso de nulidad, ya probada en tres casos del estudio.',
    'MODELO','PROCESAL',2025,'EXP-0332',
    'modelo-escrito-apelacion.pdf',
    'biblioteca-v1/BIBLIOTECA/recurso-9/v001_4e17aa83f6c29d0b.pdf','pdf',1117,
    'ACTIVO',DATE_SUB(NOW(3), INTERVAL 28 DAY),3,1),

(10,1,'Modelo de contestacion de demanda por injurias',
    'Redactado para procesos penales por difamacion y calumnia, con la '
    'excepcion de verdad prevista en el art. 190 del C. Penal.',
    'MODELO','DERECHO_PENAL',2025,'EXP-0033',
    NULL,NULL,NULL,NULL,
    'ACTIVO',DATE_SUB(NOW(3), INTERVAL 26 DAY),3,1),

-- --- PLANTILLAS ------------------------------------------------------------
(11,1,'Plante de recurso de apelacion en via administrativa',
    'Para impugnacion de resoluciones que declaran improcedente un recurso de '
    'reconsideracion ante entidades del Estado.',
    'PLANTILLA','DERECHO_ADMINISTRATIVO',2024,'EXP-0091',
    'plantilla-recurso-apelacion-administrativa.pdf',
    'biblioteca-v1/BIBLIOTECA/recurso-11/v001_55c8e0317ad94f62.pdf','pdf',1091,
    'ACTIVO',DATE_SUB(NOW(3), INTERVAL 60 DAY),2,1),

(12,1,'Plantilla de demanda de desalojo por ocupacion sin titulo',
    'Incluye el informe de colindancias y el pedido de posesion material que '
    'exige el juzgado civil de Trujillo.',
    'PLANTILLA','GENERAL',2024,NULL,
    NULL,NULL,NULL,NULL,
    'ACTIVO',DATE_SUB(NOW(3), INTERVAL 55 DAY),2,1),

-- --- FORMATO ----------------------------------------------------------------
(13,1,'Formato unico de calculo de intereses legales',
    'Planilla con la tasa efectiva anual y la capitalizacion mensual usada '
    'para sustentar la liquidacion en ejecucion de sentencia.',
    'FORMATO','DERECHO_TRIBUTARIO',2026,NULL,
    'formato-calculo-intereses-legales.pdf',
    'biblioteca-v1/BIBLIOTECA/recurso-13/v001_2b6fd794ca30e158.pdf','pdf',1079,
    'ACTIVO',DATE_SUB(NOW(3), INTERVAL 6 DAY),1,1),

-- --- LIBRO (archivado: comprueba que totalCategorias y recursosActivos no coincidan)
(14,1,'Derecho Procesal Civil - manual de consulta anterior',
    'Edicion de referencia del estudio. ARCHIVADO desde que se adopto el '
    'codigo vigente; se conserva por las sentencias comentadas al anexo.',
    'LIBRO','GENERAL',2019,NULL,
    'manual-derecho-procesal-civil-2019.pdf',
    'biblioteca-v1/BIBLIOTECA/recurso-14/v001_7a4e18db60f2c935.pdf','pdf',1047,
    'ARCHIVADO',DATE_SUB(NOW(3), INTERVAL 400 DAY),1,1);

-- =============================================================================
-- MONITOR JUDICIAL — 8 expedientes monitoreados
-- =============================================================================
-- `estado` NO se filtra en ninguna consulta del repositorio: lo unico que saca
-- una fila es `activo = 0` o un expediente `activo = 0`. Aqui van las tres
-- variantes del CHECK para que la pantalla cubra los tres casos de la BD
-- ('ACTIVO' | 'PAUSADO' | 'CERRADO'), pero las tres siguen siendo visibles.
--
-- El orden del listado es COALESCE(ultima_consulta, fecha_creacion) DESC, asi
-- que esta columna define lo que se ve arriba. Verificada contra
-- MonitorJudicialRepositoryJdbc.java L103 y L158.
--
-- Unicidad (estudio_id, expediente_id) en V17 L21: un solo monitor por
-- expediente. No se puede repetir expediente_id aqui.
INSERT INTO monitor_expedientes
    (id, estudio_id, expediente_id, fuente_plataforma, estado, ultima_consulta,
     fecha_creacion, usuario_creacion_id, activo) VALUES
(1,1,1,'CEJ - Poder Judicial','ACTIVO',NOW(3),
    DATE_SUB(NOW(3), INTERVAL 210 DAY),1,1),
(2,1,2,'CEJ - Poder Judicial','ACTIVO',NOW(3),
    DATE_SUB(NOW(3), INTERVAL 440 DAY),2,1),
(3,1,3,'CEJ - Poder Judicial','ACTIVO',DATE_SUB(NOW(3), INTERVAL 2 DAY),
    DATE_SUB(NOW(3), INTERVAL 660 DAY),3,1),
(4,1,4,'Sistema de Gestion Judicial - PJ','ACTIVO',DATE_SUB(NOW(3), INTERVAL 1 DAY),
    DATE_SUB(NOW(3), INTERVAL 140 DAY),5,1),
(5,1,5,'CEJ - Poder Judicial','CERRADO',DATE_SUB(NOW(3), INTERVAL 145 DAY),
    DATE_SUB(NOW(3), INTERVAL 240 DAY),1,1),
(6,1,7,'Sistema de Gestion Judicial - PJ','ACTIVO',DATE_SUB(NOW(3), INTERVAL 2 DAY),
    DATE_SUB(NOW(3), INTERVAL 100 DAY),6,1),
(7,1,8,'CEJ - Poder Judicial','PAUSADO',DATE_SUB(NOW(3), INTERVAL 3 DAY),
    DATE_SUB(NOW(3), INTERVAL 185 DAY),3,1),
(8,1,9,'Mesa de Partes Virtual','ACTIVO',NOW(3),
    DATE_SUB(NOW(3), INTERVAL 85 DAY),4,1);

-- =============================================================================
-- MONITOR JUDICIAL — 25 consultas registradas
-- =============================================================================
-- Esto es lo que alimenta las tarjetas de `GET .../resumen` y el modulo
-- "Nuevas resoluciones" del Dashboard:
--
--   expedientes_monitoreados = 8   (no depende de fechas)
--   movimientos_hoy          >= 2  (hubo_movimiento=1 Y fecha_consulta HOY)
--   resoluciones_hoy         >= 2  (nueva_resolucion=1 Y fecha_consulta HOY)
--   pendientes               = 6   (estado_verificacion='PENDIENTE', sin fecha)
--
-- Las dos tarjetas de "hoy" usan CURRENT_DATE, por eso tres consultas llevan
-- NOW(3) y el resto DATE_SUB: con fechas fijas de 2024 esas tarjetas se quedarian
-- en 0 para siempre, el mismo error de la semilla anterior que comenta
-- R__seed_desarrollo.sql L16-20.
--
-- Las dos consultas de origen 'IA' van a 'PENDIENTE' a proposito: el servicio
-- obliga PENDIENTE cuando origenInformacion = 'IA' (MonitorJudicialService L99)
-- y no conviene sembrar un estado que la app no podria haber producido.
--
-- resolucion_id solo se rellena donde el expediente tiene fila en `resoluciones`
-- (la semilla principal crea las 6, ids 1..6). Es NULL en el resto: la columna
-- es nullable en V17 L54 y el frontend pinta el numero desde la propia consulta.
--
-- numero_resolucion, tipo_resolucion, fecha_resolucion y fecha_notificacion son
-- los de esas seis filas reales, copiados a mano. Con `DATE_SUB` fijo la fecha
-- se desincroniza la semana siguiente: hoy concuerdan, en marzo no. Por eso van
-- como subconsulta a `resoluciones` en vez de un INTERVAL — la semilla reutiliza
-- lo que ya esta en la base y nunca se despega.
--
-- El numero SI va literal, no en subconsulta: es el dato que la consulta
-- muestra en la pantalla y tiene que existir aunque un dia se borren las
-- resoluciones de la semilla principal.
INSERT INTO monitor_consultas
    (id, monitor_id, expediente_id, fecha_consulta, usuario_id,
     fuente_plataforma, hubo_movimiento, tipo_movimiento, nueva_resolucion,
     numero_resolucion, tipo_resolucion, fecha_resolucion, fecha_notificacion,
     resolucion_id, documento_descargado, notificacion, plazo, observaciones,
     origen_informacion, estado_verificacion, usuario_verificacion_id,
     fecha_verificacion) VALUES

-- ---- monitor 1 -> expediente 1 (alimentos) -------------------------------
( 1,1,1,DATE_SUB(NOW(3), INTERVAL 5 DAY),1,'CEJ - Poder Judicial',1,'NOTIFICACION',0,
    NULL,NULL,NULL,NULL,NULL,
    'notify_0142_0506.pdf',
    'Notificacion de la resolucion que corre traslado de la demanda.',
    'Contestar dentro de 5 dias utiles.',
    NULL,'DESCARGADA','VERIFICADA',1,DATE_SUB(NOW(3), INTERVAL 5 DAY)),

( 2,1,1,DATE_SUB(NOW(3), INTERVAL 4 DAY),1,'CEJ - Poder Judicial',1,'RESOLUCION',1,
    'Resolucion N.o 0021','AUTO',
    (SELECT fecha_resolucion FROM resoluciones WHERE id=1),
    (SELECT fecha_notificacion FROM resoluciones WHERE id=1),1,
    NULL,NULL,'Calcular plazo y preparar escrito',
    'Se tiene por presentada la demanda.',
    'MANUAL','VERIFICADA',1,DATE_SUB(NOW(3), INTERVAL 4 DAY)),

-- HOY: movimiento + nueva resolucion, alimenta movimientos_hoy y resoluciones_hoy
( 3,1,1,NOW(3),1,'CEJ - Poder Judicial',1,'AUTO',0,
    NULL,NULL,NULL,NULL,NULL,
    'auto_saneamiento_0142.pdf',
    'Auto que declara saneado el proceso y fija el inicio del periodo probatorio.',
    'Ofrecer medios probatorios antes del vencimiento.',
    'Consulta automatica de hoy.','IA','PENDIENTE',NULL,NULL),

( 4,1,1,DATE_SUB(NOW(3), INTERVAL 9 DAY),2,'CEJ - Poder Judicial',0,'OTRO',0,
    NULL,NULL,NULL,NULL,NULL,
    NULL,NULL,NULL,
    'Sin novedad en el portal.',
    'MANUAL','VERIFICADA',1,DATE_SUB(NOW(3), INTERVAL 9 DAY)),

-- ---- monitor 2 -> expediente 2 (indemnizacion) ---------------------------
( 5,2,2,DATE_SUB(NOW(3), INTERVAL 6 DAY),2,'CEJ - Poder Judicial',1,'TRAMITE',0,
    NULL,NULL,NULL,NULL,NULL,
    NULL,NULL,'Ofrecer medios probatorios dentro del plazo de ley.',
    NULL,'CORREO','VERIFICADA',2,DATE_SUB(NOW(3), INTERVAL 6 DAY)),

( 6,2,2,DATE_SUB(NOW(3), INTERVAL 3 DAY),2,'CEJ - Poder Judicial',1,'AUTO',1,
    'Resolucion N.o 0018','AUTO',
    (SELECT fecha_resolucion FROM resoluciones WHERE id=2),
    (SELECT fecha_notificacion FROM resoluciones WHERE id=2),2,
    'auto_0018_saneamiento.pdf',
    'Se declara saneado el proceso.',
    'Solicitar pericia de danos.',
    NULL,'DESCARGADA','VERIFICADA',2,DATE_SUB(NOW(3), INTERVAL 3 DAY)),

-- HOY: la misma resolucion vuelta a notificar, estado pendiente de verificar
( 7,2,2,NOW(3),3,'CEJ - Poder Judicial',1,'NOTIFICACION',1,
    'Resolucion N.o 0018','AUTO',
    (SELECT fecha_resolucion FROM resoluciones WHERE id=2),
    (SELECT fecha_notificacion FROM resoluciones WHERE id=2),2,
    'notificacion_0018.pdf',
    'Notificacion con fecha de recepcion observada.',
    'Verificar la fecha real de recepcion antes de calcular el plazo.',
    'La notificacion llego con un dia de retraso respecto a la fecha del sistema.',
    'MANUAL','PENDIENTE',NULL,NULL),

( 8,2,2,DATE_SUB(NOW(3), INTERVAL 12 DAY),3,'CEJ - Poder Judicial',0,'OTRO',0,
    NULL,NULL,NULL,NULL,NULL,
    NULL,NULL,NULL,'Sin novedad.','MANUAL','VERIFICADA',2,DATE_SUB(NOW(3), INTERVAL 12 DAY)),

-- ---- monitor 3 -> expediente 3 (apelacion de sentencia) ------------------
( 9,3,3,DATE_SUB(NOW(3), INTERVAL 10 DAY),3,'CEJ - Poder Judicial',1,'SENTENCIA',1,
    'Resolucion N.o 0009','SENTENCIA',
    (SELECT fecha_resolucion FROM resoluciones WHERE id=3),
    (SELECT fecha_notificacion FROM resoluciones WHERE id=3),3,
    'sentencia_0009.pdf',
    'Sentencia adversa parcialmente. Cabe recurso de apelacion.',
    'Interponer apelacion dentro de 5 dias.',
    NULL,'DESCARGADA','VERIFICADA',3,DATE_SUB(NOW(3), INTERVAL 10 DAY)),

(10,3,3,DATE_SUB(NOW(3), INTERVAL 4 DAY),3,'CEJ - Poder Judicial',1,'AUDIENCIA',0,
    NULL,NULL,NULL,NULL,NULL,
    'acta_audiencia_pericial.pdf',NULL,'Elaborar el pliego de preguntas.',
    'Se fijaron los puntos controvertidos.',
    'MANUAL','VERIFICADA',3,DATE_SUB(NOW(3), INTERVAL 4 DAY)),

(11,3,3,DATE_SUB(NOW(3), INTERVAL 2 DAY),8,'CEJ - Poder Judicial',0,'OTRO',0,
    NULL,NULL,NULL,NULL,NULL,
    NULL,NULL,NULL,
    'El cliente pregunto por el estado; sin movimiento en el portal.',
    'CLIENTE','PENDIENTE',NULL,NULL),

-- ---- monitor 4 -> expediente 4 (tenencia) --------------------------------
(12,4,4,DATE_SUB(NOW(3), INTERVAL 12 DAY),5,'Sistema de Gestion Judicial - PJ',1,'PROVIDENCIA',0,
    NULL,NULL,NULL,NULL,NULL,
    'providencia_pericial.pdf',NULL,
    'Presentar el pliego antes de la audiencia.',
    NULL,'CORREO','VERIFICADA',5,DATE_SUB(NOW(3), INTERVAL 12 DAY)),

(13,4,4,DATE_SUB(NOW(3), INTERVAL 7 DAY),5,'Sistema de Gestion Judicial - PJ',1,'AUDIENCIA',0,
    NULL,NULL,NULL,NULL,NULL,
    NULL,NULL,NULL,
    'La pericia no se realizo por inasistencia del perito. Se reprograma.',
    'MANUAL','RECHAZADA',5,DATE_SUB(NOW(3), INTERVAL 7 DAY)),

(14,4,4,DATE_SUB(NOW(3), INTERVAL 1 DAY),4,'Sistema de Gestion Judicial - PJ',1,'ESCRITO',0,
    NULL,NULL,NULL,NULL,NULL,
    'escrito_ofrecimiento_pericial.pdf',NULL,
    'El escrito quedo pendiente de firma del socio.',
    'Escrito listo, pendiente de firma.',
    'MANUAL','VERIFICADA',5,DATE_SUB(NOW(3), INTERVAL 1 DAY)),

-- ---- monitor 5 -> expediente 5 (conciliado, monitor CERRADO) -------------
(15,5,5,DATE_SUB(NOW(3), INTERVAL 150 DAY),1,'CEJ - Poder Judicial',1,'AUTO',0,
    NULL,NULL,NULL,NULL,NULL,
    'auto_archivo_exp0052.pdf',NULL,'Cerrar el monitor.',
    'Carpeta archivada tras el acuerdo conciliatorio.',
    'DESCARGADA','VERIFICADA',1,DATE_SUB(NOW(3), INTERVAL 150 DAY)),

(16,5,5,DATE_SUB(NOW(3), INTERVAL 145 DAY),1,'CEJ - Poder Judicial',0,'OTRO',0,
    NULL,NULL,NULL,NULL,NULL,
    NULL,NULL,NULL,'Ultima consulta antes de cerrar.',
    'MANUAL','VERIFICADA',1,DATE_SUB(NOW(3), INTERVAL 145 DAY)),

-- ---- monitor 6 -> expediente 7 (despido arbitrario) ----------------------
(17,6,7,DATE_SUB(NOW(3), INTERVAL 45 DAY),6,'Sistema de Gestion Judicial - PJ',1,'NOTIFICACION',0,
    NULL,NULL,NULL,NULL,NULL,
    'notificacion_contestacion.pdf',
    'La demandada contesta la demanda laboral.',
    'Absolver el traslado.',NULL,'CORREO','VERIFICADA',6,DATE_SUB(NOW(3), INTERVAL 45 DAY)),

(18,6,7,DATE_SUB(NOW(3), INTERVAL 6 DAY),6,'Sistema de Gestion Judicial - PJ',1,'RESOLUCION',1,
    'Resolucion N.o 0011','AUTO',
    (SELECT fecha_resolucion FROM resoluciones WHERE id=6),
    (SELECT fecha_notificacion FROM resoluciones WHERE id=6),6,
    'resolucion_0011.pdf',
    'Se admite la pericia contable sobre las remuneraciones devengadas.',
    'Designar perito contable.',NULL,'DESCARGADA','VERIFICADA',6,DATE_SUB(NOW(3), INTERVAL 6 DAY)),

(19,6,7,DATE_SUB(NOW(3), INTERVAL 2 DAY),6,'Sistema de Gestion Judicial - PJ',1,'TRAMITE',0,
    NULL,NULL,NULL,NULL,NULL,
    NULL,NULL,'Presentar escrito antes del vencimiento.',
    'Consulta automatizada; sin verificacion humana todavia.',
    'IA','PENDIENTE',NULL,NULL),

-- ---- monitor 7 -> expediente 8 (hurto agravado, monitor PAUSADO) ---------
(20,7,8,DATE_SUB(NOW(3), INTERVAL 5 DAY),3,'CEJ - Poder Judicial',1,'RESOLUCION',1,
    'Resolucion N.o 0004','DECRETO',
    (SELECT fecha_resolucion FROM resoluciones WHERE id=4),
    (SELECT fecha_notificacion FROM resoluciones WHERE id=4),4,
    'decreto_0004.pdf',
    'Decreto que amplia la investigacion preparatoria.',
    'Verificar la fecha real de recepcion.',
    NULL,'DESCARGADA','VERIFICADA',3,DATE_SUB(NOW(3), INTERVAL 5 DAY)),

(21,7,8,DATE_SUB(NOW(3), INTERVAL 3 DAY),3,'CEJ - Poder Judicial',1,'NOTIFICACION',0,
    NULL,NULL,NULL,NULL,NULL,
    NULL,NULL,'Cedula con fecha observada.',
    'La cedula de notificacion trae una fecha distinta a la del sistema.',
    'MANUAL','PENDIENTE',NULL,NULL),

(22,7,8,DATE_SUB(NOW(3), INTERVAL 185 DAY),3,'CEJ - Poder Judicial',1,'TRAMITE',0,
    NULL,NULL,NULL,NULL,NULL,
    'disposicion_fiscal.pdf',NULL,NULL,NULL,'CORREO','VERIFICADA',3,DATE_SUB(NOW(3), INTERVAL 185 DAY)),

-- ---- monitor 8 -> expediente 9 (amparo, confidencial) --------------------
(23,8,9,DATE_SUB(NOW(3), INTERVAL 88 DAY),4,'Mesa de Partes Virtual',0,'OTRO',0,
    NULL,NULL,NULL,NULL,NULL,
    NULL,NULL,NULL,'Sin movimiento en la carpeta.',
    'MANUAL','VERIFICADA',4,DATE_SUB(NOW(3), INTERVAL 88 DAY)),

(24,8,9,DATE_SUB(NOW(3), INTERVAL 2 DAY),4,'Mesa de Partes Virtual',1,'NOTIFICACION',0,
    NULL,NULL,NULL,NULL,NULL,
    'notificacion_admision_amparo.pdf',
    'Se admite la demanda de amparo y se corre traslado a la Procuraduria.',
    'Preparar el escrito de apersonamiento.',NULL,'MANUAL','VERIFICADA',4,DATE_SUB(NOW(3), INTERVAL 2 DAY)),

-- HOY: segunda fila de movimiento y de resolucion del dia
(25,8,9,NOW(3),4,'Mesa de Partes Virtual',1,'PROVIDENCIA',1,
    'Resolucion N.o 0002','AUTO',
    (SELECT fecha_resolucion FROM resoluciones WHERE id=5),
    (SELECT fecha_notificacion FROM resoluciones WHERE id=5),5,
    'providencia_0002.pdf',
    'Providencia que fija fecha de audiencia de juzgamiento.',
    'Citaciones a la audiencia.',NULL,'MANUAL','PENDIENTE',NULL,NULL);

-- =============================================================================
-- VERIFICACION
-- =============================================================================
-- Todo esto tiene que dar distinto de cero. Ejecutarlo tras arrancar el backend:
--
--   SELECT (SELECT COUNT(*) FROM recursos_juridicos)                    AS biblioteca,
--          (SELECT COUNT(*) FROM recursos_juridicos
--            WHERE estado = 'ACTIVO')                                   AS recursosActivos,
--          (SELECT COUNT(*) FROM monitor_expedientes WHERE activo = 1)  AS monitores,
--          (SELECT COUNT(*) FROM monitor_consultas)                     AS consultas,
--          (SELECT COUNT(*) FROM recursos_juridicos
--            WHERE nombre_archivo IS NOT NULL AND nombre_archivo <> '')  AS conArchivo,
--          (SELECT COUNT(DISTINCT categoria) FROM recursos_juridicos)   AS totalCategorias,
--          (SELECT COUNT(*) FROM monitor_consultas
--            WHERE estado_verificacion = 'PENDIENTE')                   AS pendientes,
--          (SELECT COUNT(*) FROM monitor_consultas
--            WHERE hubo_movimiento = 1
--              AND fecha_consulta >= CURRENT_DATE
--              AND fecha_consulta < DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY)) AS movimientos_hoy,
--          (SELECT COUNT(*) FROM monitor_consultas
--            WHERE nueva_resolucion = 1
--              AND fecha_consulta >= CURRENT_DATE
--              AND fecha_consulta < DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY)) AS resoluciones_hoy;
--
-- Esperado: biblioteca 15, recursosActivos 14, monitores 8, consultas 25,
--           conArchivo 10, totalCategorias 9, pendientes 6,
--           movimientos_hoy 3, resoluciones_hoy 2.
--
-- recursosActivos se filtra por `estado`, NO por `activo`: los dos tienen que
-- dar distinto o la fila ARCHIVADO no esta haciendo su trabajo. Es lo que hace
-- el repositorio en RecursoJuridicoRepository L95.
--
-- Verificado con el backend levantado: /biblioteca/estadisticas devuelve
-- {"recursosTotales":15,"recursosActivos":14,"conArchivo":10,"totalCategorias":9}
-- y /monitor-judicial/resumen devuelve
-- {"expedientesMonitoreados":8,"movimientosHoy":3,"nuevasResolucionesHoy":2,"pendientesVerificacion":6}.
SELECT
    (SELECT COUNT(*) FROM recursos_juridicos)                       AS biblioteca,
    (SELECT COUNT(*) FROM recursos_juridicos
      WHERE estado = 'ACTIVO')                                      AS recursosActivos,
    (SELECT COUNT(*) FROM monitor_expedientes WHERE activo = 1)     AS monitores,
    (SELECT COUNT(*) FROM monitor_consultas)                        AS consultas;
