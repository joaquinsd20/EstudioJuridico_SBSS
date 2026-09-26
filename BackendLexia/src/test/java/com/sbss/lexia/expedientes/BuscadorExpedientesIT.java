package com.sbss.lexia.expedientes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import com.sbss.lexia.seguridad.service.UsuarioDetailsService;
import org.hamcrest.Matchers;
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
 * El selector de expedientes, que existe para que los demas modulos no
 * vuelquen los miles de expedientes visibles en un desplegable.
 *
 * Medido con 5.010 expedientes: /plazos/catalogos devuelve 1.038 KB y
 * /expedientes/buscar 4 KB.
 *
 *   LEXIA_TEST_DB=1 ./mvnw test -Dtest=BuscadorExpedientesIT
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "LEXIA_TEST_DB", matches = ".+")
class BuscadorExpedientesIT {

    private static final String ADMIN = "carlos.mendoza@lexia.com";
    /** Responsable de EXP-0088, que es confidencial. */
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

    @Test
    @DisplayName("sin texto devuelve los mas recientes, acotados")
    void sinTextoDevuelveLosRecientes() throws Exception {
        mockMvc.perform(get("/api/v1/expedientes/buscar").with(authentication(como(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(Matchers.both(
                        Matchers.greaterThan(0)).and(Matchers.lessThanOrEqualTo(20))));
    }

    @Test
    @DisplayName("el tope son 50 opciones aunque se pidan 9999")
    void elTopeSonCincuenta() throws Exception {
        mockMvc.perform(get("/api/v1/expedientes/buscar").param("limite", "9999")
                        .with(authentication(como(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(Matchers.lessThanOrEqualTo(50)));
    }

    @Test
    @DisplayName("encuentra por prefijo del codigo interno")
    void encuentraPorCodigo() throws Exception {
        mockMvc.perform(get("/api/v1/expedientes/buscar").param("texto", "EXP-0239")
                        .with(authentication(como(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigoInterno == 'EXP-0239')]").isNotEmpty());
    }

    @Test
    @DisplayName("encuentra por una palabra INTERIOR del nombre del cliente, que un prefijo no encontraria")
    void encuentraPorPalabraInteriorDelCliente() throws Exception {
        // La razon de ser de la rama FULLTEXT. Los nombres se guardan como
        // "Banco del Peru S.A.A." y "Fernandez Salas Ana Maria", asi que un
        // LIKE 'peru%' no encuentra nada. Se comprueba aqui para que la prueba
        // no pase en vacio si algun dia cambia la semilla.
        Integer conPrefijo = jdbc.queryForObject(
                "SELECT COUNT(*) FROM clientes WHERE nombre_busqueda LIKE 'peru%'", Integer.class);
        assertEquals(0, conPrefijo,
                "si un prefijo simple ya encontrara 'peru', esta prueba no demostraria nada");

        mockMvc.perform(get("/api/v1/expedientes/buscar").param("texto", "peru")
                        .with(authentication(como(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.cliente =~ /.*Peru.*/i)]").isNotEmpty());

        // Y por nombre de pila, que en "Fernandez Salas Ana Maria" es la tercera palabra
        mockMvc.perform(get("/api/v1/expedientes/buscar").param("texto", "ana")
                        .with(authentication(como(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.cliente =~ /.*Ana Maria.*/)]").isNotEmpty());
    }

    @Test
    @DisplayName("los operadores de fulltext en el texto no rompen la consulta")
    void losOperadoresNoRompen() throws Exception {
        // IN BOOLEAN MODE interpreta + - * " ( ) ~ < > @, y pasarlos crudos da
        // error de sintaxis o resultados absurdos: el termino se construye.
        for (String veneno : new String[] {"+peru* (\"", "~~~", "\"peru", "@peru", "*", "-"}) {
            mockMvc.perform(get("/api/v1/expedientes/buscar").param("texto", veneno)
                            .with(authentication(como(ADMIN))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("una palabra de menos de 3 letras no revienta, aunque el indice no la tenga")
    void palabraCortaNoRevienta() throws Exception {
        // innodb_ft_min_token_size vale 3: "ab" no esta en el indice. La rama
        // fulltext se omite y quedan las de codigo y numero.
        mockMvc.perform(get("/api/v1/expedientes/buscar").param("texto", "ab")
                        .with(authentication(como(ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("el buscador respeta la visibilidad de RF-02")
    void respetaLaVisibilidad() throws Exception {
        // EXP-0088 es confidencial y su responsable es Maria, sin el permiso
        // EXPEDIENTE_CONFIDENCIAL. Juan no tiene relacion con el.
        mockMvc.perform(get("/api/v1/expedientes/buscar").param("texto", "EXP-0088")
                        .with(authentication(como(JUAN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigoInterno == 'EXP-0088')]").isEmpty());

        mockMvc.perform(get("/api/v1/expedientes/buscar").param("texto", "EXP-0088")
                        .with(authentication(como(MARIA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigoInterno == 'EXP-0088')]").isNotEmpty());
    }

    @Test
    @DisplayName("no expone el id BIGINT, solo el uuid")
    void noExponeElId() throws Exception {
        mockMvc.perform(get("/api/v1/expedientes/buscar").with(authentication(como(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].uuid").isString())
                .andExpect(jsonPath("$[0].id").doesNotExist());
    }
}
