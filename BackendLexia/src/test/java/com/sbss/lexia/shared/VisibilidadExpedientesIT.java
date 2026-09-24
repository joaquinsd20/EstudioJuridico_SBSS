package com.sbss.lexia.shared;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import com.sbss.lexia.seguridad.service.UsuarioDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La regla de RF-02, comprobada en los cuatro modulos que la usan.
 *
 * Existe porque la regla estaba escrita cuatro veces y dos copias estaban mal:
 * actuaciones y documental no filtraban por asignacion, asi que lo que
 * expedientes ocultaba, ellos lo ensenaban. Verificado antes del arreglo: Juan
 * Perez recibia 404 al abrir EXP-0088 (confidencial, de Maria) y sin embargo
 * veia su actuacion y podia elegir ese expediente para registrarle otra.
 *
 * EXP-0088 de la semilla es el caso: confidencial, con Maria Lopez de
 * responsable (ABOGADO_AUXILIAR, sin EXPEDIENTE_CONFIDENCIAL) y Sofia de
 * supervisora.
 *
 *   LEXIA_TEST_DB=1 ./mvnw test -Dtest=VisibilidadExpedientesIT
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "LEXIA_TEST_DB", matches = ".+")
class VisibilidadExpedientesIT {

    /** Responsable del expediente confidencial, SIN el permiso de confidenciales. */
    private static final String MARIA = "maria.lopez@lexia.com";
    /** Abogado sin relacion con ese expediente. */
    private static final String JUAN = "juan.perez@lexia.com";
    private static final String ADMIN = "carlos.mendoza@lexia.com";

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

    private long idExpedienteConfidencial() {
        return jdbc.queryForObject(
                "SELECT id FROM expedientes WHERE codigo_interno = 'EXP-0088'", Long.class);
    }

    private String uuidExpedienteConfidencial() {
        return jdbc.queryForObject(
                "SELECT uuid_publico FROM expedientes WHERE codigo_interno = 'EXP-0088'", String.class);
    }

    @Test
    @DisplayName("quien no esta asignado no ve el expediente confidencial ni por expedientes ni por actuaciones")
    void ajenoNoVeNada() throws Exception {
        long id = idExpedienteConfidencial();

        mockMvc.perform(get("/api/v1/expedientes/" + uuidExpedienteConfidencial()).with(authentication(como(JUAN))))
                .andExpect(status().isNotFound());

        // Antes del arreglo, esto devolvia la actuacion del expediente ajeno
        mockMvc.perform(get("/api/v1/actuaciones").param("expedienteId", String.valueOf(id))
                        .with(authentication(como(JUAN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Y tampoco puede elegirlo para registrarle una actuacion nueva
        mockMvc.perform(get("/api/v1/actuaciones/catalogos").with(authentication(como(JUAN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expedientes[?(@.codigo == 'EXP-0088')]").isEmpty());
    }

    @Test
    @DisplayName("su responsable SI lo ve, aunque no tenga el permiso de confidenciales (RF-02)")
    void asignadoSiVe() throws Exception {
        long id = idExpedienteConfidencial();

        mockMvc.perform(get("/api/v1/expedientes/" + uuidExpedienteConfidencial()).with(authentication(como(MARIA))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/actuaciones").param("expedienteId", String.valueOf(id))
                        .with(authentication(como(MARIA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/actuaciones/catalogos").with(authentication(como(MARIA))))
                .andExpect(jsonPath("$.expedientes[?(@.codigo == 'EXP-0088')]").isNotEmpty());
    }

    @Test
    @DisplayName("el Administrador ve todo el estudio")
    void administradorVeTodo() throws Exception {
        long id = idExpedienteConfidencial();

        mockMvc.perform(get("/api/v1/actuaciones").param("expedienteId", String.valueOf(id))
                        .with(authentication(como(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("documental tampoco muestra documentos de expedientes ajenos")
    void documentalAcotaPorAsignacion() throws Exception {
        Integer documentosAjenos = jdbc.queryForObject("""
                SELECT COUNT(*) FROM documentos d JOIN expedientes e ON e.id = d.expediente_id
                 WHERE e.codigo_interno = 'EXP-0088' AND d.activo = 1 AND d.en_papelera = 0""",
                Integer.class);

        // El listado de Juan no puede contener ninguno de ellos
        // Se compara por expedienteId: `expedienteCodigo` de documental no es
        // "EXP-0088" sino el correlativo con el anio.
        mockMvc.perform(get("/api/v1/documentos").param("limite", "200").with(authentication(como(JUAN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.expedienteId == " + idExpedienteConfidencial() + ")]").isEmpty());

        // Y si la semilla trae alguno, Maria (su responsable) si debe verlos
        if (documentosAjenos != null && documentosAjenos > 0) {
            mockMvc.perform(get("/api/v1/documentos").param("limite", "200").with(authentication(como(MARIA))))
                    .andExpect(jsonPath("$[?(@.expedienteId == " + idExpedienteConfidencial() + ")]").isNotEmpty());
        }
    }
}
