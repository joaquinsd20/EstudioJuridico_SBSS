package com.sbss.lexia.documental.dto;

import java.time.LocalDateTime;

public record DocumentoVersionResponse(
        Long id,
        Integer numeroVersion,
        String nombreArchivo,
        String extension,
        String mimeType,
        Long tamanoBytes,
        String hashSha256,
        String cargadoPor,
        LocalDateTime fechaCarga,
        String observaciones,
        boolean vigente) {
}
