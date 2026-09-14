package com.sbss.lexia.shared.exception;

/**
 * Un parametro de la peticion no es valido mas alla de lo que cubre
 * {@code @Valid} — por ejemplo, un campo de {@code ?sort=} que existe en la
 * entidad pero no esta en la lista blanca del endpoint (porque no esta
 * indexado, o porque exponerlo como criterio de orden no tiene sentido de
 * negocio). Distinta de {@code PropertyReferenceException}, que cubre un
 * campo de orden que ni siquiera existe.
 */
public class ParametroInvalidoException extends RuntimeException {

    public ParametroInvalidoException(String mensaje) {
        super(mensaje);
    }
}
