#!/usr/bin/env bash
# =============================================================================
# LEXIA — Verificacion de la base de datos
# =============================================================================
# Uso:   bash scripts/verificar-bd.sh [usuario] [base]
# Ej.:   bash scripts/verificar-bd.sh root lexia_db
#
# Comprueba lo que un script de base de datos DEBE poder demostrar:
#   1. El esquema esta completo
#   2. El motor y el charset son los correctos
#   3. Las restricciones se aplican de verdad (pruebas negativas)
#   4. El computo de plazos habiles funciona
#   5. El flujo de aprobacion del calendario funciona
#   6. El dashboard no depende de la fecha de hoy
#   7. El indice de plazos se usa de verdad
#   8. Biblioteca Juridica y Monitor Judicial tienen sus datos
#
# El paso 6 y el 8 comparten una idea: una comprobacion que pasa hoy y falla la
# semana siguiente no esta comprobando nada. Los contadores "de hoy" del paso 8
# se miden sobre el ultimo dia con consultas, no sobre CURRENT_DATE.
# =============================================================================
set -uo pipefail

USUARIO="${1:-root}"
BASE="${2:-lexia_db}"

read -rsp "Contraseña de MySQL para ${USUARIO}: " MYSQL_PWD
echo
export MYSQL_PWD

MYSQL="mysql -u ${USUARIO} ${BASE}"
OK=0; FALLOS=0

titulo() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }
check()  { if [ "$2" = "$3" ]; then printf '  [OK]    %-46s %s\n' "$1" "$2"; OK=$((OK+1));
           else printf '  [FALLA] %-46s obtuvo:%s esperado:%s\n' "$1" "$2" "$3"; FALLOS=$((FALLOS+1)); fi; }
q()      { mysql -u "${USUARIO}" -N -B "${BASE}" -e "$1" 2>/dev/null; }
rechaza(){ if mysql -u "${USUARIO}" "${BASE}" -e "$2" >/dev/null 2>&1;
           then printf '  [FALLA] %-46s la base ACEPTO datos invalidos\n' "$1"; FALLOS=$((FALLOS+1));
           else printf '  [OK]    %-46s rechazado\n' "$1"; OK=$((OK+1)); fi; }

titulo "1. Version del motor"
V=$(q "SELECT VERSION();")
echo "  MySQL ${V}"
MIN=$(q "SELECT IF(VERSION() >= '8.0.16', 'SI', 'NO');")
check "Version >= 8.0.16 (los CHECK se aplican)" "$MIN" "SI"

