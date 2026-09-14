package com.sbss.lexia.documental.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentoContenidoRequest(
        @NotBlank String html,
        @Size(max = 500) String observaciones) {
}
