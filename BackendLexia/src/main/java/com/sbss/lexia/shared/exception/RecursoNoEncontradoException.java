package com.sbss.lexia.shared.exception;

/**
 * Un recurso identificado por su uuid_publico no existe, o no es visible desde
 * la sesion actual (otro estudio). Las dos situaciones se tratan igual a
 * proposito: devolver 403 en vez de 404 cuando el recurso pertenece a otro
 * estudio confirmaria que existe, que ya es una fuga de informacion entre
 * inquilinos.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public static RecursoNoEncontradoException de(String tipoRecurso, String uuid) {
        return new RecursoNoEncontradoException(tipoRecurso + " no encontrado: " + uuid);
    }
}
