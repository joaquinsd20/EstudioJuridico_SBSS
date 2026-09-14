package com.sbss.lexia.shared.security;

import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Datos del usuario autenticado, leidos del SecurityContext.
 *
 * El motivo de que exista: `estudioId` es la frontera de multi-tenencia de
 * todo el CRM, y un `service` no deberia depender de que el `controller` se
 * acuerde de extraerlo de `@AuthenticationPrincipal` y pasarlo como parametro.
 * Con este helper el service lo lee directamente, sin acoplarse a como llego
 * la peticion.
 *
 * Regla de CLAUDE.md: ningun metodo de repositorio del CRM consulta sin
 * `estudio_id`. Este es el unico lugar del que ese valor debe salir.
 */
public final class SesionActual {

    private SesionActual() {
    }

    /** El estudio del usuario autenticado. Lanza si no hay sesion valida. */
    public static Integer estudioId() {
        return principal().getEstudioId();
    }

    public static Long usuarioId() {
        return principal().getId();
    }

    public static String uuid() {
        return principal().getUuid();
    }

    private static UsuarioPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioPrincipal principal)) {
            // No deberia ocurrir en un endpoint protegido: si sucede, es un error
            // de configuracion (endpoint sin seguridad) y debe fallar ruidoso,
            // no devolver un estudioId nulo que filtraria datos entre estudios.
            throw new IllegalStateException(
                    "No hay un UsuarioPrincipal autenticado en el contexto de seguridad");
        }
        return principal;
    }
}
