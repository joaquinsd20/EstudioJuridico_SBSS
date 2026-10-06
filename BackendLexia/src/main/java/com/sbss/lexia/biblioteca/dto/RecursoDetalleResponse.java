package com.sbss.lexia.biblioteca.dto;

import java.time.LocalDateTime;

/** Detalle bajo demanda de un recurso jurídico. */
public record RecursoDetalleResponse(
        String uuid,
        String titulo,
        String subtituloDescripcion,
        String tipo,
        String categoria,
        Integer anio,
        String expedienteCodigo,
        String nombreArchivo,
        String extension,
        Long tamanioBytes,
        String estado,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaModificacion) {
}