titulo "2. Inventario del esquema"
check "Tablas"                "$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${BASE}' AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history';")" "46"
check "Roles del sistema (V12)"      "$(q "SELECT COUNT(*) FROM roles;")" "8"
# 31 de V12 + 2 de V17 (MONITOR_*) + 4 de V20 (FACTURACION_*) + 2 de V23 (MENSAJERIA_*)
check "Permisos del sistema"         "$(q "SELECT COUNT(*) FROM permisos;")" "39"
# 123 de V12 + 10 de V17 (2 permisos x 5 roles) + 12 de V20 (4 x 3 roles)
# + 14 de V23 (2 permisos x 7 roles). Medido en una base nueva, no calculado: la
# primera cuenta a mano dio 153 por leer solo cuatro de los siete roles de V23.
check "Pares rol-permiso"            "$(q "SELECT COUNT(*) FROM roles_permisos;")" "159"
# V19 se llamaba V19_facturacion.sql, con un solo guion bajo: Flyway la saltaba
# con una linea en INFO y estas dos tablas no existian en ninguna base.
# V22 agrego tarea_historial y la etapa del flujo de aprobacion.
check "Etapa de flujo en tareas (V22)"  "$(q "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${BASE}' AND table_name='tareas' AND column_name='etapa_flujo';")" "1"
check "Tablas de facturacion (V19)"  "$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${BASE}' AND table_name IN ('honorarios','movimientos_financieros');")" "2"
# Nadie operativo ni el portal llega al dinero (V20).
check "Roles sin acceso a facturacion" "$(q "SELECT COUNT(*) FROM roles_permisos rp JOIN roles r ON r.id=rp.rol_id JOIN permisos p ON p.id=rp.permiso_id WHERE p.modulo='FACTURACION' AND r.codigo IN ('ABOGADO','ABOGADO_AUXILIAR','ASISTENTE_LEGAL','RECEPCIONISTA','CLIENTE');")" "0"
# V23: el chat interno. V24: los seis indices que #52 habia metido dentro de V16 y V18.
check "Tablas de mensajeria (V23)"   "$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${BASE}' AND table_name IN ('conversaciones','mensajes');")" "2"
check "Indices de agenda y plazos (V24)" "$(q "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='${BASE}' AND index_name IN ('ix_eventos_rango','ix_eventos_tipo_estado_fecha','ix_participantes_usuario_evento','ix_plazos_notificacion','ix_plazos_tipo_estado_venc','ix_plazos_riesgo_estado_venc');")" "6"
check "Funciones de plazos"   "$(q "SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema='${BASE}';")" "4"
check "Motor InnoDB en todas" "$(q "SELECT COUNT(DISTINCT engine) FROM information_schema.tables WHERE table_schema='${BASE}' AND table_type='BASE TABLE';")" "1"
check "Una sola collation"    "$(q "SELECT COUNT(DISTINCT table_collation) FROM information_schema.tables WHERE table_schema='${BASE}' AND table_type='BASE TABLE';")" "1"
echo "  Collation: $(q "SELECT DISTINCT table_collation FROM information_schema.tables WHERE table_schema='${BASE}' AND table_type='BASE TABLE';")"
echo "  FK: $(q "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='${BASE}' AND constraint_type='FOREIGN KEY';")"
echo "  CHECK: $(q "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='${BASE}' AND constraint_type='CHECK';")"

titulo "3. Pruebas negativas — la base debe RECHAZAR esto"
rechaza "DNI de 7 digitos" \
  "INSERT INTO clientes (estudio_id,tipo_persona,tipo_documento,numero_documento,nombres,apellido_paterno,estado) VALUES (1,'NATURAL','DNI','1234567','X','Y','ACTIVO');"
rechaza "Persona juridica sin razon social" \
  "INSERT INTO clientes (estudio_id,tipo_persona,tipo_documento,numero_documento,estado) VALUES (1,'JURIDICA','RUC','20999888777','ACTIVO');"
rechaza "Actuacion sin expediente (regla de oro)" \
  "INSERT INTO actuaciones (expediente_id,tipo_actuacion_id,fecha_hora,asunto,usuario_responsable_id) VALUES (NULL,1,NOW(),'X',1);"
rechaza "Expediente sin responsable (regla de oro)" \
  "INSERT INTO expedientes (estudio_id,codigo_interno,anio,cliente_id,especialidad_id,estado_id,abogado_responsable_id,fecha_inicio) VALUES (1,'EXP-TEST',2026,1,1,4,NULL,CURRENT_DATE);"
rechaza "Estado de plazo inexistente" \
  "INSERT INTO plazos (expediente_id,tipo_plazo_id,descripcion,fecha_notificacion,fecha_inicio_computo,dias_plazo,tipo_computo,fecha_vencimiento,usuario_responsable_id,estado) VALUES (1,1,'X',CURRENT_DATE,CURRENT_DATE,5,'HABILES',CURRENT_DATE,1,'INVENTADO');"
rechaza "Plazo en revision profesional sin motivo" \
  "INSERT INTO plazos (expediente_id,tipo_plazo_id,descripcion,fecha_notificacion,fecha_inicio_computo,dias_plazo,tipo_computo,fecha_vencimiento,usuario_responsable_id,requiere_revision_profesional) VALUES (1,1,'X',CURRENT_DATE,CURRENT_DATE,5,'HABILES',CURRENT_DATE,1,1);"
rechaza "Dos veces la misma version de un documento" \
  "INSERT INTO documento_versiones (documento_id,numero_version,nombre_archivo,ruta_almacenamiento,extension,tamano_bytes,hash_sha256,usuario_carga_id) VALUES (1,1,'d.pdf','/x','pdf',100,REPEAT('a',64),1);"
