package com.sbss.lexia.seguridad.service;

import com.sbss.lexia.shared.exception.PasswordInvalidaException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Reglas que debe cumplir una contrasena nueva.
 *
 * Sigue la guia NIST SP 800-63B: se exige LONGITUD y no composicion. Obligar a
 * "una mayuscula, un numero y un simbolo" produce contrasenas como
 * "Lexia2026!", faciles de adivinar y dificiles de recordar; una frase larga
 * ("mi gato duerme en el expediente") es mas segura y se recuerda. Tampoco se
 * obliga a cambiarla cada N dias: eso empuja a variaciones predecibles.
 *
 * El maximo de 72 BYTES no es arbitrario: BCrypt ignora todo lo que venga
 * despues, asi que una contrasena mas larga daria una falsa sensacion de
 * seguridad (y Spring Security rechaza con excepcion las que lo superan).
 * Se cuentan bytes UTF-8, no caracteres: una tilde ocupa dos.
 *
 * Clase pura, sin Spring: se prueba sin levantar el contexto.
 */
public final class PoliticaPassword {

    public static final int LONGITUD_MINIMA = 12;
    public static final int BYTES_MAXIMOS = 72;

    private PoliticaPassword() {
    }

    /** Lanza PasswordInvalidaException(PASSWORD_NO_VALIDA) si no cumple. */
    public static void validar(String password, String email) {
        if (password == null || password.length() < LONGITUD_MINIMA) {
            throw noValida("La contrasena debe tener al menos " + LONGITUD_MINIMA + " caracteres");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS) {
            throw noValida("La contrasena es demasiado larga (maximo " + BYTES_MAXIMOS
                    + " bytes; las letras con tilde cuentan doble)");
        }
        if (password.isBlank() || password.chars().distinct().count() < 4) {
            throw noValida("La contrasena no puede ser un mismo caracter repetido");
        }
        String usuario = email == null ? "" : email.substring(0, Math.max(0, email.indexOf('@')));
        if (usuario.length() >= 4
                && password.toLowerCase(Locale.ROOT).contains(usuario.toLowerCase(Locale.ROOT))) {
            throw noValida("La contrasena no puede contener tu nombre de usuario");
        }
    }

    private static PasswordInvalidaException noValida(String mensaje) {
        return new PasswordInvalidaException("PASSWORD_NO_VALIDA", mensaje);
    }
}
