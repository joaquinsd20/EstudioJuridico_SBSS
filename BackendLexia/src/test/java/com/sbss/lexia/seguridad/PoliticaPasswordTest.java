package com.sbss.lexia.seguridad;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sbss.lexia.seguridad.service.PoliticaPassword;
import com.sbss.lexia.shared.exception.PasswordInvalidaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reglas de contrasena nueva. Clase pura: sin Spring ni base de datos. */
class PoliticaPasswordTest {

    private static final String EMAIL = "carlos.mendoza@lexia.com";

    @Test
    @DisplayName("una frase larga sin simbolos es valida: se exige longitud, no composicion")
    void fraseLargaEsValida() {
        assertThatCode(() -> PoliticaPassword.validar("mi gato duerme en el expediente", EMAIL))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("11 caracteres no alcanzan; 12 si")
    void longitudMinima() {
        assertThatThrownBy(() -> PoliticaPassword.validar("abcdefghijk", EMAIL))
                .isInstanceOf(PasswordInvalidaException.class)
                .hasMessageContaining("12");
        assertThatCode(() -> PoliticaPassword.validar("abcdefghijkl", EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("mas de 72 bytes se rechaza: BCrypt ignoraria el resto")
    void maximoDeBytes() {
        // 72 bytes exactos: se acepta
        assertThatCode(() -> PoliticaPassword.validar("a1b2c3d4e5".repeat(7) + "xy", EMAIL))
                .doesNotThrowAnyException();
        // 73: se rechaza
        assertThatThrownBy(() -> PoliticaPassword.validar("a1b2c3d4e5".repeat(7) + "xyz", EMAIL))
                .isInstanceOf(PasswordInvalidaException.class);
    }

    @Test
    @DisplayName("las tildes cuentan como dos bytes al medir el maximo")
    void tildesCuentanDoble() {
        // 37 caracteres, pero 73 bytes en UTF-8
        assertThatThrownBy(() -> PoliticaPassword.validar("á".repeat(36) + "b", EMAIL))
                .isInstanceOf(PasswordInvalidaException.class);
    }

    @Test
    @DisplayName("un mismo caracter repetido no vale aunque sea largo")
    void caracterRepetido() {
        assertThatThrownBy(() -> PoliticaPassword.validar("aaaaaaaaaaaaaaaa", EMAIL))
                .isInstanceOf(PasswordInvalidaException.class);
    }

    @Test
    @DisplayName("no puede contener el usuario del correo, sin importar mayusculas")
    void noContieneElUsuario() {
        assertThatThrownBy(() -> PoliticaPassword.validar("Carlos.Mendoza2026", EMAIL))
                .isInstanceOf(PasswordInvalidaException.class)
                .hasMessageContaining("usuario");
    }

    @Test
    @DisplayName("todo rechazo sale con el codigo PASSWORD_NO_VALIDA")
    void codigo() {
        assertThatThrownBy(() -> PoliticaPassword.validar("corta", EMAIL))
                .hasFieldOrPropertyWithValue("codigo", "PASSWORD_NO_VALIDA");
    }
}
