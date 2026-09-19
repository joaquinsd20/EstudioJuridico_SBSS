package com.sbss.lexia.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
 * El flujo de contrasenas de punta a punta, con tokens reales (no con
 * `authentication(...)`, que se saltaria el filtro JWT y con el la revocacion).
 *
 * Usa a Lucia (usuario 10), la unica de la semilla con debe_cambiar_password = 1.
 * Cambia su contrasena de verdad en lexia_db, asi que despues de CADA prueba se
 * restaura exactamente la fila que habia: hash, bandera, version, intentos y
 * estado. Si no, la siguiente ejecucion encontraria otra contrasena.
 *
 *   LEXIA_TEST_DB=1 ./mvnw test -Dtest=CambioPasswordIT
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "LEXIA_TEST_DB", matches = ".+")
class CambioPasswordIT {

    private static final String LUCIA = "lucia.castillo@lexia.com";
    private static final String PASSWORD_SEMILLA = "Lexia.Dev.2026";
    private static final String PASSWORD_NUEVA = "una frase larga para lucia";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Map<String, Object> filaOriginal;

    @BeforeAll
    void guardarFila() {
        filaOriginal = jdbc.queryForMap("""
                SELECT password_hash, debe_cambiar_password, fecha_cambio_password,
                       intentos_fallidos, estado, fecha_bloqueo
                  FROM usuarios WHERE email = ?""", LUCIA);
        assertThat(String.valueOf(filaOriginal.get("debe_cambiar_password")))
                .as("la semilla debe dejar a Lucia con debe_cambiar_password = 1")
                .isIn("true", "1");
    }

    @AfterEach
    void restaurarFila() {
        jdbc.update("""
                UPDATE usuarios SET password_hash = ?, debe_cambiar_password = ?,
                       fecha_cambio_password = ?, intentos_fallidos = ?, estado = ?, fecha_bloqueo = ?
                 WHERE email = ?""",
                filaOriginal.get("password_hash"), filaOriginal.get("debe_cambiar_password"),
                filaOriginal.get("fecha_cambio_password"), filaOriginal.get("intentos_fallidos"),
                filaOriginal.get("estado"), filaOriginal.get("fecha_bloqueo"), LUCIA);
    }

    private String credenciales(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private String cuerpoCambio(String actual, String nueva) {
        return "{\"passwordActual\":\"%s\",\"passwordNueva\":\"%s\"}".formatted(actual, nueva);
    }

    private String login(String email, String password) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }

    @Test
    @DisplayName("con la contrasena temporal solo puede ver quien es: lo demas es 403 PASSWORD_CAMBIO_OBLIGATORIO")
    void contrasenaTemporalBloqueaElResto() throws Exception {
        String token = login(LUCIA, PASSWORD_SEMILLA);

        mockMvc.perform(get("/api/v1/auth/yo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarPassword").value(true));

        mockMvc.perform(get("/api/v1/clientes").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PASSWORD_CAMBIO_OBLIGATORIO"));
    }

    @Test
    @DisplayName("contrasena actual incorrecta: 400 (no 401, que cerraria la sesion) y cuenta como intento fallido")
    void actualIncorrecta() throws Exception {
        String token = login(LUCIA, PASSWORD_SEMILLA);

        mockMvc.perform(post("/api/v1/auth/cambiar-password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCambio("no-es-la-actual", PASSWORD_NUEVA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PASSWORD_ACTUAL_INCORRECTA"));

        Integer intentos = jdbc.queryForObject(
                "SELECT intentos_fallidos FROM usuarios WHERE email = ?", Integer.class, LUCIA);
        assertThat(intentos).isEqualTo(1);
    }

    @Test
    @DisplayName("nueva demasiado corta o igual a la actual: 400 con su codigo")
    void nuevaNoValida() throws Exception {
        String token = login(LUCIA, PASSWORD_SEMILLA);

        mockMvc.perform(post("/api/v1/auth/cambiar-password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCambio(PASSWORD_SEMILLA, "corta")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PASSWORD_NO_VALIDA"));

        mockMvc.perform(post("/api/v1/auth/cambiar-password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCambio(PASSWORD_SEMILLA, PASSWORD_SEMILLA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PASSWORD_REPETIDA"));
    }

    @Test
    @DisplayName("al cambiarla: el token nuevo entra, el viejo queda revocado y solo vale la contrasena nueva")
    void cambioCompleto() throws Exception {
        String tokenViejo = login(LUCIA, PASSWORD_SEMILLA);

        String cuerpo = mockMvc.perform(post("/api/v1/auth/cambiar-password")
                        .header("Authorization", "Bearer " + tokenViejo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoCambio(PASSWORD_SEMILLA, PASSWORD_NUEVA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.debeCambiarPassword").value(false))
                .andReturn().getResponse().getContentAsString();
        String tokenNuevo = JsonPath.read(cuerpo, "$.token");

        // El token nuevo ya no tiene restricciones (Recepcionista tiene CLIENTE_LEER)
        mockMvc.perform(get("/api/v1/clientes").header("Authorization", "Bearer " + tokenNuevo))
                .andExpect(status().isOk());

        // El viejo se emitio en el mismo segundo que el cambio, y aun asi queda
        // revocado: la version de contrasena se compara por igualdad, no por hora
        mockMvc.perform(get("/api/v1/auth/yo").header("Authorization", "Bearer " + tokenViejo))
                .andExpect(status().isUnauthorized());

        // La contrasena anterior ya no entra; la nueva si
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales(LUCIA, PASSWORD_SEMILLA)))
                .andExpect(status().isUnauthorized());
        login(LUCIA, PASSWORD_NUEVA);

        assertThat(jdbc.queryForObject(
                "SELECT fecha_cambio_password FROM usuarios WHERE email = ?", Object.class, LUCIA))
                .isNotNull();
    }
}
