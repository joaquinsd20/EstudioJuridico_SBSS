package com.sbss.lexia.documental;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Comprueba que los @PreAuthorize de DocumentoController de verdad bloquean, y
 * que un acceso denegado sale como 403 con el formato de error de la API — no
 * como 500 ni, peor, como 200.
 *
 * Esa cadena no es obvia: @PreAuthorize lanza AuthorizationDeniedException
 * DENTRO del DispatcherServlet, asi que el @RestControllerAdvice la ve antes
 * que Spring Security. ManejadorGlobalExcepciones la relanza a proposito para
 * que siga su curso hasta ApiAccessDeniedHandler, que es quien arma el 403.
 * Si alguien agrega un catch-all de Exception sin respetar eso, todos los 403
 * se convierten en 500 en silencio y esta prueba lo detecta.
 *
 * Las denegaciones se prueban con un uuid inventado a proposito: la
 * autorizacion se evalua ANTES de entrar al metodo, asi que el servicio nunca
 * llega a ejecutarse. Eso hace la prueba independiente de que la semilla tenga
 * documentos cargados.
 *
 *   LEXIA_TEST_DB=1 ./mvnw test -Dtest=PermisosDocumentalIT
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "LEXIA_TEST_DB", matches = ".+")
class PermisosDocumentalIT {

    /** Usuario 7, rol ADMINISTRADOR: tiene todos los permisos. */
    private static final String ADMINISTRADOR = "carlos.mendoza@lexia.com";

    /** Usuario 9, rol CONTABILIDAD: no tiene ningun permiso de DOCUMENTOS. */
    private static final String CONTABILIDAD = "roberto.diaz@lexia.com";

    /** Usuario 4, rol ABOGADO_AUXILIAR: tiene DOCUMENTO_LEER y CREAR, pero NO DESCARGAR. */
    private static final String ABOGADO_AUXILIAR = "maria.lopez@lexia.com";

    private static final String UUID_INVENTADO = "00000000-0000-0000-0000-000000000000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioDetailsService usuarioDetailsService;

    private Authentication como(String email) {
        UsuarioPrincipal principal = usuarioDetailsService.loadUserByUsername(email);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("Contabilidad no puede listar documentos: 403, no 500 ni 200")
    void contabilidadNoPuedeListarDocumentos() throws Exception {
        mockMvc.perform(get("/api/v1/documentos").with(authentication(como(CONTABILIDAD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"))
                .andExpect(jsonPath("$.estado").value(403));
    }

    @Test
    @DisplayName("el Administrador si puede listar documentos")
    void elAdministradorSiPuedeListarDocumentos() throws Exception {
        mockMvc.perform(get("/api/v1/documentos").with(authentication(como(ADMINISTRADOR))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("el abogado auxiliar si puede listar: tiene DOCUMENTO_LEER")
    void elAbogadoAuxiliarSiPuedeListar() throws Exception {
        mockMvc.perform(get("/api/v1/documentos").with(authentication(como(ABOGADO_AUXILIAR))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("el abogado auxiliar no puede descargar: no tiene DOCUMENTO_DESCARGAR")
    void elAbogadoAuxiliarNoPuedeDescargar() throws Exception {
        mockMvc.perform(get("/api/v1/documentos/{uuid}/descargar", UUID_INVENTADO)
                        .with(authentication(como(ABOGADO_AUXILIAR))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("visualizar tampoco: devuelve los mismos bytes que descargar")
    void elAbogadoAuxiliarTampocoPuedeVisualizar() throws Exception {
        // El punto de toda la revision: /visualizar y /descargar llaman al mismo
        // metodo del servicio y devuelven el mismo archivo, solo cambia
        // inline vs attachment. Si visualizar quedara con DOCUMENTO_LEER, la
        // restriccion de descarga seria cosmetica — bastaria cambiar una
        // palabra en la URL.
        mockMvc.perform(get("/api/v1/documentos/{uuid}/visualizar", UUID_INVENTADO)
                        .with(authentication(como(ABOGADO_AUXILIAR))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("el borrado definitivo es solo del Administrador")
    void soloElAdministradorBorraDefinitivamente() throws Exception {
        mockMvc.perform(delete("/api/v1/documentos/{uuid}", UUID_INVENTADO)
                        .with(authentication(como(ABOGADO_AUXILIAR))))
                .andExpect(status().isForbidden());

        // El Administrador pasa el control de permisos. Lo que responda despues
        // depende de como el servicio maneje un uuid inexistente, asi que aqui
        // solo se comprueba que NO lo frene la autorizacion.
        mockMvc.perform(delete("/api/v1/documentos/{uuid}", UUID_INVENTADO)
                        .with(authentication(como(ADMINISTRADOR))))
                .andExpect(resultado ->
                        org.assertj.core.api.Assertions
                                .assertThat(resultado.getResponse().getStatus())
                                .as("al Administrador no debe frenarlo el permiso")
                                .isNotEqualTo(403));
    }
}
