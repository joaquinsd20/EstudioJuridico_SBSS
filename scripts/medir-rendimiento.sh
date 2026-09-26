#!/usr/bin/env bash
# =============================================================================
# LEXIA — Medir el rendimiento de los listados con volumen realista.
# =============================================================================
# La semilla de desarrollo tiene 10 expedientes. Con 10 filas todo es rapido y
# no se ve nada: los problemas de este tipo aparecen a los miles. Este script
# clona la base, la infla, mide los endpoints reales por HTTP y borra la copia.
#
#   bash scripts/medir-rendimiento.sh
#
# NO toca lexia_db ni ninguna base de trabajo: crea lexia_perf desde cero con
# las migraciones actuales, la infla, mide y la borra.
# Necesita DB_PASSWORD exportada (en este repo: `lexia-secretos`).
#
# Lo que se aprendio la primera vez que se corrio, para no repetir el camino:
#
#   - El coste dominante era el TAMANO de la respuesta, no el plan de la
#     consulta. GET /api/v1/plazos devolvia 16.884 KB en una peticion.
#   - El segundo era calcular un contador por fila ANTES del LIMIT: las cuatro
#     subconsultas del listado de actuaciones corrian con loops=50014 para
#     devolver 250 filas.
#   - Y lo que NO importaba, medido: el EXISTS de visibilidad (acelera, no
#     frena), la profundidad del OFFSET, el DATE() sobre la columna de fecha, un
#     indice nuevo en fecha_hora (MySQL no lo usa) y denormalizar estudio_id a
#     las tablas hijas (con un solo estudio no tiene selectividad).
#
# Si vas a optimizar algo, corre esto antes y despues. Sin numeros, "optimizar"
# es adivinar.
# =============================================================================
set -uo pipefail

USUARIO="${DB_USERNAME:-root}"
COPIA=lexia_perf
PUERTO=8099
EXPEDIENTES="${1:-5000}"

if [ -z "${DB_PASSWORD:-}" ]; then
    echo "Falta DB_PASSWORD. En este repo: lexia-secretos" >&2
    exit 1
fi
export MYSQL_PWD="$DB_PASSWORD"
M="mysql -u ${USUARIO}"
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

titulo() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }

# -----------------------------------------------------------------------------
titulo "1. Creando ${COPIA} desde cero con las migraciones actuales"
# Desde cero y no clonando una base de trabajo: esa puede estar varias
# migraciones atras, y entonces el backend fallaria contra columnas que su
# codigo ya usa. Aqui Flyway aplica V1..Vn y el seed, y asi la medicion
# siempre corre contra el esquema del codigo que tenemos delante.
$M -e "DROP DATABASE IF EXISTS ${COPIA};
       CREATE DATABASE ${COPIA} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" 2>/dev/null
RAIZ_TMP="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ_TMP/BackendLexia" || exit 1
DB_NAME="$COPIA" DB_PASSWORD="$DB_PASSWORD"     ./mvnw -q spring-boot:run -Dspring-boot.run.arguments="--server.port=${PUERTO}"     > "$TMP/migrar.log" 2>&1 &
MIG=$!
for _ in $(seq 1 180); do
    grep -q "Started BackendLexiaApplication" "$TMP/migrar.log" 2>/dev/null && break
    sleep 1
done
kill $MIG 2>/dev/null; sleep 3; pkill -f "spring-boot:run" 2>/dev/null
if ! grep -q "Successfully applied" "$TMP/migrar.log" 2>/dev/null; then
    echo "  Flyway no aplico nada:"; grep -iE "ERROR|Caused by|convention" "$TMP/migrar.log" | head -8
    exit 1
fi
grep -oP "Successfully applied \d+ migrations.*?now at version \S+" "$TMP/migrar.log" | sed 's/^/  /'
# Si alguna migracion quedo fuera por el nombre, hay que saberlo AQUI y no
# descubrirlo cuando falte una tabla.
if grep -q "did not follow the filename convention" "$TMP/migrar.log"; then
    echo "  AVISO: Flyway ignoro alguna migracion por su nombre. Ver db/migration/LEEME.md"
