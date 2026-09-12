package com.sbss.lexia.seguridad.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Credenciales que envia la pantalla de login de Angular. */
public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato valido")
        String email,

        @NotBlank(message = "La contrasena es obligatoria")
        String password) {
}
