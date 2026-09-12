package com.sbss.lexia.shared.audit;

import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Provee el id del usuario que Spring Data escribe en `usuario_creacion_id` y
 * `usuario_modificacion_id`.
 *
 * Devuelve vacio cuando no hay usuario autenticado — el arranque de Flyway, la
 * semilla o una tarea programada. Ambas columnas admiten NULL justamente por eso.
 */
@Component
public class AuditorActual implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof UsuarioPrincipal usuario)) {
            return Optional.empty();
        }
        return Optional.ofNullable(usuario.getId());
    }
}
