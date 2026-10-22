package com.sbss.lexia.seguridad.preferencias.controller;

import com.sbss.lexia.seguridad.preferencias.service.PreferenciasUsuarioService;
import com.sbss.lexia.seguridad.preferencias.service.PreferenciasUsuarioService.GuardarPreferenciasRequest;
import com.sbss.lexia.seguridad.preferencias.service.PreferenciasUsuarioService.PreferenciasDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;

/** Solo expone las preferencias del usuario autenticado; nunca acepta usuarioId desde el cliente. */
@RestController
@RequestMapping("/api/v1/mi-configuracion")
@RequiredArgsConstructor
public class PreferenciasUsuarioController {

    private final PreferenciasUsuarioService preferenciasService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PreferenciasDto obtener() {
        return preferenciasService.obtener();
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public PreferenciasDto guardar(@Valid @RequestBody GuardarRequest request, HttpServletRequest contexto) {
        if (request.tema() == null || request.tema().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un tema válido.");
        }
        String tema = request.tema().trim().toUpperCase(Locale.ROOT);
        if (!"CLARO".equals(tema) && !"OSCURO".equals(tema)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tema debe ser CLARO u OSCURO.");
        }
        return preferenciasService.guardar(new GuardarPreferenciasRequest(tema, request.sidebarColapsado()), contexto);
    }

    public record GuardarRequest(
            @Pattern(regexp = "(?i)CLARO|OSCURO", message = "El tema debe ser CLARO u OSCURO") String tema,
            boolean sidebarColapsado) {
    }
}
