package com.sbss.lexia.seguridad.service;

import com.sbss.lexia.seguridad.dto.LoginRequest;
import com.sbss.lexia.seguridad.dto.LoginResponse;
import com.sbss.lexia.seguridad.dto.UsuarioAutenticadoDto;
import com.sbss.lexia.seguridad.entity.Rol;
import com.sbss.lexia.seguridad.entity.Usuario;
import com.sbss.lexia.seguridad.repository.UsuarioRepository;
import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Autenticacion: valida credenciales, emite el token y arma la respuesta. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final com.sbss.lexia.shared.security.JwtService jwtService;

    @Transactional
    public LoginResponse login(LoginRequest peticion) {
        // Lanza AuthenticationException si las credenciales no son validas, si la
        // cuenta esta inactiva o si esta bloqueada. El manejador la traduce a 401.
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(peticion.email(), peticion.password()));

        UsuarioPrincipal principal = (UsuarioPrincipal) auth.getPrincipal();

        Usuario usuario = usuarioRepository.findByEmailConRoles(principal.getEmail())
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado sin registro"));

        // El indicador "En linea" del sidebar se resuelve con esta marca.
        usuario.setUltimoAcceso(LocalDateTime.now());
        usuario.setIntentosFallidos((short) 0);

        return new LoginResponse(
                jwtService.generar(principal),
                "Bearer",
                jwtService.getExpiracionSegundos(),
                aDto(usuario));
    }

    /** Los mismos datos que devuelve el login, para repintar tras recargar la pagina. */
    @Transactional(readOnly = true)
    public UsuarioAutenticadoDto usuarioActual(String email) {
        return usuarioRepository.findByEmailConRoles(email)
                .map(this::aDto)
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado sin registro"));
    }

    private UsuarioAutenticadoDto aDto(Usuario usuario) {
        Rol principal = usuario.getRoles().stream()
                .max(Comparator.comparing(Rol::getNivelAcceso))
                .orElse(null);

        List<String> codigos = usuario.getRoles().stream()
                .map(Rol::getCodigo)
                .sorted()
                .toList();

        return new UsuarioAutenticadoDto(
                usuario.getUuidPublico(),
                usuario.getNombreCompleto(),
                usuario.getEmail(),
                usuario.getCargo(),
                usuario.getFotoUrl(),
                Boolean.TRUE.equals(usuario.getDebeCambiarPassword()),
                codigos,
                principal != null ? principal.getNombre() : null,
                principal != null ? principal.getNivelAcceso() : 0);
    }
}
