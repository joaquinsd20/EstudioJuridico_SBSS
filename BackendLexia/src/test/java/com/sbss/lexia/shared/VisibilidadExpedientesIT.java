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

    // =======================================================================
    // PLAZOS (PR #41). Su filtro era "aplicarPortal": solo estudio y portal,
    // el mismo metodo que actuaciones tenia antes del arreglo. Comprobado por
    // HTTP antes de este cambio: Juan recibia 404 en EXP-0088 y sin embargo
    // veia su plazo, podia elegirlo en /plazos/catalogos, y el listado general
    // le devolvia los plazos de todo el estudio.
    // =======================================================================

    @Test
    @DisplayName("plazos no muestra los de un expediente confidencial ajeno")
    void plazosAcotaPorAsignacion() throws Exception {
        long id = idExpedienteConfidencial();

        mockMvc.perform(get("/api/v1/plazos").param("expedienteId", String.valueOf(id))
                        .with(authentication(como(JUAN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Tampoco puede elegirlo para crearle un plazo nuevo
        mockMvc.perform(get("/api/v1/plazos/catalogos").with(authentication(como(JUAN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expedientes[?(@.codigo == 'EXP-0088')]").isEmpty());
    }

    @Test
    @DisplayName("plazos SI los muestra a su responsable, aunque sean confidenciales")
    void plazosLosVeSuResponsable() throws Exception {
        long id = idExpedienteConfidencial();
        Integer cuantos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM plazos WHERE expediente_id = ? AND activo = 1", Integer.class, id);
        // Si la semilla dejara de traer plazos en EXP-0088, esta prueba pasaria
        // en vacio y no comprobaria nada: mejor que falle y se note.
        org.junit.jupiter.api.Assertions.assertTrue(cuantos != null && cuantos > 0,
                "EXP-0088 debe tener al menos un plazo en la semilla para que esta prueba valga");

        mockMvc.perform(get("/api/v1/plazos").param("expedienteId", String.valueOf(id))
                        .with(authentication(como(MARIA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.expedienteCodigo == 'EXP-0088')]").isNotEmpty());

        mockMvc.perform(get("/api/v1/plazos/catalogos").with(authentication(como(MARIA))))
                .andExpect(jsonPath("$.expedientes[?(@.codigo == 'EXP-0088')]").isNotEmpty());
    }

    // =======================================================================
    // MONITOR JUDICIAL (PR #40). Tenia el bug inverso: aplicaba
    // "es_confidencial = 0" incluso a quien tenia el expediente asignado, asi
    // que Maria no veia su propio caso confidencial en el monitor.
    // =======================================================================

    @Test
    @DisplayName("el monitor deja elegir el confidencial a su responsable, y no a un ajeno")
    void monitorRespetaLaAsignacion() throws Exception {
        mockMvc.perform(get("/api/v1/monitor-judicial/catalogos").with(authentication(como(MARIA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expedientes[?(@.codigoInterno == 'EXP-0088')]").isNotEmpty());

        mockMvc.perform(get("/api/v1/monitor-judicial/catalogos").with(authentication(como(JUAN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expedientes[?(@.codigoInterno == 'EXP-0088')]").isEmpty());
    }

    // =======================================================================
    // FACTURACION (PR #43). Sus 9 endpoints no tenian @PreAuthorize, asi que
    // cualquier usuario autenticado leia los honorarios del estudio.
    // Comprobado antes del arreglo: GET /api/v1/facturacion devolvia 200 a
    // juan.perez. Los permisos FACTURACION_* no existian: los crea V20.
    // =======================================================================

    @Test
    @DisplayName("la facturacion del estudio no la lee un abogado sin permiso")
    void facturacionExigePermiso() throws Exception {
        mockMvc.perform(get("/api/v1/facturacion").with(authentication(como(JUAN))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/facturacion/resumen").with(authentication(como(JUAN))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("V19 creo de verdad las tablas de facturacion (su nombre se saltaba en silencio)")
    void facturacionRespondeAlAdministrador() throws Exception {
        // Esta prueba es el centinela del fallo de nombre: V19 se llamaba
        // "V19_facturacion.sql" con un solo guion bajo, Flyway la saltaba con
        // una linea en INFO y las tablas honorarios y movimientos_financieros
        // nunca se creaban. El modulo no fallaba porque el servicio devolvia
        // lista vacia ante cualquier excepcion. Quitado ese catch, si la
        // migracion se salta otra vez esto revienta en vez de pasar.
        mockMvc.perform(get("/api/v1/facturacion").with(authentication(como(ADMIN))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/facturacion/resumen").with(authentication(como(ADMIN))))
                .andExpect(status().isOk());
    }

    // =======================================================================
    // La cota de los listados (shared/dto/Limites).
    // =======================================================================

    @Test
    @DisplayName("los listados respetan el limite pedido")
    void losListadosSeAcotan() throws Exception {
        mockMvc.perform(get("/api/v1/plazos").param("limite", "2").with(authentication(como(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(2)));
        mockMvc.perform(get("/api/v1/expedientes").param("limite", "3").with(authentication(como(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(3)));
        mockMvc.perform(get("/api/v1/agenda").param("limite", "1").with(authentication(como(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(1)));
        // El historial del monitor crece una fila por revision judicial, y los
        // honorarios con la facturacion: los dos tenian que acotarse tambien.
        mockMvc.perform(get("/api/v1/monitor-judicial").param("limite", "2").with(authentication(como(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(2)));
        mockMvc.perform(get("/api/v1/monitor-judicial/consultas").param("limite", "2")
                        .with(authentication(como(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(2)));
        mockMvc.perform(get("/api/v1/facturacion").param("limite", "2").with(authentication(como(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(2)));
    }
}
