package com.sbss.lexia.shared.security;

import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Hace cumplir `usuarios.debe_cambiar_password` en el servidor.
 *
 * Mientras la bandera este activa, el usuario solo puede consultar quien es y
 * cambiar su contrasena; cualquier otra ruta responde 403 con el codigo
 * PASSWORD_CAMBIO_OBLIGATORIO, que Angular usa para llevarlo a esa pantalla.
 *
 * Por que aqui y no solo en Angular: una redireccion del frontend es comodidad,
 * no seguridad. Quien tenga el token puede llamar a la API directamente; sin
 * este filtro, la contrasena temporal de un alta seguiria sirviendo para todo
 * mientras el usuario no pasara por la pantalla.
 *
 * 403 y no 401 a proposito: el usuario SI esta autenticado. Un 401 haria que el
 * interceptor de Angular cerrara la sesion, y no podria llegar a cambiarla.
 *
 * NO lleva @Component, por la misma razon que JwtAuthenticationFilter: lo
 * construye SecurityConfig, o correria dos veces.
 */
public class CambioPasswordObligatorioFilter extends OncePerRequestFilter {

    /** Lo unico permitido mientras la contrasena sea temporal. */
    private static final Set<String> RUTAS_PERMITIDAS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/yo",
            "/api/v1/auth/cambiar-password");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null
                && auth.getPrincipal() instanceof UsuarioPrincipal usuario
                && usuario.isDebeCambiarPassword()
                && !RUTAS_PERMITIDAS.contains(ruta(request))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"timestamp":"%s","estado":403,"codigo":"PASSWORD_CAMBIO_OBLIGATORIO","mensaje":"Debes cambiar tu contrasena antes de continuar","detalles":[]}"""
                    .formatted(LocalDateTime.now()));
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * La ruta sin el context path. No se usa getServletPath(): depende de como
     * este mapeado el servlet (y en MockMvc viene vacio), asi que con el la lista
     * de rutas permitidas dejaria de coincidir en silencio.
     */
    private static String ruta(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
