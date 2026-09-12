package com.sbss.lexia.shared.exception;

import java.time.LocalDateTime;
import java.util.List;

/** Formato unico de error de la API, segun CLAUDE.md. */
public record ErrorRespuesta(
        LocalDateTime timestamp,
        int estado,
        String codigo,
        String mensaje,
        List<String> detalles) {

    public static ErrorRespuesta de(int estado, String codigo, String mensaje) {
        return new ErrorRespuesta(LocalDateTime.now(), estado, codigo, mensaje, List.of());
    }
}
