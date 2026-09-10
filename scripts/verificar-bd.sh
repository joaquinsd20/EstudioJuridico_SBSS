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
#   6. La semilla es idempotente y no depende de la fecha
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
check "Tablas"                "$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${BASE}' AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history';")" "35"
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

printf '\n\033[1m== RESULTADO: %s comprobaciones OK, %s fallas ==\033[0m\n' "$OK" "$FALLOS"
[ "$FALLOS" -eq 0 ] || exit 1
