package com.sbss.lexia.shared.config;

import com.sbss.lexia.seguridad.service.UsuarioDetailsService;
import com.sbss.lexia.shared.security.ApiAccessDeniedHandler;
import com.sbss.lexia.shared.security.CambioPasswordObligatorioFilter;
import com.sbss.lexia.shared.security.JwtAuthenticationEntryPoint;
import com.sbss.lexia.shared.security.JwtAuthenticationFilter;
import com.sbss.lexia.shared.security.JwtService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Seguridad de la API.
 *
 * @EnableMethodSecurity habilita @PreAuthorize, que en LEXIA siempre se escribe
 * contra `roles.codigo` o `permisos.codigo`, nunca contra la etiqueta visible.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;
    private final UsuarioDetailsService usuarioDetailsService;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final ApiAccessDeniedHandler apiAccessDeniedHandler;

    @Value("${lexia.cors.origenes}")
    private List<String> origenesPermitidos;

    /** BCrypt cost 12, como fija CLAUDE.md. Debe coincidir con el de la semilla. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origenesPermitidos);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                // Sin CSRF: la API es stateless y no usa cookies de sesion, asi que
                // no hay nada que un sitio de terceros pueda reenviar en tu nombre.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        // Recuperar contrasena: por definicion, quien la usa no tiene sesion.
                        .requestMatchers("/api/v1/auth/recuperar-password",
                                "/api/v1/auth/restablecer-password",
                                "/api/v1/auth/restablecer-password/validar").permitAll()
                        // Spring Boot reenvia internamente a /error para renderizar
                        // cualquier excepcion no manejada (415, 500, etc.). Sin esto,
                        // ese reenvio vuelve a pasar por seguridad, se rechaza por no
                        // estar autenticado, y el cliente recibe 401 "NO_AUTENTICADO"
                        // en vez del error real — un 500 por un bug se veria identico
                        // a un problema de login. Se confirmo pidiendo /error directo:
                        // devolvia 401 antes de esta linea.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, usuarioDetailsService),
                        UsernamePasswordAuthenticationFilter.class)
                // Despues del JWT: necesita al usuario ya autenticado para leer
                // su bandera debe_cambiar_password.
                .addFilterAfter(new CambioPasswordObligatorioFilter(), JwtAuthenticationFilter.class)
                .build();
    }
}
