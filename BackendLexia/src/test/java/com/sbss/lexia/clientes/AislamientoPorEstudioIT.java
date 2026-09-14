package com.sbss.lexia.clientes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sbss.lexia.catalogos.entity.Estudio;
import com.sbss.lexia.catalogos.repository.EstudioRepository;
import com.sbss.lexia.clientes.entity.Cliente;
import com.sbss.lexia.clientes.repository.ClienteRepository;
import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import com.sbss.lexia.seguridad.service.UsuarioDetailsService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
// En Spring Boot 4 este paquete se movio de
// org.springframework.boot.test.autoconfigure.web.servlet a
// org.springframework.boot.webmvc.test.autoconfigure (ver pom.xml, dependencia
// spring-boot-starter-webmvc-test).
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Demuestra que un cliente de OTRO estudio es invisible para un usuario del
 * estudio 1, aun con un permiso que le alcanzaria para verlo en su propio
 * estudio (CLIENTE_LEER).
 *
 * Necesita el MySQL local de desarrollo con la semilla cargada: se salta si
 * LEXIA_TEST_DB no esta definida, para no exigirle Docker/Testcontainers a
 * nadie del equipo. Correr con:
 *   LEXIA_TEST_DB=1 ./mvnw test -Dtest=AislamientoPorEstudioIT
 *
 * La autenticacion se arma directamente con UsuarioDetailsService y se inyecta
 * en la peticion con SecurityMockMvcRequestPostProcessors.authentication(...),
 * sin pasar por el filtro JWT: lo que se pone a prueba aqui es el aislamiento
 * por estudio en el repositorio/servicio, no la validacion del token — eso ya
 * lo cubre JwtAuthenticationFilter por su cuenta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "LEXIA_TEST_DB", matches = ".+")
class AislamientoPorEstudioIT {

    /** Administrador de la semilla (estudio 1): todos los permisos, incluido CLIENTE_LEER. */
    private static final String EMAIL_ADMIN_ESTUDIO_1 = "carlos.mendoza@lexia.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioDetailsService usuarioDetailsService;

    @Autowired
    private EstudioRepository estudioRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    private Estudio estudioAjeno;
    private Cliente clienteAjeno;
    private Authentication autenticacionEstudio1;

    @BeforeAll
    void crearClienteDeOtroEstudio() {
        estudioAjeno = new Estudio();
        estudioAjeno.setRazonSocial("Estudio de prueba (AislamientoPorEstudioIT)");
        estudioAjeno.setRuc("20999999999");
        estudioAjeno.setZonaHoraria("America/Lima");
        estudioAjeno.setMonedaBase("PEN");
        estudioAjeno = estudioRepository.save(estudioAjeno);

        clienteAjeno = new Cliente();
        clienteAjeno.setEstudioId(estudioAjeno.getId());
        clienteAjeno.setTipoPersona("NATURAL");
        clienteAjeno.setTipoDocumento("DNI");
        clienteAjeno.setNumeroDocumento("00000002");
        clienteAjeno.setNombres("Prueba");
        clienteAjeno.setApellidoPaterno("Aislamiento");
        clienteAjeno.setEstado("ACTIVO");
        clienteAjeno = clienteRepository.save(clienteAjeno);

        UsuarioPrincipal principal = usuarioDetailsService.loadUserByUsername(EMAIL_ADMIN_ESTUDIO_1);
        autenticacionEstudio1 = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
    }

    @AfterAll
    void borrarClienteDeOtroEstudio() {
        // Orden por la FK fk_clientes_estudio: el cliente primero.
        if (clienteAjeno != null) {
            clienteRepository.deleteById(clienteAjeno.getId());
        }
        if (estudioAjeno != null) {
            estudioRepository.deleteById(estudioAjeno.getId());
        }
    }

    @Test
    void laListaDelEstudio1NoIncluyeUnClienteDeOtroEstudio() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/v1/clientes")
                        .param("size", "200")
                        .with(authentication(autenticacionEstudio1)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString())
                .as("La lista de clientes del estudio 1 no debe incluir a un cliente de otro estudio")
                .doesNotContain(clienteAjeno.getUuidPublico());
    }

    @Test
    void obtenerUnClienteDeOtroEstudioDevuelve404NoNi403() throws Exception {
        // 404, no 403: un 403 confirmaria que el recurso existe (en otro
        // estudio), que ya es una fuga de informacion entre inquilinos.
        mockMvc.perform(get("/api/v1/clientes/{uuid}", clienteAjeno.getUuidPublico())
                        .with(authentication(autenticacionEstudio1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }
}
