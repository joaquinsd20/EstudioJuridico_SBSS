package com.sbss.lexia.documental.dto;

import java.time.LocalDateTime;

public record DocumentoResponse(
        String uuid,
        String nombre,
        String descripcion,
        String categoria,
        String estado,
        boolean confidencial,
        boolean enPapelera,
        LocalDateTime fechaPapelera,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaModificacion,
        Long expedienteId,
        Long actuacionId,
        Long tareaId,
        String expedienteCodigo,
        String numeroExpediente,
        Long clienteId,
        String clienteNombre,
        String creadoPor,
        String responsable,
        String nombreArchivo,
        String extension,
        String mimeType,
        Long tamanoBytes,
        Integer numeroVersion) {
}
