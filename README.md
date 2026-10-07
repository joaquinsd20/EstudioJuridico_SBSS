# LEXIA — Sistema de Gestión para Estudios Jurídicos

Gestión integral de un estudio de abogados: expedientes, clientes, agenda,
actuaciones, plazos procesales, tareas, documentos, biblioteca jurídica, monitor
judicial y facturación.

Monorepo con dos aplicaciones y una capa de datos:

| Componente | Carpeta | Stack |
|---|---|---|
| API REST | `BackendLexia/` | Spring Boot 4.1.1 · Java 21 · Maven · Spring Security (JWT) · Spring Data JPA + JdbcTemplate · Flyway · JJWT · Apache POI · PDFBox |
| Interfaz web | `FrontendLexia/` | Angular 21.2 (standalone, signals, zone-less) · TypeScript 5.9 · Vitest · ExcelJS |
| Base de datos | — | MySQL 8.0.16+ / 9.x · InnoDB · `utf8mb4_0900_ai_ci` |
| Utilidades | `scripts/` | Bash: verificación de esquema y benchmark de rendimiento |

---

## 1. Estado actual

Verificado en local el **28/09/2026** contra MySQL 9.7.1.

| Item | Estado |
|---|---|
| Compilación del backend (123 clases) | OK |
| Arranque de Spring Boot | OK — ~11 s |
| Migraciones Flyway | OK — 22 migraciones aplicadas, esquema en `v22` |
| Login JWT y RBAC | OK — 37 permisos sobre el rol `ADMINISTRADOR` |
| 12 módulos REST (~90 endpoints) | OK — los 13 endpoints de cabecera devuelven `200` |
| CORS `4200 → 8080` | OK |
| Compilación de Angular (18 chunks lazy) | OK — 7.5 s |
| Arranque del dev-server en `:4200` | OK |
| Tests unitarios del backend | OK |
| Tests de integración | Requieren `LEXIA_TEST_DB` (ver §10) |
| `npm test` | **1 de 2 tests falla** (ver §12) |

Contenido sembrado en desarrollo: **45 tablas**, 8 roles, 37 permisos, 145 pares
rol-permiso, 4 funciones SQL; 11 usuarios, 8 clientes, 10 expedientes, 14
actuaciones, 9 plazos, 10 tareas, 9 documentos, 8 eventos de agenda.

---

## 2. Requisitos

| Herramienta | Versión | Nota |
|---|---|---|
| JDK | **21** | Obligatorio. Ver §3. |
| Node.js | 20.19+ / 22.12+ / 24.x | `package.json` fija `packageManager: npm@11.11.0` |
| MySQL | ≥ 8.0.16 | Por debajo de 8.0.16 no se aplican los `CHECK`. Probado en 8.0.46 y 9.7.1 |
| Maven | No necesario | El wrapper (`mvnw` / `mvnw.cmd`) descarga la 3.9.16 solo |

> `mvn` no hace falta en el `PATH`. Usa siempre `mvnw.cmd` (Windows) o `./mvnw`
> (Bash). El wrapper está en modo `only-script`: no usa `maven-wrapper.jar`.

---

## 3. ⚠️ JDK 25 rompe la compilación

Este es el primer obstáculo real al levantar el proyecto en una máquina nueva.

El `pom.xml` fija `<java.version>21</java.version>`, pero si el JDK por defecto de
la máquina es 24 o superior, la compilación **falla con ~200 errores**:

```
cannot find symbol: method getUuidPublico()
  location: variable cliente of type com.sbss.lexia.clientes.entity.Cliente
cannot find symbol: method builder()
  location: class com.sbss.lexia.biblioteca.dto.BibliotecaEstadisticasResponse
variable service not initialized in the default constructor
```

**Causa:** desde JDK 24, `javac` ya no ejecuta automáticamente los annotation
processors que encuentra en el classpath. Lombok no es invocado, así que `@Getter`
y `@Setter` no generan nada y todas las entidades quedan sin sus accesores.