rechaza "Codigo de rol en minusculas" \
  "INSERT INTO roles (codigo,nombre,nivel_acceso) VALUES ('abogado','X',50);"
rechaza "Entrada de calendario RECHAZADO sin motivo" \
  "INSERT INTO calendario_judicial (fecha_inicio,fecha_fin,tipo,descripcion,estado,usuario_registro_id) VALUES (CURRENT_DATE,CURRENT_DATE,'FERIADO_NACIONAL','X','RECHAZADO',1);"
rechaza "Evento que termina antes de empezar" \
  "INSERT INTO eventos_agenda (estudio_id,tipo,titulo,fecha_inicio,fecha_fin) VALUES (1,'AUDIENCIA','X',NOW(),DATE_SUB(NOW(),INTERVAL 1 DAY));"

titulo "4. Computo de plazos"
check "Plazos con vencimiento correcto" \
  "$(q "SELECT COUNT(*) FROM plazos WHERE fecha_vencimiento = fn_calcular_vencimiento(fecha_inicio_computo, dias_plazo, tipo_computo, NULL);")" \
  "$(q "SELECT COUNT(*) FROM plazos;")"
echo "  Sabado: $(q "SELECT fn_es_dia_habil(DATE_ADD(CURRENT_DATE, INTERVAL (7 - DAYOFWEEK(CURRENT_DATE)) DAY), NULL);") (debe ser 0)"

titulo "5. Flujo de aprobacion del calendario judicial"
check "Entradas APROBADO afectan el computo" \
  "$(q "SELECT fn_es_dia_habil((SELECT fecha_inicio FROM calendario_judicial WHERE estado='APROBADO' AND tipo='FERIADO_NACIONAL' LIMIT 1), NULL);")" "0"
check "Entradas PROPUESTO NO afectan el computo" \
  "$(q "SELECT IFNULL((SELECT fn_es_dia_habil(fecha_inicio,'La Libertad') FROM calendario_judicial WHERE estado='PROPUESTO' LIMIT 1),1);")" "1"

titulo "6. Dashboard — no debe depender de la fecha de hoy"
echo "  Plazos vencidos:      $(q "SELECT COUNT(*) FROM plazos WHERE activo=1 AND fecha_vencimiento < CURRENT_DATE AND estado NOT IN ('CUMPLIDO','PRESENTADO','CANCELADO');") (esperado 2)"
echo "  Plazos por vencer:    $(q "SELECT COUNT(*) FROM plazos WHERE activo=1 AND fecha_vencimiento BETWEEN CURRENT_DATE AND DATE_ADD(CURRENT_DATE, INTERVAL 3 DAY) AND estado NOT IN ('CUMPLIDO','PRESENTADO','CANCELADO');") (esperado 5)"
echo "  Nuevas resoluciones:  $(q "SELECT COUNT(*) FROM resoluciones WHERE activo=1 AND vista=0;") (esperado 3)"
echo "  Audiencias proximas:  $(q "SELECT COUNT(*) FROM eventos_agenda WHERE activo=1 AND tipo='AUDIENCIA' AND fecha_inicio BETWEEN CURRENT_DATE AND DATE_ADD(CURRENT_DATE, INTERVAL 7 DAY) AND estado NOT IN ('CANCELADO','REALIZADO');") (esperado 1)"
echo "  Tareas pendientes:    $(q "SELECT COUNT(*) FROM tareas WHERE activo=1 AND estado='PENDIENTE';") (esperado 4)"
echo "  -> Volver a ejecutar este script dentro de una semana debe dar lo mismo."

titulo "7. El indice caliente se usa"
PLAN=$(q "EXPLAIN SELECT id FROM plazos WHERE fecha_vencimiento < CURRENT_DATE AND estado <> 'CUMPLIDO';" | awk -F'\t' '{print $5" via "$7}')
echo "  Acceso: ${PLAN}   (el tipo NO debe ser 'ALL')"

