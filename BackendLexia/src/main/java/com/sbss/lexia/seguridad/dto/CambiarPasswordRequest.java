package com.sbss.lexia.seguridad.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Cambio de contrasena del propio usuario. La confirmacion ("repite la nueva")
 * la valida Angular: al backend solo le importa que la nueva cumpla la politica.
 */
public record CambiarPasswordRequest(
        @NotBlank(message = "La contrasena actual es obligatoria")
        String passwordActual,
        @NotBlank(message = "La contrasena nueva es obligatoria")
        String passwordNueva) {
}
