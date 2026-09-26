package com.sbss.lexia.tareas.controller;

import com.sbss.lexia.tareas.dto.TareaDtos.*;
import com.sbss.lexia.tareas.service.TareaService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tareas")
@RequiredArgsConstructor
public class TareaController {
    private final TareaService service;

    @GetMapping
    @PreAuthorize("hasAuthority('TAREA_LEER')")
    public Pagina<TareaLista> listar(
            @RequestParam(required=false) String texto,
            @RequestParam(required=false) String estado,
            @RequestParam(required=false) String prioridad,
            @RequestParam(required=false) Long responsableId,
            @RequestParam(required=false) Long expedienteId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="24") int size) {
        return service.listar(texto, estado, prioridad, responsableId, expedienteId, desde, hasta, page, size);
    }

    @GetMapping("/exportacion")
    @PreAuthorize("hasAuthority('TAREA_LEER')")
    public List<ExportacionFila> exportacion(
            @RequestParam(required=false) String texto,
            @RequestParam(required=false) String estado,
            @RequestParam(required=false) String prioridad,
            @RequestParam(required=false) Long responsableId,
            @RequestParam(required=false) Long expedienteId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return service.exportacion(texto, estado, prioridad, responsableId, expedienteId, desde, hasta);
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('TAREA_LEER')")
    public Resumen resumen() { return service.resumen(); }

    @GetMapping("/catalogos")
    @PreAuthorize("hasAuthority('TAREA_LEER')")
    public Catalogos catalogos() { return service.catalogos(); }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('TAREA_LEER')")
    public TareaDetalle detalle(@PathVariable String uuid) { return service.detalle(uuid); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TAREA_CREAR')")
    public TareaDetalle crear(@Valid @RequestBody GuardarRequest request) { return service.crear(request); }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('TAREA_CREAR')")
    public TareaDetalle actualizar(@PathVariable String uuid, @Valid @RequestBody GuardarRequest request) {
        return service.actualizar(uuid, request);
    }

    @PatchMapping("/{uuid}/flujo")
    @PreAuthorize("hasAnyAuthority('TAREA_CREAR','TAREA_APROBAR')")
    public TareaDetalle cambiarFlujo(@PathVariable String uuid, @Valid @RequestBody CambiarFlujoRequest request) {
        return service.cambiarFlujo(uuid, request);
    }

    @PatchMapping("/{uuid}/subtareas/{subtareaId}")
    @PreAuthorize("hasAuthority('TAREA_CREAR')")
    public TareaDetalle cambiarSubtarea(@PathVariable String uuid, @PathVariable Long subtareaId) {
        return service.cambiarSubtarea(uuid, subtareaId);
    }

    @PostMapping("/{uuid}/comentarios")
    @PreAuthorize("hasAuthority('TAREA_CREAR')")
    public TareaDetalle comentar(@PathVariable String uuid, @Valid @RequestBody ComentarioRequest request) {
        return service.comentar(uuid, request);
    }

    @PatchMapping("/{uuid}/tiempo")
    @PreAuthorize("hasAuthority('TAREA_CREAR')")
    public TareaDetalle registrarTiempo(@PathVariable String uuid, @Valid @RequestBody TiempoRequest request) {
        return service.registrarTiempo(uuid, request);
    }
}
