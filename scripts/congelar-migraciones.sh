#!/usr/bin/env bash
# =============================================================================
# LEXIA — Congelar migraciones: guardar la huella de las que ya estan publicadas.
# =============================================================================
# Una migracion aplicada no se edita jamas (regla 3 de CLAUDE.md): Flyway guarda
# su checksum y, si el archivo cambia, aborta el arranque de quien ya la tenga
# con "Migration checksum mismatch". Ya paso con V10 (2026-09-12) y con V16 y V18
# (2026-10-01, indices agregados dentro de las migraciones de agenda y plazos).
#
# GuardiasDelRepositorioTest compara cada migracion contra la huella de este
# manifiesto y falla si alguna cambio. No necesita base de datos.
#
# Se corre en el MISMO PR que crea la migracion, desde la raiz del repo:
#
#   bash scripts/congelar-migraciones.sh
#
# y se sube el manifiesto con ella. Si falta una huella, la prueba
# todaMigracionEstaCongelada falla (y el CI del PR con ella).
#
# Solo AGREGA las migraciones que aun no estan en el manifiesto. Nunca reescribe
# una huella existente: cambiar una huella a mano es justamente lo que el
# manifiesto esta para impedir, y se nota en el diff del PR.
#
# Las huellas se calculan sobre el contenido con fin de linea LF, para que Windows
# (CRLF) y Linux den lo mismo.
# =============================================================================
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
MIGRACIONES="$RAIZ/BackendLexia/src/main/resources/db/migration"
MANIFIESTO="$RAIZ/BackendLexia/src/test/resources/migraciones.sha256"

touch "$MANIFIESTO"
nuevas=0

for f in $(ls "$MIGRACIONES"/V*.sql | sort -V); do
    nombre="$(basename "$f")"
    if grep -q "  ${nombre}\$" "$MANIFIESTO"; then
        continue
    fi
    huella="$(tr -d '\r' < "$f" | sha256sum | cut -d' ' -f1)"
    echo "${huella}  ${nombre}" >> "$MANIFIESTO"
    echo "  + ${nombre}"
    nuevas=$((nuevas + 1))
done

if [ "$nuevas" -eq 0 ]; then
    echo "Nada que congelar: todas las migraciones ya estan en el manifiesto."
else
    sort -V -k2 -o "$MANIFIESTO" "$MANIFIESTO"
    echo "${nuevas} migracion(es) congelada(s) en BackendLexia/src/test/resources/migraciones.sha256"
fi
