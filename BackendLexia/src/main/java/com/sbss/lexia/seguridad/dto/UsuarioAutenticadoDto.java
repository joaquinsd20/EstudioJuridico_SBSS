package com.sbss.lexia.seguridad.dto;

import java.util.List;

/**
 * Datos del usuario que Angular necesita tras el login: los del sidebar y los
 * que deciden que secciones mostrar.
 *
 * Expone `uuid`, nunca el id BIGINT: ese no sale del backend.
 */
public record UsuarioAutenticadoDto(
        String uuid,
        String nombreCompleto,
        String email,
        String cargo,
        String fotoUrl,
        boolean debeCambiarPassword,
        /** Codigos inmutables de rol. Angular decide con estos, no con la etiqueta. */
        List<String> roles,
        /** Etiqueta visible del rol de mayor nivel. Es lo que muestra el sidebar. */
        String rolPrincipal,
        /** Nivel del rol mas alto. Numero ALTO = MAS acceso. */
        int nivelAcceso) {
}
