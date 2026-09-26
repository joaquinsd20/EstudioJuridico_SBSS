# Antes de añadir una migración

Tres incidentes en dos semanas salieron de esta carpeta. Los tres eran del
nombre del archivo, no del SQL. Cuesta menos leer esto que depurarlo.

## 1. Dos guiones bajo, no uno

```
V21__descripcion_corta.sql     correcto
V21_descripcion_corta.sql      Flyway la IGNORA
```

Con un solo guion bajo, Flyway **no falla**. Se la salta y lo dice en una línea
de `INFO` que se pierde entre el resto del arranque:

```
1 SQL migrations were detected but not run because they did not follow
the filename convention.
```

El backend arranca perfecto, en 13 segundos, sin un solo `ERROR`, y las tablas
no existen. Pasó con `V19_facturacion.sql`: el módulo entero —9 endpoints y una
pantalla de 3.130 líneas— consultaba dos tablas que nunca se crearon, y nadie
lo notó porque el servicio devolvía lista vacía ante cualquier excepción.

Para comprobarlo antes de subir, el arranque tiene que decir el número que
esperás:

```
Successfully applied N migrations to schema `lexia_db`, now at version vN
```

## 2. El número, avisado en el grupo

Dos migraciones con el mismo número y **nadie del equipo arranca**:

```
Found more than one migration with version 12
```

Git no lo detecta, porque son dos archivos distintos. Se descubre cuando ya está
en `main`. Ya pasó con V12, y con V15, que estaba "reservada de palabra" para
facturación y la tomó actuaciones: **la reserva de palabra no funciona**. Antes
de crear el archivo, mirar el último número en esta carpeta y decirlo en el
grupo.

## 3. Nada de `USE <base>` dentro

La base la elige la conexión (`DB_NAME` en desarrollo, `LEXIA_DB_NAME` en
producción). Un `USE lexia_db` dentro de la migración escribe en la base de
desarrollo aunque Flyway apunte a otra, y en producción apunta a una base que
no existe.

## 4. La collation es `utf8mb4_0900_ai_ci`

La de todo el esquema. `scripts/verificar-bd.sh` comprueba que haya una sola, y
mezclarlas da `Illegal mix of collations` en cuanto dos `CHAR(36)` de tablas
distintas se comparan.

## 5. Si añade una tabla, hay que tocar `verificar-bd.sh`

El script comprueba el número de tablas. Si no se actualiza en el mismo PR,
empieza a fallar para todo el equipo y deja de servir para nada.

## 6. Su propia consulta de verificación

Al final del archivo, comentada: qué hay que consultar para saber que la
migración hizo lo que dice, con el resultado esperado. Y si tiene sentido, una
**prueba negativa**: un INSERT que la base tiene que rechazar.

## Y una que no es del nombre

**Una migración ya aplicada no se edita nunca.** Cambia su checksum y el
arranque de quien ya la tenía falla con `Migration checksum mismatch`. Se
corrige con la siguiente.

La excepción es una que **nunca** se aplicó en ninguna base —porque Flyway la
estaba ignorando, o porque no se ha subido todavía—: esa sí se corrige en su
sitio, y es mejor que arrastrar el error en un parche.
