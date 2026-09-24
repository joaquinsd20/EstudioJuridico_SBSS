package com.sbss.lexia.plazos.controller;

import com.sbss.lexia.plazos.dto.PlazoDtos.*;
import com.sbss.lexia.plazos.service.PlazoService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/plazos")
@RequiredArgsConstructor
public class PlazoController {

    private final PlazoService service;

    @GetMapping
    @PreAuthorize("hasAuthority('PLAZO_LEER')")
    public List<PlazoLista> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Long expedienteId,
            @RequestParam(required = false) Long responsableId,
            @RequestParam(required = false) Long tipoPlazoId,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String riesgo,
            @RequestParam(required = false) String computo,
            @RequestParam(required = false) Boolean requiereRevision,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.listar(texto, expedienteId, responsableId, tipoPlazoId, estado, riesgo,
                computo, requiereRevision, desde, hasta);
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('PLAZO_LEER')")
    public Resumen resumen() { return service.resumen(); }

    @GetMapping("/catalogos")
    @PreAuthorize("hasAuthority('PLAZO_LEER')")
    public Catalogos catalogos() { return service.catalogos(); }

    @GetMapping("/relaciones/{expedienteId}")
    @PreAuthorize("hasAuthority('PLAZO_LEER')")
    public Relaciones relaciones(@PathVariable Long expedienteId) { return service.relaciones(expedienteId); }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PLAZO_LEER')")
    public PlazoDetalle detalle(@PathVariable String uuid) { return service.detalle(uuid); }

    @PostMapping("/calcular")
    @PreAuthorize("hasAuthority('PLAZO_CREAR')")
    public Calculo calcular(@Valid @RequestBody CalcularRequest request) { return service.calcular(request); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PLAZO_CREAR')")
    public PlazoDetalle crear(@Valid @RequestBody GuardarRequest request) { return service.crear(request); }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PLAZO_CREAR')")
    public PlazoDetalle actualizar(@PathVariable String uuid, @Valid @RequestBody GuardarRequest request) {
        return service.actualizar(uuid, request);
    }

    @PatchMapping("/{uuid}/estado/{estado}")
    @PreAuthorize("hasAnyAuthority('PLAZO_CREAR','PLAZO_APROBAR')")
    public PlazoDetalle cambiarEstado(@PathVariable String uuid, @PathVariable String estado) {
        return service.cambiarEstado(uuid, estado);
    }
}
