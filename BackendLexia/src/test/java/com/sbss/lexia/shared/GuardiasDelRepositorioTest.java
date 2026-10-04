package com.sbss.lexia.shared;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guardias que leen ARCHIVOS, no la base de datos: corren con un simple
 * {@code ./mvnw test}, sin {@code LEXIA_TEST_DB}.
 *
 * Existen porque no hay CI ni hooks, y en tres semanas el equipo repitio los
 * mismos fallos, todos detectables leyendo el repositorio:
 *
 *  - migraciones con la misma version: V12 (PR #33), V15 y V20 (panel de tareas);
 *  - V19 con UN guion bajo, que Flyway se salta en silencio y dejo un modulo
 *    entero sin tablas;
 *  - V10 y luego V16 y V18 EDITADAS despues de aplicarse, que rompe el arranque
 *    de quien ya las tiene con "Migration checksum mismatch";
 *  - la contrasena de MySQL escrita en application-dev.yml, dos veces;
 *  - la regla de visibilidad de expedientes (RF-02) olvidada en cinco modulos.
 *
 * Una regla se incumple todavia hoy: la visibilidad a mano en la busqueda
 * global. Se trata como un TRINQUETE: ese caso queda en una lista de
 * conocidos, de modo que NO puede aparecer ninguno nuevo, y la propia prueba
 * obliga a vaciar la lista en cuanto se arregle.
 */
class GuardiasDelRepositorioTest {

    /** Maven ejecuta las pruebas con el directorio del modulo (BackendLexia) como actual. */
    private static final Path MODULO = Path.of("").toAbsolutePath();
    private static final Path MIGRACIONES = MODULO.resolve("src/main/resources/db/migration");
    private static final Path RECURSOS = MODULO.resolve("src/main/resources");
    private static final Path FUENTES = MODULO.resolve("src/main/java/com/sbss/lexia");
    private static final Path MANIFIESTO = MODULO.resolve("src/test/resources/migraciones.sha256");

    /**
     * Controllers cuyas rutas no llevan @PreAuthorize a proposito.
     * auth: login, /yo y cambiar-password tienen que funcionar con la sesion a medio
     * habilitar. notificaciones: la bandeja es de cada usuario y toda consulta lleva
     * su usuario_id.
     */
    private static final Set<String> SIN_PREAUTHORIZE_A_PROPOSITO = Set.of(
            "seguridad/controller/AuthController.java",
            "notificaciones/controller/NotificacionController.java");

    /**
     * TRINQUETE 1. Repositorios que todavia escriben a mano el filtro de
     * visibilidad con un parametro :vis*. Quitar de aqui cuando se arreglen.
     * Arreglo: concatenar VisibilidadExpedientes.sql("e") en vez del texto a mano.
     */
    private static final Set<String> VISIBILIDAD_A_MANO_CONOCIDA = Set.of(
            "busqueda/repository/BusquedaRepositoryJdbc.java");

    // =======================================================================
    // MIGRACIONES
    // =======================================================================

    @Test
    @DisplayName("toda migracion se llama V<n>__descripcion.sql, con DOS guiones bajo")
    void lasMigracionesSiguenLaConvencionDeNombre() throws IOException {
        Pattern valido = Pattern.compile("^(V\\d+__\\S.*|R__\\S.*)\\.sql$");
        List<String> mal = new ArrayList<>();
        for (Path p : archivos(MIGRACIONES)) {
            String nombre = p.getFileName().toString();
            if (nombre.equals("LEEME.md")) {
                continue;
            }
            if (!valido.matcher(nombre).matches()) {
                mal.add(nombre);
            }
        }
        assertTrue(mal.isEmpty(), "Nombre invalido en db/migration: " + mal + ". Con un solo guion bajo "
                + "Flyway NO falla: la salta, y el modulo queda sin tablas. Ver db/migration/LEEME.md");
    }

    @Test
    @DisplayName("no hay dos migraciones con la misma version")
    void noHayVersionesDuplicadas() throws IOException {
        Pattern version = Pattern.compile("^V(\\d+)__.*\\.sql$");
        Map<Integer, List<String>> porVersion = new TreeMap<>();
        for (Path p : archivos(MIGRACIONES)) {
            Matcher m = version.matcher(p.getFileName().toString());
            if (m.matches()) {
                porVersion.computeIfAbsent(Integer.valueOf(m.group(1)), k -> new ArrayList<>())
                        .add(p.getFileName().toString());
            }
        }
        porVersion.values().removeIf(l -> l.size() < 2);
        assertTrue(porVersion.isEmpty(), "Versiones duplicadas: " + porVersion + ". Git no lo detecta "
                + "(son archivos distintos) y Flyway aborta el arranque de todo el equipo: "
                + "'Found more than one migration with version N'. Avisar el numero en el grupo.");
    }

    @Test
    @DisplayName("ninguna migracion fija la base con USE ni la borra con DROP DATABASE")
    void ningunaMigracionFijaOBorraLaBase() throws IOException {
        Pattern peligro = Pattern.compile("(?i)^\\s*(USE\\s+\\S+|DROP\\s+DATABASE\\b)");
        List<String> culpables = new ArrayList<>();
        for (Path p : archivos(MIGRACIONES)) {
            if (!p.getFileName().toString().endsWith(".sql")) {
                continue;
            }
            for (String linea : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                if (!linea.stripLeading().startsWith("--") && peligro.matcher(linea).find()) {
                    culpables.add(p.getFileName() + ": " + linea.strip());
                }
            }
        }
        assertTrue(culpables.isEmpty(), "La base la elige la conexion (DB_NAME / LEXIA_DB_NAME). "
                + "Un USE fijo escribe en lexia_db aunque Flyway apunte a otra: " + culpables);
    }

    @Test
    @DisplayName("una migracion ya publicada no se edita (su huella coincide con el manifiesto)")
    void lasMigracionesPublicadasNoCambian() throws Exception {
        Set<String> cambiadas = new java.util.TreeSet<>();
        List<String> borradas = new ArrayList<>();

        for (String linea : Files.readAllLines(MANIFIESTO, StandardCharsets.UTF_8)) {
            if (linea.isBlank()) {
                continue;
            }
            String huella = linea.substring(0, linea.indexOf(' '));
            String nombre = linea.substring(linea.lastIndexOf(' ') + 1);
            Path archivo = MIGRACIONES.resolve(nombre);
            if (!Files.exists(archivo)) {
                borradas.add(nombre);
            } else if (!huella.equals(huellaDe(archivo))) {
                cambiadas.add(nombre);
            }
        }

        assertTrue(borradas.isEmpty(), "Migraciones publicadas que ya no existen: " + borradas);

        assertTrue(cambiadas.isEmpty(), "Migraciones editadas DESPUES de publicarse: " + cambiadas + ". Regla 3 de "
                + "CLAUDE.md: Flyway guarda su checksum y aborta con 'Migration checksum mismatch' a quien "
                + "ya las tenga. Restaura el archivo y pon el cambio en una V<n+1> NUEVA "
                + "(un indice tambien es un cambio).");
    }

    // =======================================================================
    // CONFIGURACION
    // =======================================================================

    @Test
    @DisplayName("ninguna contrasena ni secreto esta escrito en los application*.yml")
    void lasCredencialesVienenDelEntorno() throws IOException {
        Pattern secreto = Pattern.compile("^\\s*(password|admin-password|secreto|secret):\\s*(.*?)\\s*(#.*)?$");
        List<String> culpables = new ArrayList<>();
        for (Path p : archivos(RECURSOS)) {
            String nombre = p.getFileName().toString();
            if (!nombre.startsWith("application") || !nombre.endsWith(".yml")) {
                continue;
            }
            for (String linea : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                Matcher m = secreto.matcher(linea);
                if (m.matches()) {
                    String valor = m.group(2).replace("\"", "").replace("'", "");
                    if (!valor.isEmpty() && !valor.contains("${")) {
                        culpables.add(nombre + " -> " + m.group(1) + ": <valor escrito en el archivo>");
                    }
                }
            }
        }
        assertTrue(culpables.isEmpty(), "Credenciales fijas en el yml: " + culpables + ". Va como "
                + "${DB_PASSWORD}. Una vez en el historial de git no se puede sacar: hay que CAMBIAR "
                + "la contrasena, no solo revertir el archivo.");
    }

    // =======================================================================
    // SEGURIDAD
    // =======================================================================

    @Test
    @DisplayName("todo endpoint lleva @PreAuthorize (salvo auth y notificaciones, a proposito)")
    void todoEndpointTienePermiso() throws IOException {
        Pattern ruta = Pattern.compile("@(Get|Post|Put|Patch|Delete)Mapping");
        List<String> culpables = new ArrayList<>();
        for (Path p : archivos(FUENTES)) {
            if (!p.getFileName().toString().endsWith("Controller.java")) {
                continue;
            }
            String relativa = relativa(p);
            if (SIN_PREAUTHORIZE_A_PROPOSITO.contains(relativa)) {
                continue;
            }
            String codigo = sinComentarios(Files.readString(p, StandardCharsets.UTF_8));
            long rutas = ruta.matcher(codigo).results().count();
            long permisos = Pattern.compile("@PreAuthorize").matcher(codigo).results().count();
            int cabeceraClase = codigo.indexOf("public class");
            boolean permisoDeClase = cabeceraClase > 0
                    && codigo.substring(0, cabeceraClase).contains("@PreAuthorize");
            if (!permisoDeClase && rutas != permisos) {
                culpables.add(relativa + " (" + rutas + " rutas, " + permisos + " @PreAuthorize)");
            }
        }
        assertTrue(culpables.isEmpty(), "Endpoints sin @PreAuthorize: " + culpables + ". Hasta V20 los 9 de "
                + "facturacion estaban abiertos a cualquier usuario autenticado. Si es a proposito, "
                + "agregalo a SIN_PREAUTHORIZE_A_PROPOSITO con el motivo.");
    }

    @Test
    @DisplayName("ningun repositorio nuevo escribe a mano el filtro de visibilidad (:vis*)")
    void nadieEscribeALaManoLaVisibilidad() throws IOException {
        Set<String> infractores = repositoriosConVisibilidadAMano();

        Set<String> nuevos = new java.util.TreeSet<>(infractores);
        nuevos.removeAll(VISIBILIDAD_A_MANO_CONOCIDA);
        assertTrue(nuevos.isEmpty(), "SQL con un parametro :vis* escrito a mano en " + nuevos + ". Eso copia el "
                + "NOMBRE del parametro del helper pero no la REGLA: filtra solo por estudio. Usa "
                + "VisibilidadExpedientes.sql(\"e\"). Es la quinta vez que se pierde RF-02 y la ultima "
                + "venia camuflada: el repositorio importaba el helper, asi que un grep no lo veia.");

        Set<String> yaArreglados = new java.util.TreeSet<>(VISIBILIDAD_A_MANO_CONOCIDA);
        yaArreglados.removeAll(infractores);
        assertTrue(yaArreglados.isEmpty(), "Ya se arreglo " + yaArreglados + ": quitalo de "
                + "VISIBILIDAD_A_MANO_CONOCIDA para que el trinquete no se afloje.");
    }

    // =======================================================================
    // ayudantes
    // =======================================================================

    private static Set<String> repositoriosConVisibilidadAMano() throws IOException {
        Pattern aMano = Pattern.compile(":vis[A-Z]\\w*");
        Set<String> infractores = new java.util.TreeSet<>();
        for (Path p : archivos(FUENTES)) {
            String relativa = relativa(p);
            if (!relativa.endsWith(".java") || relativa.equals("shared/security/VisibilidadExpedientes.java")) {
                continue;
            }
            String codigo = sinComentarios(Files.readString(p, StandardCharsets.UTF_8));
            if (aMano.matcher(codigo).find()) {
                infractores.add(relativa);
            }
        }
        return infractores;
    }

    /** Huella SHA-256 con fin de linea LF, igual que scripts/congelar-migraciones.sh. */
    private static String huellaDe(Path archivo) throws Exception {
        String texto = Files.readString(archivo, StandardCharsets.UTF_8).replace("\r", "");
        byte[] resumen = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(resumen);
    }

    /** Quita los comentarios para no contar lo que un javadoc MENCIONA como si fuera codigo. */
    private static String sinComentarios(String fuente) {
        return fuente.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static String relativa(Path archivo) {
        return FUENTES.relativize(archivo).toString().replace('\\', '/');
    }

    private static List<Path> archivos(Path raiz) throws IOException {
        assertTrue(Files.isDirectory(raiz), "No existe " + raiz + ". Las pruebas deben correr desde el "
                + "directorio BackendLexia (./mvnw test).");
        try (Stream<Path> s = Files.walk(raiz)) {
            return s.filter(Files::isRegularFile).sorted().toList();
        }
    }
}
