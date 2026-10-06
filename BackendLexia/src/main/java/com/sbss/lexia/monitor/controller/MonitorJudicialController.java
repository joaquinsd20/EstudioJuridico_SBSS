package com.sbss.lexia.monitor.controller;

import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.AgregarExpedienteRequest;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.Catalogos;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.Consulta;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.ExpedienteMonitor;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.RegistrarConsultaRequest;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.Resumen;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.VerificarRequest;
import com.sbss.lexia.monitor.service.MonitorJudicialService;
import com.sbss.lexia.shared.dto.PaginaDto;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/monitor-judicial")
@RequiredArgsConstructor
public class MonitorJudicialController {

    private final MonitorJudicialService service;

    /**
     * Compatibilidad con consumidores antiguos que esperan un array.
     * Para la pantalla principal se usa /pagina.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('MONITOR_LEER')")
    public List<ExpedienteMonitor> listar(
            @RequestParam(required = false) String expediente,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String abogado,
            @RequestParam(required = false) String organo,
            @RequestParam(required = false) String estadoProcesal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "250") int limite,
            @RequestParam(defaultValue = "0") int desplazamiento) {
        return service.listar(expediente, cliente, abogado, organo, estadoProcesal, desde, hasta,
                limite, desplazamiento);
    }

    /** Listado escalable con total real y paginacion de servidor. */
    @GetMapping("/pagina")
    @PreAuthorize("hasAuthority('MONITOR_LEER')")
    public PaginaDto<ExpedienteMonitor> listarPagina(
            @RequestParam(required = false) String expediente,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String abogado,
            @RequestParam(required = false) String organo,
            @RequestParam(required = false) String estadoProcesal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return service.listarPagina(expediente, cliente, abogado, organo, estadoProcesal, desde, hasta,
                pagina, tamano);
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('MONITOR_LEER')")
    public Resumen resumen() {
        return service.resumen();
    }

    /**
     * Catalogo limitado y buscable. Evita descargar todos los expedientes del
     * estudio para llenar un select.
     */
    @GetMapping("/catalogos")
    @PreAuthorize("hasAuthority('MONITOR_LEER')")
    public Catalogos catalogos(
            @RequestParam(required = false) String busqueda,
            @RequestParam(defaultValue = "50") int limite) {
        return service.catalogos(busqueda, limite);
    }

    /** Compatibilidad con Dashboard y otros consumidores existentes. */
    @GetMapping("/consultas")
    @PreAuthorize("hasAuthority('MONITOR_LEER')")
    public List<Consulta> consultas(
            @RequestParam(required = false) Long expedienteId,
            @RequestParam(required = false) String tipoMovimiento,
            @RequestParam(required = false) String estadoVerificacion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "250") int limite,
            @RequestParam(defaultValue = "0") int desplazamiento) {
        return service.consultas(expedienteId, tipoMovimiento, estadoVerificacion, desde, hasta,
                limite, desplazamiento);
    }

    /** Historial paginado para la pantalla del Monitor Judicial. */
    @GetMapping("/consultas/pagina")
    @PreAuthorize("hasAuthority('MONITOR_LEER')")
    public PaginaDto<Consulta> consultasPagina(
            @RequestParam(required = false) Long expedienteId,
            @RequestParam(required = false) String tipoMovimiento,
            @RequestParam(required = false) String estadoVerificacion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return service.consultasPagina(expedienteId, tipoMovimiento, estadoVerificacion, desde, hasta,
                pagina, tamano);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MONITOR_REGISTRAR')")
    public Map<String, String> agregar(@Valid @RequestBody AgregarExpedienteRequest request) {
        return Map.of("uuid", service.agregar(request));
    }

    @PostMapping("/consultas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MONITOR_REGISTRAR')")
    public Map<String, String> registrarConsulta(@Valid @RequestBody RegistrarConsultaRequest request) {
        return Map.of("uuid", service.registrarConsulta(request));
    }

    @PatchMapping("/consultas/{uuid}/verificacion")
    @PreAuthorize("hasAuthority('MONITOR_REGISTRAR')")
    public void verificar(@PathVariable String uuid, @Valid @RequestBody VerificarRequest request) {
        service.verificar(uuid, request);
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('MONITOR_REGISTRAR')")
    public void quitar(@PathVariable String uuid) {
        service.quitar(uuid);
    }
}
