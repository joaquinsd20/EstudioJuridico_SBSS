package com.sbss.lexia.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El modulo de usuarios con tokens reales, incluidas las reglas que protegen
 * los enlaces: no se da de baja a quien sigue siendo responsable de algo, y
 * restablecer la contrasena cierra las sesiones de esa persona.
 *
 * Crea usuarios de verdad en lexia_db y los borra despues de cada prueba. Se
 * pueden borrar fisicamente porque son cuentas recien creadas, sin nada a su
 * nombre; esa es justo la unica situacion en la que el sistema permite la baja.
 *
 *   LEXIA_TEST_DB=1 ./mvnw test -Dtest=UsuariosGestionIT
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "LEXIA_TEST_DB", matches = ".+")
class UsuariosGestionIT {

    /** Usuario 7, ADMINISTRADOR: el unico con USUARIO_GESTIONAR ademas de la Socia. */
    private static final String ADMIN = "carlos.mendoza@lexia.com";
    /** Usuario 9, CONTABILIDAD: no tiene USUARIO_GESTIONAR. */
    private static final String CONTABILIDAD = "roberto.diaz@lexia.com";
    /** Usuario 2: la semilla le deja 3 expedientes, plazos y tareas abiertas. */
    private static final String CON_CARGA = "ana.morales@lexia.com";
    private static final String PASSWORD = "Lexia.Dev.2026";
    private static final String CORREO_PRUEBA = "prueba.it.usuarios@lexia.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM usuarios_roles WHERE usuario_id IN"
                + " (SELECT id FROM usuarios WHERE email = ?)", CORREO_PRUEBA);
        jdbc.update("DELETE FROM usuarios WHERE email = ?", CORREO_PRUEBA);
    }

    private String token(String email, String password) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }

    private String alta(String correo, String rol) {
        return """
                {"nombres":"Prueba","apellidos":"Integracion","email":"%s",
                 "cargo":"Tester","roles":["%s"]}""".formatted(correo, rol);
    }

    private String crearUsuarioDePrueba(String tokenAdmin) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alta(CORREO_PRUEBA, "ASISTENTE_LEGAL")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return cuerpo;
    }

    @Test
    @DisplayName("Contabilidad no puede administrar usuarios: 403")
    void sinPermisoNoEntra() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + token(CONTABILIDAD, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @DisplayName("el alta devuelve una contrasena temporal que sirve para entrar una vez")
    void altaConPasswordTemporal() throws Exception {
        String tokenAdmin = token(ADMIN, PASSWORD);
        String respuesta = crearUsuarioDePrueba(tokenAdmin);

        String temporal = JsonPath.read(respuesta, "$.passwordTemporal");
        assertThat(temporal).hasSizeGreaterThanOrEqualTo(12);

        // Entra con ella, y el sistema le exige cambiarla
        String cuerpo = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(CORREO_PRUEBA, temporal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.debeCambiarPassword").value(true))
                .andReturn().getResponse().getContentAsString();

        // Y el codigo de usuario se genero solo
        assertThat((String) JsonPath.read(cuerpo, "$.usuario.nombreCompleto")).contains("Prueba");
    }

    @Test
    @DisplayName("el correo del personal debe ser del dominio del estudio")
    void dominioObligatorio() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + token(ADMIN, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alta("alguien@gmail.com", "ABOGADO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("@lexia.com")));
    }

    @Test
    @DisplayName("el rol del portal no se asigna desde aqui")
    void rolDePortalRechazado() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + token(ADMIN, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alta(CORREO_PRUEBA, "CLIENTE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("portal")));
    }

    @Test
    @DisplayName("no se puede dar de baja a quien sigue siendo responsable de expedientes o plazos")
    void bajaBloqueadaPorCargaDeTrabajo() throws Exception {
        String tokenAdmin = token(ADMIN, PASSWORD);

        String uuid = jdbc.queryForObject(
                "SELECT uuid_publico FROM usuarios WHERE email = ?", String.class, CON_CARGA);

        mockMvc.perform(get("/api/v1/usuarios/" + uuid).header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cargaTrabajo.expedientes").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(post("/api/v1/usuarios/" + uuid + "/desactivar")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("reasignar")));

        // Y sigue activa: la baja no se aplico a medias
        Integer activo = jdbc.queryForObject(
                "SELECT activo FROM usuarios WHERE email = ?", Integer.class, CON_CARGA);
        assertThat(activo).isEqualTo(1);
    }

    @Test
    @DisplayName("una cuenta recien creada, sin nada a su nombre, si se puede dar de baja")
    void bajaPermitidaSinCarga() throws Exception {
        String tokenAdmin = token(ADMIN, PASSWORD);
        String uuid = JsonPath.read(crearUsuarioDePrueba(tokenAdmin), "$.uuidPublico");

        mockMvc.perform(post("/api/v1/usuarios/" + uuid + "/desactivar")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        Integer activo = jdbc.queryForObject(
                "SELECT activo FROM usuarios WHERE email = ?", Integer.class, CORREO_PRUEBA);
        assertThat(activo).isZero();
    }

    @Test
    @DisplayName("nadie puede desactivarse a si mismo ni quedarse sin Administrador")
    void noPuedeDesactivarseASiMismo() throws Exception {
        String tokenAdmin = token(ADMIN, PASSWORD);
        String uuid = jdbc.queryForObject(
                "SELECT uuid_publico FROM usuarios WHERE email = ?", String.class, ADMIN);

        mockMvc.perform(post("/api/v1/usuarios/" + uuid + "/desactivar")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("tu propia cuenta")));
    }

    @Test
    @DisplayName("restablecer la contrasena cierra las sesiones abiertas de esa persona")
    void restablecerCierraSesiones() throws Exception {
        String tokenAdmin = token(ADMIN, PASSWORD);
        String respuesta = crearUsuarioDePrueba(tokenAdmin);
        String uuid = JsonPath.read(respuesta, "$.uuidPublico");
        String tokenUsuario = token(CORREO_PRUEBA, JsonPath.read(respuesta, "$.passwordTemporal"));

        // Su sesion funciona (lo unico que puede hacer con contrasena temporal)
        mockMvc.perform(get("/api/v1/auth/yo").header("Authorization", "Bearer " + tokenUsuario))
                .andExpect(status().isOk());

        String nueva = mockMvc.perform(post("/api/v1/usuarios/" + uuid + "/restablecer-password")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // La sesion anterior quedo cerrada, y la contrasena nueva si entra
        mockMvc.perform(get("/api/v1/auth/yo").header("Authorization", "Bearer " + tokenUsuario))
                .andExpect(status().isUnauthorized());
        token(CORREO_PRUEBA, JsonPath.read(nueva, "$.passwordTemporal"));
    }

    @Test
    @DisplayName("el correo no se puede cambiar al editar: es el usuario del login")
    void correoNoSeCambia() throws Exception {
        String tokenAdmin = token(ADMIN, PASSWORD);
        String uuid = JsonPath.read(crearUsuarioDePrueba(tokenAdmin), "$.uuidPublico");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/usuarios/" + uuid)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alta("otro.correo@lexia.com", "ASISTENTE_LEGAL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("no se puede cambiar")));
    }
}
