package com.sbss.lexia.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import com.sbss.lexia.seguridad.service.UsuarioDetailsService;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
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

/**
 * Reasignacion del trabajo de un usuario, contra la semilla real.
 *
 * Cada prueba corre dentro de una transaccion que se DESHACE al terminar:
 * MockMvc ejecuta la peticion en el mismo hilo, asi que el servicio se une a
 * ella. Por eso se puede mover el trabajo de Ana Morales de verdad y comprobar
 * el resultado sin dejar la base cambiada.
 *
 *   LEXIA_TEST_DB=1 ./mvnw test -Dtest=ReasignacionTrabajoIT
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "LEXIA_TEST_DB", matches = ".+")
class ReasignacionTrabajoIT {

    private static final String ADMIN = "carlos.mendoza@lexia.com";
    /** 3 expedientes, 2 plazos, 3 tareas, 3 clientes y apoyo en 1 equipo. */
    private static final String ANA = "ana.morales@lexia.com";
    private static final String LUIS = "luis.torres@lexia.com";         // ABOGADO
    private static final String ROSA = "rosa.jimenez@lexia.com";        // ASISTENTE_LEGAL
    private static final String ROBERTO = "roberto.diaz@lexia.com";     // CONTABILIDAD
    private static final String MARIA = "maria.lopez@lexia.com";        // responsable de EXP-0088
    private static final String JUAN = "juan.perez@lexia.com";          // sin relacion con EXP-0088

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

    private String uuid(String email) {
        return jdbc.queryForObject("SELECT uuid_publico FROM usuarios WHERE email = ?", String.class, email);
    }

    private Long id(String email) {
        return jdbc.queryForObject("SELECT id FROM usuarios WHERE email = ?", Long.class, email);
    }

    private String cuerpo(String expedientesA, String tareasA, String clientesA) {
        return "{\"expedientesA\":%s,\"tareasA\":%s,\"clientesA\":%s}".formatted(
                comillas(expedientesA), comillas(tareasA), comillas(clientesA));
    }

    private static String comillas(String valor) {
        return valor == null ? "null" : "\"" + valor + "\"";
    }

    @Test
    @DisplayName("la carga detallada lista lo concreto y quien puede recibirlo")
    void cargaDetallada() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/" + uuid(ANA) + "/carga").with(authentication(como(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expedientes.length()").value(3))
                .andExpect(jsonPath("$.plazos.length()").value(2))
                .andExpect(jsonPath("$.tareas.length()").value(3))
                .andExpect(jsonPath("$.clientes.length()").value(3))
                .andExpect(jsonPath("$.equipos.length()").value(1))
                // Ella no aparece como candidata a recibir su propio trabajo
                .andExpect(jsonPath("$.candidatos[?(@.uuidPublico == '%s')]".formatted(uuid(ANA))).isEmpty())
                // Contabilidad puede recibir tareas, pero no expedientes
                .andExpect(jsonPath("$.candidatos[?(@.uuidPublico == '%s')].esAbogado".formatted(uuid(ROBERTO)))
                        .value(Matchers.contains(false)));
    }

    @Test
    @DisplayName("sin destino para una categoria con trabajo: 400 y no se mueve nada")
    void faltaDestino() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios/" + uuid(ANA) + "/reasignar").with(authentication(como(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(null, uuid(ROSA), uuid(LUIS))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(Matchers.containsString("expedientes")));
    }

    @Test
    @DisplayName("los expedientes no pueden pasar a quien no es abogado")
    void expedientesSoloAAbogados() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios/" + uuid(ANA) + "/reasignar").with(authentication(como(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(uuid(ROBERTO), uuid(ROSA), uuid(LUIS))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(Matchers.containsString("abogado")));
    }

    @Test
    @DisplayName("no se puede reasignar a la misma persona")
    void noASiMisma() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios/" + uuid(ANA) + "/reasignar").with(authentication(como(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(uuid(ANA), uuid(ROSA), uuid(LUIS))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("reasignacion completa: expedientes en la linea de tiempo, plazos y alertas siguen al expediente, y la baja queda permitida")
    void reasignacionCompleta() throws Exception {
        Long ana = id(ANA);
        Long luis = id(LUIS);
        Long rosa = id(ROSA);

        // Una alerta todavia programada sobre un plazo de Ana: debe irse con el plazo
        Long plazoConAlerta = jdbc.queryForObject("""
                SELECT p.id FROM plazos p WHERE p.usuario_responsable_id = ?
                   AND p.estado NOT IN ('CUMPLIDO','CANCELADO') ORDER BY p.id LIMIT 1""", Long.class, ana);
        jdbc.update("""
                INSERT INTO alertas (estudio_id, plazo_id, expediente_id, usuario_destino_id, tipo, mensaje,
                                     fecha_programada, estado)
                SELECT 1, p.id, p.expediente_id, ?, 'PLAZO_PROXIMO', 'Prueba de reasignacion',
                       NOW(3) + INTERVAL 1 DAY, 'PROGRAMADA'
                  FROM plazos p WHERE p.id = ?""", ana, plazoConAlerta);

        List<Long> expedientesDeAna = jdbc.queryForList(
                "SELECT id FROM expedientes WHERE abogado_responsable_id = ? AND activo = 1", Long.class, ana);

        mockMvc.perform(post("/api/v1/usuarios/" + uuid(ANA) + "/reasignar").with(authentication(como(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(uuid(LUIS), uuid(ROSA), uuid(LUIS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expedientes").value(3))
                .andExpect(jsonPath("$.plazos").value(2))
                .andExpect(jsonPath("$.alertas").value(1))
                .andExpect(jsonPath("$.tareas").value(3))
                .andExpect(jsonPath("$.clientes").value(3))
                .andExpect(jsonPath("$.equiposRetirados").value(1));

        // Expedientes: nuevo responsable, equipo coherente y rastro en la linea de tiempo
        for (Long expediente : expedientesDeAna) {
            Map<String, Object> e = jdbc.queryForMap(
                    "SELECT abogado_responsable_id, uuid_publico FROM expedientes WHERE id = ?", expediente);
            assertThat(((Number) e.get("abogado_responsable_id")).longValue()).isEqualTo(luis);

            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM expediente_equipo WHERE expediente_id = ? AND usuario_id = ?
                       AND rol_en_expediente = 'RESPONSABLE' AND activo = 1""", Integer.class, expediente, luis))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM expediente_equipo WHERE expediente_id = ? AND usuario_id = ?
                       AND activo = 1""", Integer.class, expediente, ana))
                    .isZero();

            mockMvc.perform(get("/api/v1/expedientes/" + e.get("uuid_publico") + "/linea-tiempo")
                            .with(authentication(como(ADMIN))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.tipo == 'CAMBIO_RESPONSABLE')]").isNotEmpty());
        }

        // Plazos: cada uno quedo con el responsable de su expediente
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM plazos p JOIN expedientes e ON e.id = p.expediente_id
                 WHERE p.id IN (SELECT registro_id FROM auditoria WHERE entidad = 'plazos'
                                  AND descripcion LIKE 'Plazo reasignado de Ana Morales%')
                   AND p.usuario_responsable_id <> e.abogado_responsable_id""", Integer.class))
                .isZero();

        // La alerta programada ahora le llega al nuevo responsable del plazo
        Long destinoAlerta = jdbc.queryForObject(
                "SELECT usuario_destino_id FROM alertas WHERE plazo_id = ? AND estado = 'PROGRAMADA'"
                        + " AND mensaje = 'Prueba de reasignacion'", Long.class, plazoConAlerta);
        Long responsablePlazo = jdbc.queryForObject(
                "SELECT usuario_responsable_id FROM plazos WHERE id = ?", Long.class, plazoConAlerta);
        assertThat(destinoAlerta).isEqualTo(responsablePlazo).isNotEqualTo(ana);

        // Tareas a Rosa, clientes a Luis
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM tareas WHERE usuario_responsable_id = ?
                   AND estado NOT IN ('COMPLETADA','CANCELADA')""", Integer.class, rosa))
                .isGreaterThanOrEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM clientes WHERE abogado_responsable_id = ? AND activo = 1", Integer.class, ana))
                .isZero();

        // Sin carga: la baja, antes bloqueada, ahora se permite
        mockMvc.perform(get("/api/v1/usuarios/" + uuid(ANA)).with(authentication(como(ADMIN))))
                .andExpect(jsonPath("$.cargaTrabajo.expedientes").value(0))
                .andExpect(jsonPath("$.cargaTrabajo.plazosAbiertos").value(0))
                .andExpect(jsonPath("$.cargaTrabajo.tareasAbiertas").value(0))
                .andExpect(jsonPath("$.cargaTrabajo.clientes").value(0))
                .andExpect(jsonPath("$.cargaTrabajo.equiposApoyo").value(0));

        mockMvc.perform(post("/api/v1/usuarios/" + uuid(ANA) + "/desactivar").with(authentication(como(ADMIN))))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("la baja tambien se bloquea por clientes a su nombre")
    void clientesBloqueanBaja() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios/" + uuid(ANA) + "/desactivar").with(authentication(como(ADMIN))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(Matchers.containsString("cliente")));
    }

    @Test
    @DisplayName("RF-02: el responsable ve su expediente confidencial; quien no esta asignado, no")
    void confidencialVisibleParaSuResponsable() throws Exception {
        String exp0088 = jdbc.queryForObject(
                "SELECT uuid_publico FROM expedientes WHERE codigo_interno = 'EXP-0088'", String.class);

        mockMvc.perform(get("/api/v1/expedientes/" + exp0088).with(authentication(como(MARIA))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/expedientes").with(authentication(como(MARIA))))
                .andExpect(jsonPath("$.contenido[?(@.uuid == '%s' || @.uuidPublico == '%s')]".formatted(exp0088, exp0088))
                        .isNotEmpty());

        mockMvc.perform(get("/api/v1/expedientes/" + exp0088).with(authentication(como(JUAN))))
                .andExpect(status().isNotFound());
    }
}
