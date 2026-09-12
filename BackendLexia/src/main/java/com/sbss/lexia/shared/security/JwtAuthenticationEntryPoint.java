package com.sbss.lexia.shared.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Sin esto, Spring Security responde 403 a una peticion sin token, igual que a
 * una con permisos insuficientes. Un cliente REST necesita distinguirlas: 401
 * significa "inicia sesion", 403 significa "no tienes permiso para esto". Solo
 * la primera debe hacer que Angular redirija al login.
 *
 * Se escribe el JSON a mano, sin ObjectMapper: esta excepcion ocurre en la
 * cadena de filtros de servlet, antes de DispatcherServlet, asi que
 * @RestControllerAdvice no la intercepta.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"timestamp":"%s","estado":401,"codigo":"NO_AUTENTICADO","mensaje":"Se requiere iniciar sesion","detalles":[]}"""
                .formatted(LocalDateTime.now()));
    }
}
