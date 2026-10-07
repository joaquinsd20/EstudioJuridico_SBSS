package com.sbss.lexia.reportes.controller;

import com.sbss.lexia.reportes.dto.ReporteDtos.CatalogosReporte;
import com.sbss.lexia.reportes.dto.ReporteDtos.ItemReporteDetalle;
import com.sbss.lexia.reportes.dto.ReporteDtos.ResumenReporte;
import com.sbss.lexia.reportes.service.ReporteService;
import com.sbss.lexia.shared.dto.PaginaDto;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private static final DateTimeFormatter ARCHIVO_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm");

    private final ReporteService service;

    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('REPORTE_LEER')")
    public ResumenReporte resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false, defaultValue = "todas") String materia,
            @RequestParam(required = false) Long abogadoId,
            @RequestParam(required = false, defaultValue = "3meses") String preset
    ) {
        return service.obtenerResumenReportes(fechaInicio, fechaFin, materia, abogadoId, preset);
    }

    @GetMapping("/detalle")
    @PreAuthorize("hasAuthority('REPORTE_LEER')")
    public PaginaDto<ItemReporteDetalle> detalle(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false, defaultValue = "todas") String materia,
            @RequestParam(required = false) Long abogadoId,
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.obtenerDetallePagina(fechaInicio, fechaFin, materia, abogadoId, texto, page, size);
    }

    @GetMapping("/catalogos")
    @PreAuthorize("hasAuthority('REPORTE_LEER')")
    public CatalogosReporte catalogos() {
        return service.catalogos();
    }

    @GetMapping("/exportar/datos")
    @PreAuthorize("hasAuthority('REPORTE_EXPORTAR')")
    public List<ItemReporteDetalle> datosExportacion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false, defaultValue = "todas") String materia,
            @RequestParam(required = false) Long abogadoId
    ) {
        return service.obtenerDatosExportacion(fechaInicio, fechaFin, materia, abogadoId);
    }

    @GetMapping("/exportar/excel")
    @PreAuthorize("hasAuthority('REPORTE_EXPORTAR')")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false, defaultValue = "todas") String materia,
            @RequestParam(required = false) Long abogadoId
    ) {
        byte[] bytes = service.exportarExcel(fechaInicio, fechaFin, materia, abogadoId);
        String nombre = "reporte-lexia-" + LocalDateTime.now().format(ARCHIVO_TS) + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(nombre).build());

        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @GetMapping("/exportar/pdf")
    @PreAuthorize("hasAuthority('REPORTE_EXPORTAR')")
    public ResponseEntity<byte[]> exportarPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false, defaultValue = "todas") String materia,
            @RequestParam(required = false) Long abogadoId
    ) {
        byte[] bytes = service.exportarPdf(fechaInicio, fechaFin, materia, abogadoId);
        String nombre = "reporte-lexia-" + LocalDateTime.now().format(ARCHIVO_TS) + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(nombre).build());

        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
