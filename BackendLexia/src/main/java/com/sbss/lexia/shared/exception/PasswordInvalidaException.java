package com.sbss.lexia.shared.exception;

import lombok.Getter;

/**
 * La contrasena enviada al cambiarla no se acepta. Lleva su propio codigo
 * (PASSWORD_ACTUAL_INCORRECTA, PASSWORD_NO_VALIDA, PASSWORD_REPETIDA) para que
 * Angular sepa bajo que campo pintar el error sin parsear el mensaje.
 *
 * Siempre sale como 400, nunca 401: el usuario SI esta autenticado, y un 401
 * haria que el interceptor de Angular le cerrara la sesion por teclear mal su
 * contrasena actual.
 */
@Getter
public class PasswordInvalidaException extends RuntimeException {

    private final String codigo;

    public PasswordInvalidaException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }
}
