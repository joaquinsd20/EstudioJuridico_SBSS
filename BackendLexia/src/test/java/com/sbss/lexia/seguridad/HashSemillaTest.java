package com.sbss.lexia.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Protege el hash de la semilla de desarrollo.
 *
 * La version anterior de R__seed_desarrollo.sql llevaba una cadena de relleno de
 * 55 caracteres que parecia un hash BCrypt pero no lo era, asi que ningun usuario
 * podia iniciar sesion. Esta prueba deja constancia de cual es la contrasena de
 * desarrollo y falla si alguien vuelve a poner un valor invalido.
 *
 * No necesita base de datos ni contexto de Spring.
 */
class HashSemillaTest {

    private static final String PASSWORD_DESARROLLO = "Lexia.Dev.2026";
    private static final String HASH_SEMILLA =
            "$2a$12$6kg/Qlbe39nKhdxrNi4BAe7N7URnAFo7xnwucHXE2.kaetLjOimLK";

    @Test
    void elHashDeLaSemillaCorrespondeALaPasswordDeDesarrollo() {
        assertThat(new BCryptPasswordEncoder(12).matches(PASSWORD_DESARROLLO, HASH_SEMILLA))
                .as("El hash de R__seed_desarrollo.sql debe validar contra '%s'", PASSWORD_DESARROLLO)
                .isTrue();
    }

    @Test
    void elHashTieneElFormatoBCrypt() {
        assertThat(HASH_SEMILLA).startsWith("$2a$12$").hasSize(60);
    }
}
