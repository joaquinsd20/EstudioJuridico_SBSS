package com.sbss.lexia.shared.security;

import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import com.sbss.lexia.seguridad.service.UsuarioDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lee el token de la cabecera Authorization y, si es valido, deja al usuario
 * autenticado en el SecurityContext de ese request.
 *
 * Recarga el usuario desde la base en cada peticion en vez de confiar en las
 * authorities del token. Cuesta una consulta, pero significa que quitarle un rol
 * a alguien tiene efecto inmediato en vez de esperar a que expire su token — en
 * un sistema con expedientes confidenciales, esa diferencia importa. Es tambien
 * la razon por la que `usuarios` se mantuvo ligera: se carga en cada request.
 *
 * NO lleva @Component a proposito. Spring Boot registra automaticamente en la
 * cadena de servlets cualquier Filter que sea bean, asi que anotarlo lo haria
 * correr dos veces: una como filtro global y otra dentro de la cadena de
 * seguridad. Lo construye SecurityConfig.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String CABECERA = "Authorization";
    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioDetailsService usuarioDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String cabecera = request.getHeader(CABECERA);
        if (cabecera == null || !cabecera.startsWith(PREFIJO)) {
            chain.doFilter(request, response);
            return;
        }

        JwtService.TokenLeido token = jwtService.leer(cabecera.substring(PREFIJO.length()));
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UsuarioPrincipal usuario = usuarioDetailsService.loadUserByUsername(token.email());
                // La version de contrasena del token debe ser la vigente: si el
                // usuario la cambio despues de emitirlo, esta sesion ya no vale (V13).
                if (usuario.isEnabled() && usuario.isAccountNonLocked()
                        && token.versionPassword() == JwtService.versionPassword(usuario)) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            usuario, null, usuario.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (UsernameNotFoundException e) {
                // Token valido de un usuario que ya no existe: sigue sin autenticar.
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
