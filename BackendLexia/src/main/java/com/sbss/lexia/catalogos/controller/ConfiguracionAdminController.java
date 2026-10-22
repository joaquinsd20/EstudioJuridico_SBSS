package com.sbss.lexia.catalogos.controller;

import com.sbss.lexia.catalogos.controller.ConfiguracionAdminDtos.ActualizarEstudioRequest;
import com.sbss.lexia.catalogos.controller.ConfiguracionAdminDtos.ActualizarParametrosRequest;
import com.sbss.lexia.catalogos.controller.ConfiguracionAdminDtos.EstudioResponse;
import com.sbss.lexia.catalogos.controller.ConfiguracionAdminDtos.ParametrosResponse;
import com.sbss.lexia.catalogos.service.ConfiguracionAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuración institucional. Solo el permiso administrativo CONFIGURACION_EDITAR puede operar aquí. */
@RestController
@RequestMapping("/api/v1/configuracion")
@RequiredArgsConstructor
public class ConfiguracionAdminController {

    private final ConfiguracionAdminService configuracionService;

    @GetMapping("/estudio")
    @PreAuthorize("hasAuthority('CONFIGURACION_EDITAR')")
    public EstudioResponse obtenerEstudio() {
        return configuracionService.obtenerEstudio();
    }

    @PutMapping("/estudio")
    @PreAuthorize("hasAuthority('CONFIGURACION_EDITAR')")
    public EstudioResponse actualizarEstudio(
            @Valid @RequestBody ActualizarEstudioRequest request,
            HttpServletRequest contexto) {
        return configuracionService.actualizarEstudio(request, contexto);
    }

    @GetMapping("/parametros")
    @PreAuthorize("hasAuthority('CONFIGURACION_EDITAR')")
    public ParametrosResponse obtenerParametros() {
        return configuracionService.obtenerParametros();
    }

    @PutMapping("/parametros")
    @PreAuthorize("hasAuthority('CONFIGURACION_EDITAR')")
    public ParametrosResponse actualizarParametros(
            @Valid @RequestBody ActualizarParametrosRequest request,
            HttpServletRequest contexto) {
        return configuracionService.actualizarParametros(request, contexto);
    }
}
