package com.sbss.lexia.seguridad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Edicion de los datos PROPIOS del usuario, desde su perfil.
 *
 * Deliberadamente mas corto que `GuardarUsuarioRequest`: aqui el usuario solo
 * puede cambiar lo que le pertenece. Quedan fuera a proposito
 *
 *   - `cargo` y `numeroColegiatura`: son credenciales profesionales, las
 *     concede el estudio, no el propio usuario. Si se dejaran editables aqui,
 *     cualquiera podria ponerse "Director" o inventarse una colegiatura y el
 *     panel lo mostraria como si el estudio lo hubiera nombrado.
 *   - `email`: es la identidad con la que se entra. Cambiarlo por la via
 *     rapida desincroniza al usuario de las invitaciones ya enviadas y de los
 *     registros de doble factor; si se necesita, es un flujo aparte con
 *     verificacion del correo nuevo.
 *   - roles y permisos: los asigna un administrador desde Configuracion.
 */
public record ActualizarPerfilRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede pasar de 100 caracteres")
        String nombres,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 100, message = "Los apellidos no pueden pasar de 100 caracteres")
        String apellidos,

        @Size(max = 30, message = "El telefono no puede pasar de 30 caracteres")
        @Pattern(regexp = "^$|^[0-9+() .-]{6,30}$",
                message = "El telefono solo admite numeros, espacios y los signos + ( ) - .")
        String telefono,

        @Size(max = 500, message = "La direccion de la foto no puede pasar de 500 caracteres")
        @Pattern(regexp = "^$|^https://[\\w.-]+(/[\\w./%-]*)?$",
                message = "La foto debe ser una direccion https")
        String fotoUrl) {

    public String telefono() {
        return telefono == null ? null : telefono.trim();
    }

    public String fotoUrl() {
        return fotoUrl == null || fotoUrl.isBlank() ? null : fotoUrl.trim();
    }
}
