package com.sbss.lexia.busqueda;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import com.sbss.lexia.seguridad.service.UsuarioDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * El buscador global y la regla de RF-02.
 *
 * Verificado contra el backend real el 2026-10-10, antes del arreglo: Juan
 * Perez, abogado sin relacion con EXP-0088 (confidencial, de Maria), lo
 * encontraba por la barra como expediente, como actuacion y por su documento,
 * y buscando "Resolucion" veia las resoluciones de todo el estudio, mientras
 * GET /expedientes no le mostraba EXP-0088. Las consultas escribian a mano
 * `e.estudio_id = :visEstudioId`: el nombre del parametro del helper, sin la
 * regla.
 *
 * Los textos son PREFIJOS a proposito: expedientes, resoluciones y actuaciones
 * buscan con `LIKE 'texto%'`, y una sonda con texto del medio sale vacia y
 * "pasa" sin probar nada. Cada caso negativo tiene su positivo (Maria o el
 * administrador SI lo encuentran) para que un buscador roto no parezca seguro.
 *
 *   LEXIA_TEST_DB=1 ./mvnw test -Dtest=BusquedaVisibilidadIT
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "LEXIA_TEST_DB", matches = ".+")
class BusquedaVisibilidadIT {

    /** Responsable de EXP-0088, sin el permiso de confidenciales. */
    private static final String MARIA = "maria.lopez@lexia.com";
    /** Abogado sin relacion con EXP-0088. */
    private static final String JUAN = "juan.perez@lexia.com";
    private static final String ADMIN = "carlos.mendoza@lexia.com";
    /** CONTABILIDAD: tiene EXPEDIENTE_LEER, pero no ACTUACION_LEER ni DOCUMENTO_LEER. */
    private static final String ROBERTO = "roberto.diaz@lexia.com";

    private static final String CONFIDENCIAL = "EXP-0088";
    /** Lo que cuelga de EXP-0088 en la semilla, cada uno buscable por prefijo. */
    private static final String SU_RESOLUCION = "Resolucion N.o 0002";
    private static final String SU_ACTUACION = "Notificacion de admision";
    private static final String SU_DOCUMENTO = "Resolucion N.o 0002 - Amparo";
    private static final String SU_CONTRAPARTE = "Ministerio de Salud";
    /** Cliente con dos expedientes: EXP-0088 (confidencial) y otro. */
    private static final String SU_CLIENTE = "Lopez Ramos Juan Carlos";

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

    private ResultActions buscar(String usuario, String texto) throws Exception {
        return mockMvc.perform(get("/api/v1/busqueda").param("texto", texto).with(authentication(como(usuario))))
                .andExpect(status().isOk());
    }

    /** Codigos de expediente de un grupo del buscador. */
    private static String codigosDe(String tipo) {
        return "$.grupos[?(@.tipo == '" + tipo + "')].items[*].expedienteCodigo";
    }

    @Test
    @DisplayName("quien no esta asignado no encuentra el confidencial por ninguna via")
    void ajenoNoEncuentraElConfidencial() throws Exception {
        for (String texto : new String[] {CONFIDENCIAL, SU_RESOLUCION, SU_ACTUACION, SU_DOCUMENTO}) {
            buscar(JUAN, texto)
                    .andExpect(jsonPath("$.grupos[*].items[*].expedienteCodigo", not(hasItem(CONFIDENCIAL))))
                    .andExpect(jsonPath("$.grupos[*].items[*].titulo", not(hasItem(CONFIDENCIAL))));
        }
    }

    @Test
    @DisplayName("su responsable y el administrador SI lo encuentran, por cada via")
    void asignadoYAdministradorLoEncuentran() throws Exception {
        for (String usuario : new String[] {MARIA, ADMIN}) {
            buscar(usuario, CONFIDENCIAL).andExpect(jsonPath(codigosDe("EXPEDIENTE"), hasItem(CONFIDENCIAL)));
            buscar(usuario, SU_RESOLUCION).andExpect(jsonPath(codigosDe("RESOLUCION"), hasItem(CONFIDENCIAL)));
            buscar(usuario, SU_ACTUACION).andExpect(jsonPath(codigosDe("ACTUACION"), hasItem(CONFIDENCIAL)));
            buscar(usuario, SU_DOCUMENTO).andExpect(jsonPath(codigosDe("DOCUMENTO"), hasItem(CONFIDENCIAL)));
        }
    }

