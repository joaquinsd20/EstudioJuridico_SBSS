package com.sbss.lexia.shared.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Se dispara cuando el usuario SI esta autenticado pero un @PreAuthorize le
 * niega el acceso (por ejemplo, un ABOGADO_AUXILIAR intentando algo que solo
 * puede el SOCIO). Complementa a JwtAuthenticationEntryPoint, que cubre el caso
 * de no estar autenticado.
 */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"timestamp":"%s","estado":403,"codigo":"ACCESO_DENEGADO","mensaje":"No tienes permiso para realizar esta accion","detalles":[]}"""
                .formatted(LocalDateTime.now()));
    }
}