**Solución inmediata** — apuntar `JAVA_HOME` al JDK 21 antes de arrancar:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12"
```

**Solución permanente pendiente** — declarar Lombok explícitamente en el
`maven-compiler-plugin` (`annotationProcessorPaths`), o fijar el *toolchain* a 21.
Mientras no se haga, cualquiera que tenga JDK 25 como `java` por defecto no podrá
arrancar el backend.

---

## 4. Estructura del repositorio

```
EstudioJuridico_SBSS/
├── BackendLexia/
│   ├── pom.xml, mvnw, mvnw.cmd
│   └── src/
│       ├── main/java/com/sbss/lexia/
│       │   ├── BackendLexiaApplication.java     entrypoint
│       │   ├── actuaciones/    agenda/          bibliote/    catalogos/
│       │   ├── clientes/       documental/      expedientes/ facturacion/
│       │   ├── monitor/        plazos/          seguridad/   tareas/
│       │   ├── usuarios/       shared/
│       │   └── shared/         auditoría, config, excepción, seguridad, util, bootstrap
│       ├── main/resources/
│       │   ├── application.yml          perfil común (default: dev)
│       │   ├── application-dev.yml      datasource + JWT de desarrollo + CORS
│       │   ├── application-prod.yml     todo obligatorio, sin valores por defecto
│       │   └── db/
│       │       ├── bootstrap/            catálogo inicial del estudio
│       │       ├── migration/            V1..V22 + LEEME.md (léelo antes de migrar)
│       │       └── seed/                 R__seed_desarrollo.sql (solo dev)
│       └── test/java/com/sbss/lexia/     13 clases: unitarios + 9 *IT.java
├── FrontendLexia/
│   ├── package.json, angular.json, tsconfig*.json
│   └── src/app/
│       ├── core/          guards, interceptors, models, services
│       └── pages/         18 pantallas (login, panel, expedientes, …)
└── scripts/
    ├── verificar-bd.sh        gate de calidad de las migraciones
    └── medir-rendimiento.sh   benchmark con volumen realista
```

Cada módulo de negocio sigue el patrón **controller → service → repository**
(+ `dto`, `entity` cuando aplica). Los repositorios de las consultas pesadas
(`expedientes`, `facturacion`, `tareas`, `plazos`, `agenda`, `actuaciones`,
`monitor`, `usuarios`) usan `JdbcTemplate` crudo en lugar de JPA.

---

## 5. Puesta en marcha

### 5.1 Base de datos

Crear la base vacía. Flyway construye el esquema completo.

```powershell
& "C:\Program Files\MySQL\MySQL Server 9.7\bin\mysql.exe" -u root -p `
  -e "CREATE DATABASE lexia_db_sbss CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

### 5.2 Backend (terminal 1)

```powershell
cd C:\Users\COMPUTER\EstudioJuridico_SBSS\BackendLexia

$env:JAVA_HOME  = "C:\Program Files\Java\jdk-21.0.12"   # ver §3
$env:DB_HOST    = "127.0.0.1"
$env:DB_PORT    = "3306"
$env:DB_NAME    = "lexia_db_sbss"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "<tu contraseña>"

