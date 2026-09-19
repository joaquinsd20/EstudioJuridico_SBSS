package com.sbss.lexia.seguridad.controller;

import com.sbss.lexia.seguridad.dto.CambiarPasswordRequest;
import com.sbss.lexia.seguridad.dto.LoginRequest;
import com.sbss.lexia.seguridad.dto.LoginResponse;
import com.sbss.lexia.seguridad.dto.UsuarioAutenticadoDto;
import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import com.sbss.lexia.seguridad.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest peticion) {
        return ResponseEntity.ok(authService.login(peticion));
    }

    /**
     * Cambia la contrasena del propio usuario y devuelve un token nuevo: el
     * anterior, como cualquier otro emitido antes del cambio, deja de valer.
     * Es de las pocas rutas permitidas mientras debe_cambiar_password = 1.
     */
    @PostMapping("/cambiar-password")
    public ResponseEntity<LoginResponse> cambiarPassword(
            @AuthenticationPrincipal UsuarioPrincipal usuario,
            @Valid @RequestBody CambiarPasswordRequest peticion) {
        return ResponseEntity.ok(authService.cambiarPassword(usuario.getEmail(), peticion));
    }

    /**
     * Devuelve el usuario del token. Angular lo usa para repintar el sidebar tras
     * recargar la pagina, sin obligar a volver a iniciar sesion.
     */
    @GetMapping("/yo")
    public ResponseEntity<UsuarioAutenticadoDto> yo(
            @AuthenticationPrincipal UsuarioPrincipal usuario) {
        return ResponseEntity.ok(authService.usuarioActual(usuario.getEmail()));
    }
}