fi
echo "  tablas: $($M -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${COPIA}' AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history';" 2>/dev/null) (el mismo numero que espera verificar-bd.sh)"

# -----------------------------------------------------------------------------
titulo "2. Inflando a ${EXPEDIENTES} expedientes"
# Las columnas clonables se sacan del esquema, no de una lista escrita a mano:
# asi el script no se rompe cuando una migracion agrega una columna.
columnas() { # tabla, columnas_a_excluir
    $M -N -B "$COPIA" -e "
        SELECT GROUP_CONCAT(column_name ORDER BY ordinal_position),
               GROUP_CONCAT(CONCAT('t.', column_name) ORDER BY ordinal_position)
          FROM information_schema.columns
         WHERE table_schema='${COPIA}' AND table_name='$1'
           AND extra NOT LIKE '%GENERATED%'
           AND column_name NOT REGEXP '^($2)\$';" 2>/dev/null
}

$M "$COPIA" -e "
    SET SESSION cte_max_recursion_depth = 200000;
    DROP TABLE IF EXISTS _n;
    CREATE TABLE _n (n INT PRIMARY KEY);
    INSERT INTO _n WITH RECURSIVE s(n) AS
        (SELECT 1 UNION ALL SELECT n+1 FROM s WHERE n < ${EXPEDIENTES}) SELECT n FROM s;" 2>/dev/null

IFS=$'\t' read -r EXP_BARE _ < <(columnas expedientes 'id|uuid_publico|codigo_interno|numero_expediente')
BASE_EXP=$($M -N -B "$COPIA" -e "SELECT COUNT(*) FROM expedientes;" 2>/dev/null)
$M "$COPIA" -e "
    SET @@session.unique_checks=0; SET @@session.foreign_key_checks=0;
    INSERT INTO expedientes (codigo_interno, numero_expediente, ${EXP_BARE})
    SELECT CONCAT('EXP-P', LPAD(n.n, 6, '0')),
           CONCAT(LPAD(n.n, 5, '0'), '-', 2021 + (n.n % 5), '-0-1601-JR-CI-0', 1 + (n.n % 3)),
           ${EXP_BARE}
      FROM _n n JOIN expedientes e ON e.id = 1 + (n.n % ${BASE_EXP});" 2>&1 | grep -viE warning | head -3

# 1 de cada 12 confidencial, para que la regla de visibilidad tenga trabajo
$M "$COPIA" -e "UPDATE expedientes SET es_confidencial = (id % 12 = 0) WHERE id > ${BASE_EXP};" 2>/dev/null
$M "$COPIA" -e "
    SET @@session.unique_checks=0; SET @@session.foreign_key_checks=0;
    INSERT INTO expediente_equipo (expediente_id, usuario_id, rol_en_expediente, activo, fecha_asignacion)
    SELECT e.id, u.id, 'APOYO', 1, CURRENT_DATE
      FROM expedientes e JOIN usuarios u ON u.tipo_usuario='INTERNO' AND u.activo=1
     WHERE e.id > ${BASE_EXP} AND (e.id + u.id) % 4 = 0;" 2>&1 | grep -viE warning | head -3

inflar() { # tabla, veces_por_expediente
    local bare aliased plantillas
    IFS=$'\t' read -r bare aliased < <(columnas "$1" 'id|uuid_publico|expediente_id')
    plantillas=$($M -N -B "$COPIA" -e "SELECT COUNT(*) FROM $1;" 2>/dev/null)
    [ "${plantillas:-0}" -eq 0 ] && { echo "  $1: sin filas de plantilla, se omite"; return; }
    $M "$COPIA" -e "
        SET @@session.unique_checks=0; SET @@session.foreign_key_checks=0;
        INSERT INTO $1 (expediente_id, ${bare})
        SELECT e.id, ${aliased}
          FROM expedientes e
          JOIN $1 t ON t.id = 1 + (e.id % ${plantillas})
          JOIN _n k ON k.n <= $2
         WHERE e.id > ${BASE_EXP};" 2>&1 | grep -viE warning | head -3
}
inflar actuaciones 10
inflar documentos 6
inflar plazos 3
inflar eventos_agenda 4

