package com.sbss.lexia.actuaciones.controller;

import com.sbss.lexia.actuaciones.dto.ActuacionDtos.*;
import com.sbss.lexia.actuaciones.service.ActuacionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/actuaciones")
@RequiredArgsConstructor
public class ActuacionController {

    private final ActuacionService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ACTUACION_LEER')")
    public List<ActuacionLista> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Long tipoId,
            @RequestParam(required = false) Long expedienteId,
            @RequestParam(required = false) Long responsableId,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "250") int limite,
            @RequestParam(defaultValue = "0") int desplazamiento) {
        return service.listar(texto, tipoId, expedienteId, responsableId, estado, fechaDesde, fechaHasta,
                limite, desplazamiento);
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('ACTUACION_LEER')")
    public Resumen resumen() {
        return service.resumen();
    }

    @GetMapping("/catalogos")
    @PreAuthorize("hasAuthority('ACTUACION_LEER')")
    public Catalogos catalogos() {
        return service.catalogos();
    }

    @GetMapping("/catalogos/expedientes/{expedienteId}/documentos")
    @PreAuthorize("hasAuthority('ACTUACION_LEER')")
    public List<DocumentoOpcion> documentos(@PathVariable Long expedienteId) {
        return service.documentosDisponibles(expedienteId);
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ACTUACION_LEER')")
    public ActuacionDetalle detalle(@PathVariable String uuid) {
        return service.detalle(uuid);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ACTUACION_CREAR')")
    public CrearResponse crear(@Valid @RequestBody CrearRequest request) {
        return service.crear(request);
    }
}