    @Test
    @DisplayName("la contraparte se encuentra (conflicto de intereses) pero sin el expediente ajeno")
    void laContraparteSaleSinElExpedienteAjeno() throws Exception {
        buscar(JUAN, SU_CONTRAPARTE)
                .andExpect(jsonPath("$.grupos[?(@.tipo == 'CONTRAPARTE')].items[*].titulo", hasItem(SU_CONTRAPARTE)))
                .andExpect(jsonPath(codigosDe("CONTRAPARTE"), not(hasItem(CONFIDENCIAL))));

        // Una fila por contraparte: el LEFT JOIN anterior la repetia por expediente
        buscar(ADMIN, SU_CONTRAPARTE)
                .andExpect(jsonPath(codigosDe("CONTRAPARTE"), hasItem(CONFIDENCIAL)))
                .andExpect(jsonPath("$.grupos[?(@.tipo == 'CONTRAPARTE')].items[?(@.titulo == '"
                        + SU_CONTRAPARTE + "')]", hasSize(1)));
    }

    @Test
    @DisplayName("el contador de expedientes de un cliente no cuenta los confidenciales ajenos")
    void elClienteNoCuentaLoQueNoSeVe() throws Exception {
        String detalles = "$.grupos[?(@.tipo == 'CLIENTE')].items[*].detalle";
        buscar(ADMIN, SU_CLIENTE).andExpect(jsonPath(detalles, hasItem(containsString("2 expediente(s)"))));
        buscar(JUAN, SU_CLIENTE).andExpect(jsonPath(detalles, not(hasItem(containsString("2 expediente(s)")))));
    }

    @Test
    @DisplayName("abogados: sin cuentas del portal, sin DNI del personal y sin contar lo que no se ve")
    void abogadosSoloLoQueCorresponde() throws Exception {
        String titulos = "$.grupos[?(@.tipo == 'ABOGADO')].items[*].titulo";

        // El nombre existe dos veces en `usuarios`: la abogada (INTERNO) y la
        // cuenta del portal del cliente (CLIENTE). Solo la primera es abogado.
        buscar(ADMIN, "Ana Maria Fernandez")
                .andExpect(jsonPath("$.grupos[?(@.tipo == 'ABOGADO')].items[*]", hasSize(1)));

        String dniMaria = jdbc.queryForObject(
                "SELECT numero_documento FROM usuarios WHERE email = ?", String.class, MARIA);
        buscar(ADMIN, dniMaria).andExpect(jsonPath(titulos, hasItem(containsString("Maria Lopez"))));
        buscar(JUAN, dniMaria).andExpect(jsonPath(titulos, not(hasItem(containsString("Maria Lopez")))));

        // Maria solo es responsable de EXP-0088, que Juan no ve
        String detalles = "$.grupos[?(@.tipo == 'ABOGADO')].items[*].detalle";
        buscar(ADMIN, "Maria Lopez").andExpect(jsonPath(detalles, hasItem(containsString("1 expediente(s)"))));
        buscar(JUAN, "Maria Lopez").andExpect(jsonPath(detalles, hasItem(containsString("0 expediente(s)"))));
    }

    @Test
    @DisplayName("un documento en la papelera no aparece en el buscador")
    void laPapeleraNoSale() throws Exception {
        buscar(ADMIN, SU_DOCUMENTO).andExpect(jsonPath(codigosDe("DOCUMENTO"), hasItem(CONFIDENCIAL)));

        jdbc.update("UPDATE documentos SET en_papelera = 1 WHERE nombre = ?", SU_DOCUMENTO);

        buscar(ADMIN, SU_DOCUMENTO).andExpect(jsonPath(codigosDe("DOCUMENTO"), not(hasItem(CONFIDENCIAL))));
    }

    @Test
    @DisplayName("sin el permiso del modulo dueno, ese tipo ni se consulta (aunque el expediente sea visible)")
    void sinPermisoDelModuloNoHayResultados() throws Exception {
        // Roberto pasa a ver EXP-0088 por asignacion, asi que la regla de
        // visibilidad ya no lo frena: lo que debe frenarlo es el permiso.
        jdbc.update("""
                INSERT INTO expediente_equipo (expediente_id, usuario_id, rol_en_expediente, puede_editar)
                SELECT e.id, u.id, 'OBSERVADOR', 0
                  FROM expedientes e, usuarios u
                 WHERE e.codigo_interno = ? AND u.email = ?
                """, CONFIDENCIAL, ROBERTO);

        // Control: la asignacion funciona y tiene EXPEDIENTE_LEER
        buscar(ROBERTO, CONFIDENCIAL).andExpect(jsonPath(codigosDe("EXPEDIENTE"), hasItem(CONFIDENCIAL)));

        buscar(ROBERTO, SU_ACTUACION).andExpect(jsonPath("$.grupos[?(@.tipo == 'ACTUACION')]", hasSize(0)));
        buscar(ROBERTO, SU_DOCUMENTO).andExpect(jsonPath("$.grupos[?(@.tipo == 'DOCUMENTO')]", hasSize(0)));

        mockMvc.perform(get("/api/v1/busqueda/tipo").param("texto", SU_ACTUACION).param("tipo", "ACTUACION")
                        .with(authentication(como(ROBERTO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
