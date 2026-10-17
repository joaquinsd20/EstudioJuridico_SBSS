package com.sbss.lexia.usuarios;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import com.sbss.lexia.seguridad.service.UsuarioDetailsService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las fotos del personal solo salen por GET /api/v1/usuarios/foto/{nombre}, que
 * bloquea el portal del cliente y filtra por estudio.
 *
 * Verificado contra el backend real el 2026-10-17, antes del arreglo: #74
 * ademas publicaba la carpeta como recurso estatico en /uploads/usuarios/**.
 * Esa ruta pedia sesion, pero se saltaba las dos comprobaciones: el portal
 * recibia 403 por el endpoint y 200 por la ruta estatica, con la misma foto.
 *
 *   LEXIA_TEST_DB=1 ./mvnw test -Dtest=FotosUsuarioIT
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "LEXIA_TEST_DB", matches = ".+")
class FotosUsuarioIT {

    private static final String ADMIN = "carlos.mendoza@lexia.com";
    /** CLIENTE (portal): no debe ver fotos del personal. */
    private static final String PORTAL = "anamaria.fsalas@correo.com";

    /** Las fotos de la prueba van a una carpeta temporal, no a la del equipo. */
    private static final Path CARPETA = carpetaTemporal();

    @DynamicPropertySource
    static void carpetaDeFotos(DynamicPropertyRegistry registro) {
        registro.add("lexia.usuarios.fotos-dir", CARPETA::toString);
    }

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
    @DisplayName("el portal no recibe la foto de un empleado por ninguna ruta")
    void elPortalNoRecibeLaFotoPorNingunaRuta() throws Exception {
        byte[] png = pngDePrueba();
        String uuidJuan = jdbc.queryForObject(
                "SELECT uuid_publico FROM usuarios WHERE email = 'juan.perez@lexia.com'", String.class);

        String respuesta = mockMvc.perform(multipart("/api/v1/usuarios/" + uuidJuan + "/foto")
                        .file(new MockMultipartFile("archivo", "foto.png", "image/png", png))
                        .with(authentication(como(ADMIN))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String nombre = respuesta.replaceAll(".*/uploads/usuarios/([^\"]+)\".*", "$1");

        // Control: el endpoint protegido sirve la foto a quien corresponde...
        byte[] servida = mockMvc.perform(get("/api/v1/usuarios/foto/" + nombre).with(authentication(como(ADMIN))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(png, servida);
        // ...y se la niega al portal.
        mockMvc.perform(get("/api/v1/usuarios/foto/" + nombre).with(authentication(como(PORTAL))))
                .andExpect(status().isForbidden());

        // La ruta estatica ya no existe: antes devolvia 200 al portal.
        mockMvc.perform(get("/uploads/usuarios/" + nombre).with(authentication(como(PORTAL))))
                .andExpect(status().isNotFound());
    }

    private static byte[] pngDePrueba() throws IOException {
        BufferedImage imagen = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        return salida.toByteArray();
    }

    private static Path carpetaTemporal() {
        try {
            return Files.createTempDirectory("lexia-fotos-it");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
