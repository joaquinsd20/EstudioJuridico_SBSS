package com.sbss.lexia.agenda.controller;

import com.sbss.lexia.agenda.dto.AgendaDtos.ActualizarRequest;
import com.sbss.lexia.agenda.dto.AgendaDtos.Catalogos;
import com.sbss.lexia.agenda.dto.AgendaDtos.EventoDetalle;
import com.sbss.lexia.agenda.dto.AgendaDtos.EventoLista;
import com.sbss.lexia.agenda.dto.AgendaDtos.GuardarRequest;
import com.sbss.lexia.agenda.dto.AgendaDtos.ReprogramarRequest;
import com.sbss.lexia.agenda.dto.AgendaDtos.Resumen;
import com.sbss.lexia.agenda.dto.AgendaDtos.ResultadoRequest;
import com.sbss.lexia.agenda.service.AgendaService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agenda")
@RequiredArgsConstructor
public class AgendaController {

    private final AgendaService service;

    @GetMapping
    @PreAuthorize("hasAuthority('AGENDA_LEER')")
    public List<EventoLista> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long expedienteId,
            @RequestParam(required = false) Long participanteId,
            @RequestParam(defaultValue = "250") int limite,
            @RequestParam(defaultValue = "0") int desplazamiento) {
        return service.listar(desde, hasta, texto, tipo, estado, expedienteId, participanteId,
                limite, desplazamiento);
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('AGENDA_LEER')")
    public Resumen resumen() {
        return service.resumen();
    }

    @GetMapping("/catalogos")
    @PreAuthorize("hasAuthority('AGENDA_LEER')")
    public Catalogos catalogos() {
        return service.catalogos();
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('AGENDA_LEER')")
    public EventoDetalle detalle(@PathVariable String uuid) {
        return service.detalle(uuid);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('AGENDA_CREAR')")
    public EventoDetalle crear(@Valid @RequestBody GuardarRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('AGENDA_CREAR')")
    public EventoDetalle actualizar(@PathVariable String uuid, @Valid @RequestBody ActualizarRequest request) {
        return service.actualizar(uuid, request);
    }

    @PatchMapping("/{uuid}/reprogramar")
    @PreAuthorize("hasAuthority('AGENDA_CREAR')")
    public EventoDetalle reprogramar(@PathVariable String uuid, @Valid @RequestBody ReprogramarRequest request) {
        return service.reprogramar(uuid, request);
    }

    @PatchMapping("/{uuid}/resultado")
    @PreAuthorize("hasAuthority('AGENDA_CREAR')")
    public EventoDetalle resultado(@PathVariable String uuid, @Valid @RequestBody ResultadoRequest request) {
        return service.registrarResultado(uuid, request);
    }

    @PatchMapping("/{uuid}/cancelar")
    @PreAuthorize("hasAuthority('AGENDA_CREAR')")
    public EventoDetalle cancelar(@PathVariable String uuid) {
        return service.cancelar(uuid);
    }
}
