package com.sbss.lexia.expedientes.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;

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

import com.sbss.lexia.expedientes.dto.ExpedienteDtos.Catalogos;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.EventoLineaTiempo;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.ExpedienteDetalle;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.ExpedienteLista;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.ExpedienteOpcion;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.GuardarRequest;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.Resumen;
import com.sbss.lexia.expedientes.service.ExpedienteService;
import com.sbss.lexia.shared.dto.PaginaDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/expedientes")
@RequiredArgsConstructor
public class ExpedienteController {

    private final ExpedienteService service;

    @GetMapping
    @PreAuthorize("hasAuthority('EXPEDIENTE_LEER')")
    public PaginaDto<ExpedienteLista> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long materiaId,
            @RequestParam(required = false) String organo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listar(texto, cliente, estado, materiaId, organo, page, size);
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('EXPEDIENTE_LEER')")
    public Resumen resumen() {
        return service.resumen();
    }

    /**
     * Selector de expedientes para los demas modulos. Acotado a 20 por
     * defecto: los /catalogos que vuelcan todo cuestan ~1 MB por peticion.
     */
    @GetMapping("/buscar")
    @PreAuthorize("hasAuthority('EXPEDIENTE_LEER')")
    public List<ExpedienteOpcion> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "20") int limite) {
        return service.buscar(texto, limite);
    }

    @GetMapping("/catalogos")
    @PreAuthorize("hasAuthority('EXPEDIENTE_LEER')")
    public Catalogos catalogos() {
        return service.catalogos();
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('EXPEDIENTE_LEER')")
    public ExpedienteDetalle obtener(@PathVariable String uuid) {
        return service.obtener(uuid);
    }

    @GetMapping("/{uuid}/linea-tiempo")
    @PreAuthorize("hasAuthority('EXPEDIENTE_LEER')")
    public List<EventoLineaTiempo> lineaTiempo(@PathVariable String uuid) {
        return service.lineaTiempo(uuid);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EXPEDIENTE_CREAR')")
    public ResponseEntity<Map<String, String>> crear(@Valid @RequestBody GuardarRequest request) {
        String uuid = service.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/expedientes/" + uuid))
                .body(Map.of("uuid", uuid));
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('EXPEDIENTE_EDITAR')")
    public ResponseEntity<Void> actualizar(@PathVariable String uuid, @Valid @RequestBody GuardarRequest request) {
        service.actualizar(uuid, request);
        return ResponseEntity.noContent().build();
    }
}
