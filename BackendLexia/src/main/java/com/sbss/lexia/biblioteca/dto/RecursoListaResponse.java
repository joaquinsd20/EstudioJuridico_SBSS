package com.sbss.lexia.biblioteca.dto;

import java.time.LocalDateTime;

/**
 * DTO ligero para el catálogo. No expone ids internos, rutas físicas ni datos
 * de auditoría que la tabla no necesita.
 */
public record RecursoListaResponse(
        String uuid,
        String titulo,
        String subtituloDescripcion,
        String tipo,
        String categoria,
        Integer anio,
        String expedienteCodigo,
        String nombreArchivo,
        String extension,
        String estado,
        LocalDateTime fechaCreacion) {
}
