package com.sbss.lexia.shared.audit;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

/**
 * Provee el id del usuario que Spring Data escribe en `usuario_creacion_id` y
 * `usuario_modificacion_id`.
 *
 * Hoy devuelve vacio porque todavia no existe el modulo de seguridad: sin JWT no
 * hay usuario autenticado del que sacar el id, y ambas columnas admiten NULL.
 * Cuando exista `UsuarioPrincipal`, este metodo lo leera del SecurityContext.
 */
@Component
public class AuditorActual implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        return Optional.empty();
    }
}
