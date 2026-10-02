package com.sbss.lexia.reportes.controller;

import com.sbss.lexia.reportes.dto.ReporteDtos.ResumenReporte;
import com.sbss.lexia.reportes.service.ReporteService;
import java.time.LocalDate;
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

    @GetMapping("/exportar/excel")
    @PreAuthorize("hasAuthority('REPORTE_EXPORTAR')")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false, defaultValue = "todas") String materia,
            @RequestParam(required = false) Long abogadoId
    ) {
        byte[] bytes = service.exportarExcel(fechaInicio, fechaFin, materia, abogadoId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.ms-excel"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("reporte_estudio_lexia.xls").build());

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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("reporte_estudio_lexia.pdf").build());

        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
