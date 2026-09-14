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
        /**
         * Codigos de permiso de TODOS los roles del usuario, sin duplicar. Es lo
         * que Angular debe usar para decidir que botones mostrar (p.ej.
         * "CLIENTE_CREAR"), porque es lo mismo contra lo que el backend evalua
         * @PreAuthorize. Antes de este campo, tienePermiso() en el frontend
         * comprobaba contra `roles`, que nunca contiene un codigo de permiso:
         * siempre devolvia false.
         */
        List<String> permisos,
        /** Etiqueta visible del rol de mayor nivel. Es lo que muestra el sidebar. */
        String rolPrincipal,
        /** Nivel del rol mas alto. Numero ALTO = MAS acceso. */
        int nivelAcceso) {
}