for t in expedientes actuaciones documentos plazos eventos_agenda expediente_equipo; do
    $M "$COPIA" -e "ANALYZE TABLE $t;" >/dev/null 2>&1
done
$M -t "$COPIA" -e "
    SELECT 'expedientes' tabla, COUNT(*) filas FROM expedientes
    UNION ALL SELECT 'actuaciones', COUNT(*) FROM actuaciones
    UNION ALL SELECT 'documentos', COUNT(*) FROM documentos
    UNION ALL SELECT 'plazos', COUNT(*) FROM plazos
    UNION ALL SELECT 'eventos_agenda', COUNT(*) FROM eventos_agenda;" 2>/dev/null

# -----------------------------------------------------------------------------
titulo "3. Arrancando el backend contra ${COPIA}"
# Flyway desactivado a proposito: la copia ya trae el esquema, y el seed
# repetible hace TRUNCATE, que borraria todo lo que acabamos de generar.
cd "$RAIZ/BackendLexia" || exit 1
DB_NAME="$COPIA" DB_PASSWORD="$DB_PASSWORD" \
    ./mvnw -q spring-boot:run \
    -Dspring-boot.run.arguments="--server.port=${PUERTO} --spring.flyway.enabled=false" \
    > "$TMP/backend.log" 2>&1 &
MVN=$!
for _ in $(seq 1 120); do
    grep -q "Started BackendLexiaApplication" "$TMP/backend.log" 2>/dev/null && break
    sleep 1
done
if ! grep -q "Started BackendLexiaApplication" "$TMP/backend.log" 2>/dev/null; then
    echo "  no arranco:"; grep -iE "ERROR|Caused by" "$TMP/backend.log" | head -10
    kill $MVN 2>/dev/null; pkill -f "spring-boot:run" 2>/dev/null; exit 1
fi
echo "  arriba en el puerto ${PUERTO}"

API="http://localhost:${PUERTO}/api/v1"
token() { curl -s -X POST "$API/auth/login" -H "Content-Type: application/json" \
    -d "{\"email\":\"$1\",\"password\":\"Lexia.Dev.2026\"}" | grep -oP '"token"\s*:\s*"\K[^"]+'; }

medir() { # etiqueta, ruta
    local mejor=999999 t bytes out
    for _ in 1 2 3; do
        out=$(curl -s -o "$TMP/r.json" -w '%{time_total} %{size_download}' \
              -H "Authorization: Bearer $TOKEN" "${API}$2")
        t=$(echo "$out" | awk '{printf "%.0f", $1*1000}')
        bytes=$(echo "$out" | awk '{print $2}')
        [ "$t" -lt "$mejor" ] && mejor=$t
    done
    printf "  %-46s %6s ms  %7s KB\n" "$1" "$mejor" "$((bytes/1024))"
}

for cuenta in "juan.perez@lexia.com:abogado, ve solo lo asignado" \
              "carlos.mendoza@lexia.com:administrador, ve todo (peor caso)"; do
    TOKEN=$(token "${cuenta%%:*}")
    [ -z "${TOKEN:-}" ] && { echo "  login fallo para ${cuenta%%:*}"; continue; }
    titulo "4. Tiempos — ${cuenta#*:}"
    printf "  %-46s %9s  %10s\n" "ENDPOINT" "MEJOR DE 3" "RESPUESTA"
    medir "clientes (paginado, de referencia)" "/clientes?size=20"
    medir "expedientes"                       "/expedientes"
    medir "actuaciones"                        "/actuaciones"
    medir "actuaciones, buscando texto"        "/actuaciones?texto=demanda"
    medir "documentos"                         "/documentos"
    medir "plazos"                             "/plazos"
    medir "agenda, un mes"                     "/agenda"
    medir "actuaciones/catalogos"              "/actuaciones/catalogos"
    medir "plazos/catalogos"                   "/plazos/catalogos"
done

kill $MVN 2>/dev/null; sleep 3; pkill -f "spring-boot:run" 2>/dev/null

# -----------------------------------------------------------------------------
titulo "5. Limpiando"
$M -e "DROP DATABASE IF EXISTS ${COPIA};" 2>/dev/null
echo "  ${COPIA} eliminada. Ninguna base de trabajo se toco."
