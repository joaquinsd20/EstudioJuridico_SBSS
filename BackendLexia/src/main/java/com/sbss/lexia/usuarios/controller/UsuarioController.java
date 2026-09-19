package com.sbss.lexia.usuarios.controller;

import com.sbss.lexia.usuarios.dto.UsuarioDtos.CredencialTemporal;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.GuardarUsuarioRequest;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.RolOpcion;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.UsuarioDetalle;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.UsuarioLista;
import com.sbss.lexia.usuarios.service.UsuarioService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Personal del estudio (RF-01).
 *
 * Todo exige USUARIO_GESTIONAR: quien administra cuentas puede, de hecho,
 * darse acceso a cualquier cosa. Por eso solo lo tienen Administrador y Socio.
 * Los nombres del personal que necesitan otras pantallas para sus selectores ya
 * salen de sus propios catalogos, sin pasar por aqui.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    public List<UsuarioLista> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) String estado) {
        return usuarioService.listar(texto, rol, estado);
    }

    /** Roles asignables, para el selector del formulario. */
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    public List<RolOpcion> roles() {
        return usuarioService.roles();
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    public UsuarioDetalle detalle(@PathVariable String uuid) {
        return usuarioService.detalle(uuid);
    }

    /** Alta. Devuelve la contrasena temporal UNA sola vez. */
    @PostMapping
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    public ResponseEntity<CredencialTemporal> crear(@Valid @RequestBody GuardarUsuarioRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crear(peticion));
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    public ResponseEntity<Void> actualizar(
            @PathVariable String uuid, @Valid @RequestBody GuardarUsuarioRequest peticion) {
        usuarioService.actualizar(uuid, peticion);
        return ResponseEntity.noContent().build();
    }

    /**
     * Baja logica. No hay DELETE: un usuario es responsable de expedientes y
     * plazos con FK NOT NULL, asi que borrarlo es imposible por diseno (regla 5).
     */
    @PostMapping("/{uuid}/desactivar")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    public ResponseEntity<Void> desactivar(@PathVariable String uuid) {
        usuarioService.desactivar(uuid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uuid}/activar")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    public ResponseEntity<Void> activar(@PathVariable String uuid) {
        usuarioService.activar(uuid);
        return ResponseEntity.noContent().build();
    }

    /** Quita el bloqueo por intentos fallidos de login. */
    @PostMapping("/{uuid}/desbloquear")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    public ResponseEntity<Void> desbloquear(@PathVariable String uuid) {
        usuarioService.desbloquear(uuid);
        return ResponseEntity.noContent().build();
    }

    /** Contrasena temporal nueva. Cierra las sesiones abiertas de esa persona. */
    @PostMapping("/{uuid}/restablecer-password")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
    public CredencialTemporal restablecerPassword(@PathVariable String uuid) {
        return usuarioService.restablecerPassword(uuid);
    }
}
