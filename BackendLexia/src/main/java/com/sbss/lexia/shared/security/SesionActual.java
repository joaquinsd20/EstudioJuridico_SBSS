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

    /**
     * True si quien consulta es un usuario del portal del cliente. Los modulos
     * que exponen informacion del expediente deben acotar su consulta a los
     * expedientes de SU cliente — ver clienteId.
     */
    public static boolean esPortalCliente() {
        return principal().esPortalCliente();
    }

    /**
     * El cliente al que pertenece el usuario del portal, o null si es personal
     * del estudio.
     *
     * Ojo al usarlo en una consulta: si un usuario de portal quedara mal
     * configurado (tipoUsuario = CLIENTE pero cliente_id nulo), comparar contra
     * NULL en SQL no casa con ninguna fila y no ve nada. Falla cerrado a
     * proposito; lo contrario seria que lo viera todo.
     */
    public static Long clienteId() {
        return principal().getClienteId();
    }

    /**
     * True si el usuario autenticado tiene ese rol o permiso. Estaba duplicado
     * como metodo privado en varios services; la autoridad se decide en un solo
     * sitio.
     */
    public static boolean tieneAutoridad(String codigo) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> codigo.equals(a.getAuthority()));
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
