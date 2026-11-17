package com.sbss.lexia.seguridad.controller;

import com.sbss.lexia.seguridad.service.RecuperacionPasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestParam;
import com.sbss.lexia.seguridad.dto.ActualizarPerfilRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RecuperacionPasswordService recuperacion;

    public record SolicitarRecuperacionRequest(@NotBlank @Email @Size(max = 150) String email) {
    }

    public record RestablecerPasswordRequest(@NotBlank @Size(max = 200) String token,
                                             @NotBlank @Size(max = 200) String passwordNueva) {
    }

    /**
     * "¿Olvidaste tu contrasena?". Responde SIEMPRE lo mismo, exista o no la
     * cuenta: asi no sirve para averiguar que correos estan registrados.
     */
    @PostMapping("/recuperar-password")
    public ResponseEntity<Map<String, String>> solicitarRecuperacion(
            @Valid @RequestBody SolicitarRecuperacionRequest peticion, HttpServletRequest request) {
        try {
            recuperacion.solicitar(peticion.email(), request.getRemoteAddr());
        } catch (RuntimeException e) {
            // Misma respuesta pase lo que pase: un 500 solo para cuentas que
            // existen permitiria averiguar que correos estan registrados.
            LoggerFactory.getLogger(AuthController.class)
                    .warn("Fallo la solicitud de recuperacion de contrasena: {}", e.toString());
        }
        return ResponseEntity.ok(Map.of("mensaje",
                "Si el correo esta registrado, recibiras las instrucciones para restablecer tu contrasena."));
    }

    /** Para avisar en pantalla si el enlace ya vencio, antes de escribir nada. */
    @GetMapping("/restablecer-password/validar")
    public ResponseEntity<Map<String, Boolean>> validarEnlace(@RequestParam String token) {
        return ResponseEntity.ok(Map.of("valido", recuperacion.tokenValido(token)));
    }

    @PostMapping("/restablecer-password")
    public ResponseEntity<Void> restablecer(@Valid @RequestBody RestablecerPasswordRequest peticion) {
        recuperacion.restablecer(peticion.token(), peticion.passwordNueva());
        return ResponseEntity.noContent().build();
    }

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

    /**
     * El usuario actualiza su propio perfil.
     *
     * Sin `@PreAuthorize` a proposito, igual que en `yo` y `cambiar-password`:
     * con un token valido ya se sabe quien es, y estas son de las pocas rutas
     * permitidas mientras `debe_cambiar_password = 1`. El id sale SIEMPRE del
     * token, nunca del cuerpo: por eso un usuario no puede editar el perfil de
     * otro ni aunque mande su id.
     *
     * Devuelve el usuario completo para que el front actualice el nombre del
     * sidebar con la respuesta, sin pedir un `GET /yo` extra.
     */
    @PutMapping("/yo")
    public ResponseEntity<UsuarioAutenticadoDto> actualizarYo(
            @AuthenticationPrincipal UsuarioPrincipal usuario,
            @Valid @RequestBody ActualizarPerfilRequest peticion) {
        return ResponseEntity.ok(authService.actualizarPerfil(usuario.getEmail(), peticion));
    }
}