.\mvnw.cmd spring-boot:run
```

En Git Bash / Linux / macOS:

```bash
export DB_PASSWORD='tu_contrasena'
./mvnw spring-boot:run
```

La primera vez descarga Maven y las dependencias. Arranca en **http://localhost:8080**.
El perfil por defecto es `dev`; no hace falta `SPRING_PROFILES_ACTIVE`.

**Comprueba que arrancó bien.** En el log debe aparecer esta línea — si dice
`0 migrations` o si falta, el esquema está incompleto (ver §8):

```
Successfully applied N migrations to schema `lexia_db_sbss`, now at version v22
```

> ⚠️ `db/seed/R__seed_desarrollo.sql` hace `TRUNCATE` de 33 tablas y las vuelve a
> llenar en **cada** arranque de desarrollo. Los datos locales de `lexia_db_sbss`
> se borran. Es el comportamiento previsto (fechas relativas a `CURRENT_DATE`),
> pero no apuntes ahí datos que quieras conservar.

### 5.3 Frontend (terminal 2)

```powershell
cd C:\Users\COMPUTER\EstudioJuridico_SBSS\FrontendLexia
npm install     # solo la primera vez
npm start
```

Abre **http://localhost:4200**.

### 5.4 Variables de entorno

Definidas en el nivel de usuario de Windows sobreviven a cerrar la terminal:

```powershell
[Environment]::SetEnvironmentVariable('DB_NAME',     'lexia_db_sbss', 'User')
[Environment]::SetEnvironmentVariable('DB_USERNAME', 'root',           'User')
[Environment]::SetEnvironmentVariable('DB_PASSWORD', '<contraseña>',   'User')
```

Alternativa con archivo (ignorado por git, ver §8): crear
`BackendLexia/src/main/resources/application-local.yml` y arrancar con
`SPRING_PROFILES_ACTIVE=dev,local`. El sufijo `-local` **no** se carga solo.

| Variable | Perfil | Default | Para qué |
|---|---|---|---|
| `DB_HOST` | dev | `localhost` | Servidor MySQL |
| `DB_PORT` | dev | `3306` | Puerto MySQL |
| `DB_NAME` | dev | `lexia_db` | Base de datos |
| `DB_USERNAME` | dev | `root` | Usuario |
| `DB_PASSWORD` | dev | *(vacío)* | Contraseña |
| `JWT_SECRETO` | dev | secreto de desarrollo en el repo | Clave HS256, mínimo 32 bytes |
| `SERVER_PORT` | todos | `8080` | Puerto del backend |
| `SPRING_PROFILES_ACTIVE` | todos | `dev` | `dev`, `prod`, o `dev,local` |
| `LEXIA_ADMIN_EMAIL` / `LEXIA_ADMIN_PASSWORD` | ambos | — | Solo el primer arranque en una base vacía |
| `LEXIA_ADMIN_NOMBRES` / `LEXIA_ADMIN_APELLIDOS` | ambos | — | Idem |
| `LEXIA_ESTUDIO_RUC` | ambos | — | Idem |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | prod | — | **Sin default: la app no arranca** |
| `JWT_SECRETO` / `CORS_ORIGENES` | prod | — | **Sin default** |

### 5.5 Verlo en el celular (o desde cualquier lugar)

El dev-server escucha en `0.0.0.0` (`npm start`) y `FrontendLexia/proxy.conf.js`
reenvía `/api/**` al backend en `localhost:8080` — declarado en
`angular.json → serve.options.proxyConfig`. Al servir app y API desde el **mismo
origen** no hay que ampliar CORS por cada origen nuevo, y `environment.ts` usa
por eso una URL relativa (`/api/v1`).

El proxy es un `.js` y no un `.json` porque además **retira el header `Origin`**
antes de reenviar: el navegador lo conserva con su propio host
(`https://….trycloudflare.com`, `http://<IP-local>:4200`), mientras el proxy
reescribe `Host` a `localhost:8080`; Spring ve los dos distintos y respondería
`403` por CORS. Como la petición es mismo origen, el navegador no exige esas
cabeceras, así que no hacen falta. Detalle en `proxy.conf.js:1-20`.

**Arranque de una sola vez** (MySQL + backend + frontend + túnel, en ese orden):

```powershell
cd C:\Users\<tu-usuario>\EstudioJuridico_SBSS
.\scripts\levantar-lexia.ps1
```

Cada paso arranca solo si su puerto está libre, así que se puede repetir sin
duplicar procesos; los logs quedan en `%TEMP%\lexia-logs\`. Solo el túnel bloquea
(`Ctrl+C` lo corta y deja el resto corriendo).

O, con las dos terminales ya levantadas (§5.2 y §5.3), solo el túnel:

```powershell
cd C:\Users\<tu-usuario>\EstudioJuridico_SBSS
.\scripts\ver-en-celular.ps1
```

El script comprueba los puertos 4200 y 8080, descarga `cloudflared` la primera
vez, abre un túnel de Cloudflare y muestra una URL pública `https://….trycloudflare.com`
con su QR para escanear con la cámara del celular.

- Funciona desde **cualquier red** (datos móviles incluidos) **sin abrir puertos
  en el router**: solo se publica el 4200; el backend en 8080 sigue oculto.
- La URL es efímera, cambia en cada arranque; `Ctrl+C` cierra el túnel.
- Si el dev-server responde `Blocked request. This host is not allowed`, el
  hostname no está en `angular.json → serve.options.allowedHosts` (de fábrica
  solo `.trycloudflare.com`; las IP locales están permitidas siempre).
- **Red local sin túnel:** abre `http://<IP-del-PC>:4200` en el celular (misma
  Wi-Fi) y deja pasar el 4200 en el firewall de Windows; el backend tampoco
  necesita CORS porque sigue siendo mismo origen.

---

## 6. Credenciales de desarrollo

Semilla: `db/seed/R__seed_desarrollo.sql`. **Contraseña de los 11 usuarios:
`Lexia.Dev.2026`** (BCrypt cost 12).

| Email | Rol | Para probar |
|---|---|---|
| `carlos.mendoza@lexia.com` | `ADMINISTRADOR` | Acceso total, ve todo el estudio |
| `sofia.ramirez@lexia.com` | `SOCIO` + `ABOGADO` | Aprobación de calendario y plazos |
| `juan.perez@lexia.com` | `ABOGADO` | Solo ve lo asignado |
| `maria.lopez@lexia.com` | `ABOGADO_AUXILIAR` | Responsable del expediente confidencial EXP-0088 |
| `roberto.diaz@lexia.com` | `CONTABILIDAD` | Honorarios y pagos |
| `rosa.jimenez@lexia.com` | `ASISTENTE_LEGAL` | Soporte documental |
| `lucia.castillo@lexia.com` | `RECEPCIONISTA` | **`debe_cambiar_password = 1`** |

Lucia está a propósito con la contraseña temporal: al entrar,
`CambioPasswordObligatorioFilter` bloquea todas las demás rutas hasta que la
cambie. Sirve para validar ese flujo.

> Estas credenciales son de desarrollo y están en el repositorio a propósito.
> El perfil `prod` no incluye `db/seed` en `flyway.locations`.

---

## 7. Módulos funcionales

| Módulo | Ruta web | Endpoint | Estado |
|---|---|---|---|
| Autenticación | `/login`, `/cambiar-password` | `/api/v1/auth` | Completo |
| Expedientes | `/expedientes` | `/api/v1/expedientes` | Completo |
| Clientes | `/clientes` | `/api/v1/clientes` | Completo (incluye conflicto de intereses) |
| Agenda | `/agenda` | `/api/v1/agenda` | Completo |
| Actuaciones | `/actuaciones` | `/api/v1/actuaciones` | Completo (con costos) |
| Plazos | `/plazos` | `/api/v1/plazos` | Completo |
| Tareas | `/tareas` | `/api/v1/tareas` | Completo (flujo de aprobación + export Excel) |
| Documentos | `/documentos` | `/api/v1/documentos` | Completo (versiones, papelera, conversión) |
| Biblioteca jurídica | `/biblioteca-juridica` | `/api/v1/biblioteca` | Completo |
| Monitor judicial | `/monitor-judicial` | `/api/v1/monitor-judicial` | Completo |
| Facturación | `/facturacion` | `/api/v1/facturacion` | Completo (honorarios, pagos, comprobantes) |
| Usuarios | `/usuarios` | `/api/v1/usuarios` | Completo (reasignación de carga) |
| **Dashboard** | `/dashboard` | — | **Placeholder** — HTML estático |
| **Reportes** | `/reportes` | — | **Placeholder** — sin servicio |
| **Escritos con IA** | `/escritos-ia` | — | **Placeholder** — sin servicio |
| **Configuración** | `/configuracion` | — | **Placeholder** — sin servicio |

Las cuatro pantallas de placeholder importan `PanelComponent` y nada más.
`dashboard.component.html:13` además tiene el saludo
`"Buenos días, Carlos Mendoza Rojas 👋"` escrito a mano.

Todas las rutas del router pasan por `authGuard`, salvo `login` y el wildcard
`**` (que redirige a `login`).

---

## 8. Base de datos

- **44–45 tablas** InnoDB, una sola collation: `utf8mb4_0900_ai_ci`.
- **22 migraciones** `V1..V22` en `db/migration/`, aplicadas por Flyway.
- `ddl-auto: validate`: Hibernate **solo verifica** que las entidades cuadren con
  las tablas. El esquema es de Flyway, nunca `update`.
- Zona horaria fijada a `America/Lima` en Hibernate y en Jackson. El esquema usa
  `DATETIME(3)` sin zona; no debe cambiarse.
- `open-in-view: false`: una carga perezosa fuera de transacción falla de forma
  visible en vez de disparar consultas silenciosas.
- 4 funciones SQL de cómputo de plazos, replicadas en
  `shared/util/CalculadoraPlazos.java`. La paridad Java↔SQL la comprueba
  `CalculadoraPlazosParidadIT`.

### Antes de añadir una migración

`db/migration/LEEME.md` documenta tres incidentes reales. El nombre del
archivo es la causa de todos ellos:

1. **Doble guion bajo.** `V21__descripcion.sql` funciona; `V21_descripcion.sql`
   la **ignora en silencio** (una línea de `INFO`). Pasó con
   `V19_facturacion.sql`: 9 endpoints y una pantalla de 3.130 líneas
   consultaban tablas que nunca se crearon, y el servicio devolvía lista vacía
   ante cualquier excepción, así que nadie lo notó.
2. **El número se avisa en el grupo.** Dos migraciones con el mismo número
   impiden arrancar a todo el equipo. Ya pasó con V12 y con V15.
3. **Nunca `USE <base>`** dentro de una migración: la base la elige la conexión.
4. **Una migración ya aplicada no se edita nunca** — cambia su checksum y rompe el
   arranque de quien ya la tenía.

> `verificar-bd.sh` sigue esperando 44 tablas y el esquema ya tiene 45: el script
> está desactualizado y hay que corregirlo en el mismo PR que añada la tabla.

---

## 9. Seguridad

- **JWT HS256**, 8 h de expiración (`lexia.jwt.expiracion-segundos: 28800`).
  El secreto debe tener 32 bytes o `JwtService` lanza `IllegalStateException`.
  Un claim `pv` de versión de contraseña invalida los tokens al cambiarla.
- **BCrypt cost 12.**
- **RBAC**: 8 roles × 37 permisos (145 asignaciones). `@PreAuthorize` compara
  siempre contra `permisos.codigo` / `roles.codigo`, nunca contra la etiqueta
  visible.
- **CSRF desactivado** (API stateless), `SecurityManager` stateless.
  Públicos: `OPTIONS`, `POST /api/v1/auth/login`, `/error`.
- **Aislamiento por estudio**: todas las consultas filtran por `estudio_id`,
  tomado de la sesión, no del cliente.
- **Visibilidad de expedientes** (`shared/security/VisibilidadExpedientes.java`):
  un abogado sin `EXPEDIENTE_CONFIDENCIAL` no ve los expedientes confidenciales
  ni los que no tiene asignados.
- **Auditoría JPA** en toda entidad que hereda `EntidadAuditable` (5 columnas).
- El token vive en `localStorage` (`lexia.token`, `lexia.usuario`). Es una
  debilidad conocida de XSS, documentada en `auth.service.ts:10-20`.

### Roles

| Código | Nivel | Alcance |
|---|---|---|
| `ADMINISTRADOR` | 100 | Todo: configuración, usuarios, roles, seguridad |
| `SOCIO` | 80 | Supervisión, plazos críticos, aprobaciones, finanzas |
| `ABOGADO` | 60 | Expedientes asignados, actuaciones, tareas, documentos, agenda |
| `ABOGADO_AUXILIAR` | 40 | Solo expedientes autorizados. Sin finanzas ni confidenciales |
| `ASISTENTE_LEGAL` | 30 | Documentos, notificaciones, agenda, soporte |
| `CONTABILIDAD` | 30 | Honorarios y pagos. Sin contenido jurídico reservado |
| `RECEPCIONISTA` | 20 | Clientes potenciales y recepción de documentos |
| `CLIENTE` | 10 | Portal: su expediente y documentos autorizados |

---

## 10. Tests

### Backend

```powershell
cd BackendLexia
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12"
.\mvnw.cmd test
```

Unitarios puros, sin base de datos:

| Clase | Qué cubre |
|---|---|
| `CalculadoraPlazosTest` | Días hábiles, ámbito, vencimiento, suspensiones (6 grupos `@Nested`) |
| `PoliticaPasswordTest` | Longitud ≥ 12, ≤ 72 bytes, tildes, repetidos, email incluido |
| `HashSemillaTest` | Que el hash BCrypt de la semilla sea el de `Lexia.Dev.2026` |

Tests de integración (`*IT.java`, 9 clases) — llevan la guarda
`@EnabledIfEnvironmentVariable(named = "LEXIA_TEST_DB")`, así que **no corren**
por defecto:

```powershell
$env:LEXIA_TEST_DB = "1"
.\mvnw.cmd test                                     # todas
.\mvnw.cmd test -Dtest=VisibilidadExpedientesIT      # una
```

Cubren: visibilidad de expedientes, aislamiento por estudio, permisos
documentales, visibilidad de documentos, buscador de expedientes, cambio de
contraseña, visibilidad de tareas, reasignación de trabajo y gestión de
usuarios.

> ⚠️ **No hay `src/test/resources/`, ni perfil `test`, ni Testcontainers.** Los
> tests corren contra el perfil `dev` real, es decir contra `lexia_db_sbss`, y
> la semilla borra sus tablas en cada ejecución. Además
> `BackendLexiaApplicationTests.contextLoads()` y
> `CalculadoraPlazosParidadIT` **siempre** exigen MySQL: sin base de datos
> levantada, `./mvnw test` falla aunque solo quieras los unitarios.

### Frontend

```powershell
cd FrontendLexia
npm test
```

Vitest vía `@angular/build:unit-test` (jsdom). **No hay tests e2e** — el
`ng e2e` del README de Angular no tiene target en `angular.json`.

---

## 11. Scripts

### `scripts/verificar-bd.sh`

Suite de verificación contra MySQL real. Es el **gate de calidad de las
migraciones**: si falla, la migración está mal aunque el backend arranque.

```bash
bash scripts/verificar-bd.sh [usuario] [base]     # default: root / lexia_db
```

Comprueba: versión del motor ≥ 8.0.16 · inventario del esquema (tablas, roles,
permisos, pares rol-permiso, funciones, InnoDB único, una sola collation) · **10
pruebas negativas** que la base debe rechazar (`CHECK`, `FK`, `ENUM`) · paridad
`fn_calcular_vencimiento` contra la tabla `plazos` · que un calendario `APROBADO`
afecte al cómputo y uno `PROPUESTO` no · que 5 conteos del dashboard sean
estables al reejecutar · `EXPLAIN` del índice caliente (el tipo no debe ser `ALL`).
Sale con código 1 si algo falla.

### `scripts/medir-rendimiento.sh`

Benchmark que crea y borra una base `lexia_perf` aparte (nunca toca `lexia_db`).

```bash
export DB_PASSWORD='tu_contrasena'
bash scripts/medir-rendimiento.sh [expedientes]     # default: 5000
```

Infla el esquema por CTE recursivo, arranca el backend en `:8099` con
`--spring.flyway.enabled=false`, y mide el mejor de 3 para 2 cuentas × 9
endpoints. Conclusiones ya registradas en el encabezado del script: lo que
dominaba el coste era **el tamaño de la respuesta** y los contadores por fila
antes del `LIMIT`; lo que no importaba era el `EXISTS` de visibilidad, la
profundidad del `OFFSET`, la desnormalización de `estudio_id` ni un índice nuevo
en `fecha_hora`.

---

## 12. Problemas conocidos

| # | Problema | Dónde |
|---|---|---|
| 1 | **El build falla con JDK ≥ 24** por Lombok (§3) | `pom.xml` |
| 2 | **`npm test` falla 1 de 2**: espera un `<h1>Hello, FrontedLexia</h1>` que no existe | `app.spec.ts:17` |
| 3 | **`@Scheduled` sin `@EnableScheduling`**: el cron de limpieza de papelera nunca se ejecuta | `PapeleraDocumentoScheduler.java:18` |
| 4 | 4 pantallas placeholder sin backend: dashboard, reportes, escritos-ia, configuracion | `src/app/pages/` |
| 5 | Los tests corren destructivos contra la BD de desarrollo | falta `src/test/resources/` |
| 6 | `verificar-bd.sh` espera 44 tablas; el esquema tiene 45 | `verificar-bd.sh:43` |
| 7 | El proyecto Angular se llama `FrontedLexia` (falta la `n`) | `angular.json:10` |
| 8 | Proxy de desarrollo añadido (`proxy.conf.js`, ver §5.5). En prod sigue haciendo falta un reverse proxy no documentado | `environment.prod.ts:11` |
| 9 | `biblioteca` y `facturacion` llaman `HttpClient` desde el componente, no desde `core/services` | `biblioteca-juridica.component.ts:77`, `facturacion.component.ts:254` |
| 10 | Sin CI, sin Docker, sin ESLint | raíz |
| 11 | `CLAUDE.md` y `docs/` están en `.gitignore` pero el código los cita ~10 veces | `.gitignore:78-79` |
| 12 | El comentario de la semilla describe el aislamiento de `prod` con `ignore-migration-patterns`, pero `prod` ya lo hace con `locations` | `R__seed_desarrollo.sql:6-8` |

---

## 13. Producción

```bash
cd BackendLexia
./mvnw clean package
SPRING_PROFILES_ACTIVE=prod java -jar target/BackendLexia-0.0.1-SNAPSHOT.jar
```

Sin valores por defecto: si falta `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`JWT_SECRETO` o `CORS_ORIGENES`, **la aplicación no arranca**. En el primer
arranque sobre una base vacía hace falta además `LEXIA_ESTUDIO_RUC`,
`LEXIA_ADMIN_EMAIL` y `LEXIA_ADMIN_PASSWORD` (mínimo 12 caracteres):
`shared/bootstrap/InicializadorEstudio.java` crea el estudio y su administrador
solo si la tabla `estudios` está vacía.

Diferencias con `dev`:

- `flyway.locations` = solo `db/migration`. **La semilla de desarrollo no se carga.**
- `baseline-on-migrate: false`.

El frontend de producción compila contra `/api/v1` (mismo origen), así que necesita
un reverse proxy delante que sirva los estáticos de `dist/` y reenvíe `/api` al
backend.

---

## 14. Documentación relacionada

Solo hay tres archivos Markdown en el repositorio:

- `README.md` (este) — puesta en marcha y referencia del proyecto.
- `BackendLexia/src/main/resources/db/migration/LEEME.md` — **léelo antes de
  escribir una migración**.
- `FrontendLexia/README.md` — boilerplate del CLI de Angular.

El resto del conocimiento (variables, reglas, arquitectura) está en los
encabezados de los archivos de configuración y en los comentarios del código,
que son muy densos. En particular: `application-dev.yml:1-19`,
`application-prod.yml:1-21`, `scripts/medir-rendimiento.sh:15-28` y
`auth.service.ts:10-20`.