titulo "8. Biblioteca Juridica y Monitor Judicial"
# Datos de R__seed_zz_biblioteca_monitor.sql: las tres tablas que V10 y V17
# crean vacias y que ninguna otra semilla rellenaba.
#
# OJO con los dos ultimos: los marcadores "movimientos de hoy" y "resoluciones de
# hoy" del Dashboard se calculan contra CURRENT_DATE, pero la semilla escribe
# esas tres filas con NOW(3). Contado contra la fecha de HOY, este script pasaria
# el dia de la carga y fallaria a la semana. Por eso se cuentan sobre el ultimo
# dia con consultas, que es el dia de la ultima corrida de la semilla: dan 3 y 2
# hoy, y siguen dando 3 y 2 dentro de un mes. Es el mismo criterio del paso 6.
# Sin punto y coma: la variable se interpola DENTRO de un subquery, y un
# `(SELECT ...;)` es error de sintaxis en MySQL.
ULTIMO="SELECT MAX(DATE(fecha_consulta)) FROM monitor_consultas"

check "Recursos en la biblioteca" \
  "$(q "SELECT COUNT(*) FROM recursos_juridicos;")" "15"
# `estado` y `activo` son dos columnas distintas a proposito: el ARCHIVADO sigue
# activo. recursosActivos del endpoint cuenta por `estado`, no por `activo`
# (RecursoJuridicoRepository L95), y comprobar la de `activo` daria 15 y ocultaria
# que el ARCHIVADO dejo de hacer su trabajo.
check "Recursos con estado ACTIVO" \
  "$(q "SELECT COUNT(*) FROM recursos_juridicos WHERE estado='ACTIVO';")" "14"
check "Exactamente un ARCHIVADO" \
  "$(q "SELECT COUNT(*) FROM recursos_juridicos WHERE estado='ARCHIVADO';")" "1"
check "Recursos con archivo" \
  "$(q "SELECT COUNT(*) FROM recursos_juridicos WHERE nombre_archivo IS NOT NULL AND nombre_archivo <> '';")" "10"
check "Categorias distintas" \
  "$(q "SELECT COUNT(DISTINCT categoria) FROM recursos_juridicos;")" "9"
check "Expedientes monitoreados" \
  "$(q "SELECT COUNT(*) FROM monitor_expedientes WHERE activo = 1;")" "8"
# Los tres estados del CHECK, uno por fila: sin eso el desplegable de estados
# del Monitor se prueba con un solo valor.
check "Monitores ACTIVO / PAUSADO / CERRADO" \
  "$(q "SELECT COUNT(DISTINCT estado) FROM monitor_expedientes;")" "3"
check "Consultas registradas" \
  "$(q "SELECT COUNT(*) FROM monitor_consultas;")" "25"
check "Consultas pendientes de verificar" \
  "$(q "SELECT COUNT(*) FROM monitor_consultas WHERE estado_verificacion = 'PENDIENTE';")" "6"
check "Movimientos del ultimo dia de la semilla" \
  "$(q "SELECT COUNT(*) FROM monitor_consultas WHERE hubo_movimiento = 1 AND DATE(fecha_consulta) = (${ULTIMO});")" "3"
check "Resoluciones del ultimo dia de la semilla" \
  "$(q "SELECT COUNT(*) FROM monitor_consultas WHERE nueva_resolucion = 1 AND DATE(fecha_consulta) = (${ULTIMO});")" "2"
# Cada consulta con resolucion_id apunta a la de SU expediente. Si alguien
# reordena los IDs de la semilla principal esto es lo primero que se rompe.
check "Consultas enlazadas a su resolucion" \
  "$(q "SELECT COUNT(*) FROM monitor_consultas c JOIN resoluciones r ON r.id = c.resolucion_id AND r.expediente_id = c.expediente_id WHERE c.resolucion_id IS NOT NULL;")" \
  "$(q "SELECT COUNT(*) FROM monitor_consultas WHERE resolucion_id IS NOT NULL;")"
echo "  Los 10 con nombre_archivo viven en storage/biblioteca/BIBLIOTECA/ y NO"
echo "  estan en git: en un clone nuevo se listan pero 'Ver PDF' da 404."

printf '\n\033[1m== RESULTADO: %s comprobaciones OK, %s fallas ==\033[0m\n' "$OK" "$FALLOS"
[ "$FALLOS" -eq 0 ] || exit 1
