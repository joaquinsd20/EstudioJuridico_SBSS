package com.sbss.lexia.tareas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import com.sbss.lexia.seguridad.service.UsuarioDetailsService;

/**
 * La regla de RF-02 en tareas (panel de tareas, 2026-09-26).
 *
 * El modulo acotaba solo por `e.estudio_id`, asi que cualquiera del estudio
 * veia las tareas de expedientes confidenciales ajenos y podia crearles tareas.
 * Es la cuarta vez que la regla se pierde en un modulo nuevo: antes en
 * actuaciones, documental y plazos.
 *
 *   LEXIA_TEST_DB=1 ./mvnw test -Dtest=VisibilidadTareasIT
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "LEXIA_TEST_DB", matches = ".+")
class VisibilidadTareasIT {

    private static final String ADMIN = "carlos.mendoza@lexia.com";
    /** Responsable de EXP-0088, que es confidencial. Sin TAREA_APROBAR. */
    private static final String MARIA = "maria.lopez@lexia.com";
    /** Sin relacion con EXP-0088. */
    private static final String JUAN = "juan.perez@lexia.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UsuarioDetailsService usuarioDetailsService;

    private Authentication como(String email) {
        UsuarioPrincipal principal = usuarioDetailsService.loadUserByUsername(email);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private String tareaDelConfidencial() {
        return jdbc.queryForObject("""
                SELECT t.uuid_publico FROM tareas t JOIN expedientes e ON e.id = t.expediente_id
                 WHERE e.codigo_interno = 'EXP-0088' AND t.activo = 1
                 ORDER BY t.id LIMIT 1""", String.class);
    }

    private Long idExpedienteConfidencial() {
        return jdbc.queryForObject(
                "SELECT id FROM expedientes WHERE codigo_interno = 'EXP-0088'", Long.class);
    }

    @Test
    @DisplayName("quien no esta asignado no ve las tareas del expediente confidencial")
    void elAjenoNoLasVe() throws Exception {
        // Si EXP-0088 dejara de tener tareas, lo de abajo pasaria en vacio.
        Integer cuantas = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tareas t JOIN expedientes e ON e.id = t.expediente_id
                 WHERE e.codigo_interno = 'EXP-0088' AND t.activo = 1""", Integer.class);
        assertTrue(cuantas != null && cuantas > 0, "EXP-0088 debe tener tareas para que esto pruebe algo");

        String uuid = tareaDelConfidencial();

        mockMvc.perform(get("/api/v1/tareas/" + uuid).with(authentication(como(JUAN))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/tareas").param("size", "200").with(authentication(como(JUAN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[?(@.expedienteCodigo == 'EXP-0088')]").isEmpty());

        // Ni puede elegirlo en el catalogo para crearle una
        mockMvc.perform(get("/api/v1/tareas/catalogos").with(authentication(como(JUAN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expedientes[?(@.codigo == 'EXP-0088')]").isEmpty());
    }

    @Test
    @DisplayName("su responsable SI las ve, aunque el expediente sea confidencial (RF-02)")
    void elResponsableSiLasVe() throws Exception {
        mockMvc.perform(get("/api/v1/tareas/" + tareaDelConfidencial()).with(authentication(como(MARIA))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tareas").param("size", "200").with(authentication(como(MARIA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[?(@.expedienteCodigo == 'EXP-0088')]").isNotEmpty());

        mockMvc.perform(get("/api/v1/tareas/catalogos").with(authentication(como(MARIA))))
                .andExpect(jsonPath("$.expedientes[?(@.codigo == 'EXP-0088')]").isNotEmpty());
    }

    @Test
    @DisplayName("no se crea una tarea sobre un expediente que no se ve")
    void noSeCreaEnExpedienteAjeno() throws Exception {
        String cuerpo = """
                {"titulo":"intento","expedienteId":%d,"responsableId":%d,
                 "prioridad":"BAJA","fechaLimite":"2026-12-01T12:00:00","subtareas":[]}"""
                .formatted(idExpedienteConfidencial(),
                        jdbc.queryForObject("SELECT id FROM usuarios WHERE email = ?", Long.class, JUAN));

        mockMvc.perform(post("/api/v1/tareas").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo).with(authentication(como(JUAN))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("el resumen de cada uno cuenta solo lo suyo")
    void elResumenNoFiltraMenosQueElListado() throws Exception {
        // Si el resumen contara sobre todo el estudio y el listado solo sobre lo
        // visible, los KPI de la pantalla no cuadrarian con las tarjetas.
        String resumenJuan = mockMvc.perform(get("/api/v1/tareas/resumen")
                        .with(authentication(como(JUAN))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String resumenAdmin = mockMvc.perform(get("/api/v1/tareas/resumen")
                        .with(authentication(como(ADMIN))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        long totalJuan = Long.parseLong(resumenJuan.replaceAll(".*\"total\"\\s*:\\s*(\\d+).*", "$1"));
        long totalAdmin = Long.parseLong(resumenAdmin.replaceAll(".*\"total\"\\s*:\\s*(\\d+).*", "$1"));
        assertTrue(totalJuan < totalAdmin,
                "el administrador ve mas tareas que un abogado; si son iguales, el resumen no filtra");

        String listado = mockMvc.perform(get("/api/v1/tareas").param("size", "500")
                        .with(authentication(como(JUAN))))
                .andReturn().getResponse().getContentAsString();
        long totalListado = Long.parseLong(
                listado.replaceAll(".*\"totalElementos\"\\s*:\\s*(\\d+).*", "$1"));
        assertEquals(totalJuan, totalListado, "el resumen y el listado deben contar lo mismo");
    }

    @Test
    @DisplayName("marcar vencidas no borra una aprobacion")
    void vencerNoPisaLoAprobado() throws Exception {
        // marcarVencidas() corre en cada lectura. Antes incluia APROBADA y
        // PRESENTADA, asi que una tarea aprobada que pasaba de fecha volvia a
        // VENCIDA y salia de la columna Completado.
        Long id = jdbc.queryForObject("""
                SELECT t.id FROM tareas t JOIN expedientes e ON e.id = t.expediente_id
                 WHERE t.activo = 1 ORDER BY t.id LIMIT 1""", Long.class);
        jdbc.update("""
                UPDATE tareas SET estado = 'APROBADA', etapa_flujo = 'APROBAR',
                       fecha_limite = DATE_SUB(NOW(3), INTERVAL 30 DAY)
                 WHERE id = ?""", id);

        // Cualquier lectura dispara el barrido
        mockMvc.perform(get("/api/v1/tareas/resumen").with(authentication(como(ADMIN))))
                .andExpect(status().isOk());

        String estado = jdbc.queryForObject("SELECT estado FROM tareas WHERE id = ?", String.class, id);
        assertEquals("APROBADA", estado, "una tarea aprobada no debe volver a VENCIDA por la fecha");
    }
}
