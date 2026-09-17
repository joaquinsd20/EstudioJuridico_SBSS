package com.sbss.lexia.documental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Spring Boot 4 trae Jackson 3, cuyo paquete raiz es tools.jackson y no
// com.fasterxml.jackson. Es la misma trampa que ya documenta el pom.xml para
// el serializador de jjwt.
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import com.sbss.lexia.seguridad.service.UsuarioDetailsService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * El portal del cliente solo puede ver los documentos de SUS expedientes.
 *
 * Antes de este acotado, el rol CLIENTE tenia DOCUMENTO_LEER y DESCARGAR a
 * proposito (RF-16) pero nada limitaba QUE documentos: veia los de todos los
 * expedientes del estudio. El permiso no alcanza para esto — hace falta
 * filtrar por usuarios.cliente_id -> expedientes.cliente_id.
 *
 * Necesita el MySQL local con la semilla cargada:
 *   LEXIA_TEST_DB=1 ./mvnw test -Dtest=VisibilidadDocumentosIT
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "LEXIA_TEST_DB", matches = ".+")
class VisibilidadDocumentosIT {

    /** Usuario 7, ADMINISTRADOR interno: ve todo lo del estudio. */
    private static final String ADMINISTRADOR = "carlos.mendoza@lexia.com";

    /** Usuario 11, tipo_usuario = CLIENTE, ligado al cliente 1 de la semilla. */
    private static final String PORTAL_CLIENTE = "anamaria.fsalas@correo.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioDetailsService usuarioDetailsService;

    private final ObjectMapper json = new ObjectMapper();

    private Authentication como(String email) {
        UsuarioPrincipal principal = usuarioDetailsService.loadUserByUsername(email);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private JsonNode listarComo(String email) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/documentos")
                        .param("limite", "500")
                        .with(authentication(como(email))))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("el portal ve menos documentos que el personal del estudio")
    void elPortalVeUnSubconjuntoDeLoQueVeElEstudio() throws Exception {
        JsonNode delEstudio = listarComo(ADMINISTRADOR);
        JsonNode delPortal = listarComo(PORTAL_CLIENTE);

        assertThat(delEstudio.size())
                .as("la semilla debe traer documentos; si esto falla, la base no tiene la semilla")
                .isPositive();

        assertThat(delPortal.size())
                .as("el portal no puede ver mas documentos que el propio estudio")
                .isLessThanOrEqualTo(delEstudio.size());
    }

    @Test
    @DisplayName("el portal no ve ningun documento confidencial")
    void elPortalNoVeConfidenciales() throws Exception {
        for (JsonNode doc : listarComo(PORTAL_CLIENTE)) {
            assertThat(doc.path("esConfidencial").asBoolean(false))
                    .as("documento confidencial visible en el portal: %s", doc.path("nombre").asText())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("el portal no ve documentos en curso, solo los terminados")
    void elPortalNoVeBorradores() throws Exception {
        List<String> enCurso = List.of("BORRADOR", "EN_REVISION", "OBSERVADO");
        List<String> visibles = new ArrayList<>();

        for (JsonNode doc : listarComo(PORTAL_CLIENTE)) {
            visibles.add(doc.path("estado").asText());
        }

        assertThat(visibles)
                .as("un cliente viendo el borrador sin revisar de su propia demanda "
                        + "es un problema real, no teorico")
                .doesNotContainAnyElementsOf(enCurso);
    }

    @Test
    @DisplayName("un documento fuera de su visibilidad devuelve 404, no 403")
    void pedirUnDocumentoAjenoDevuelve404() throws Exception {
        // Se toma un documento que el estudio si ve y el portal no. Si el
        // portal los ve todos, no hay nada que comprobar y la prueba lo dice.
        List<String> soloDelEstudio = new ArrayList<>();
        List<String> delPortal = new ArrayList<>();
        for (JsonNode d : listarComo(PORTAL_CLIENTE)) {
            delPortal.add(d.path("uuid").asText());
        }
        for (JsonNode d : listarComo(ADMINISTRADOR)) {
            String uuid = d.path("uuid").asText();
            if (!delPortal.contains(uuid)) {
                soloDelEstudio.add(uuid);
            }
        }

        assertThat(soloDelEstudio)
                .as("la semilla debe tener algun documento que el portal NO deba ver, "
                        + "o esta prueba no comprueba nada")
                .isNotEmpty();

        // 404 y no 403: un 403 confirmaria que el documento existe.
        mockMvc.perform(get("/api/v1/documentos/{uuid}", soloDelEstudio.getFirst())
                        .with(authentication(como(PORTAL_CLIENTE))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("el desplegable de creadores no le entrega la plantilla del estudio al portal")
    void elPortalNoVeLaListaDeUsuariosDelEstudio() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/documentos/catalogos/creadores")
                        .with(authentication(como(PORTAL_CLIENTE))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(json.readTree(r.getResponse().getContentAsString()).size())
                .as("ese filtro no le sirve al portal y le entregaria los nombres "
                        + "de todo el personal del estudio")
                .isZero();
    }
}
