package com.sbss.lexia.seguridad.dto;

/** Respuesta del login: el token y lo minimo que la interfaz necesita pintar. */
public record LoginResponse(
        String token,
        String tipo,
        /** Segundos de vida del token, para que Angular sepa cuando renovarlo. */
        long expiraEnSegundos,
        UsuarioAutenticadoDto usuario) {
}
