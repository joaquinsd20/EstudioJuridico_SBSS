package com.sbss.lexia.documental.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DocumentoActualizarRequest(
        @NotNull Long expedienteId,
        Long actuacionId,
        Long tareaId,
        @NotBlank @Size(max = 250) String nombre,
        String descripcion,
        @NotBlank @Size(max = 25) String categoria,
        @NotBlank @Size(max = 20) String estado,
        Boolean esConfidencial) {
}
