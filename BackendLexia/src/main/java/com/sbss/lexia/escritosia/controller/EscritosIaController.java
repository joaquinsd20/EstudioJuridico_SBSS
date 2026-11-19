package com.sbss.lexia.escritosia.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sbss.lexia.escritosia.dto.EscritosIaDtos.EscritoGenerado;
import com.sbss.lexia.escritosia.dto.EscritosIaDtos.ExportarEscritoRequest;
import com.sbss.lexia.escritosia.dto.EscritosIaDtos.GenerarRequest;
import com.sbss.lexia.escritosia.dto.EscritosIaDtos.TipoEscrito;
import com.sbss.lexia.escritosia.service.EscritosIaDocumentoService;
import com.sbss.lexia.escritosia.service.EscritosIaDocumentoService.ArchivoGenerado;
import com.sbss.lexia.escritosia.service.EscritosIaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/escritos-ia")
@RequiredArgsConstructor
public class EscritosIaController {

    private final EscritosIaService service;
    private final EscritosIaDocumentoService documentoService;

    @GetMapping("/tipos")
    @PreAuthorize("hasAuthority('DOCUMENTO_CREAR')")
    public List<TipoEscrito> tipos() {
        return service.tipos();
    }

    @PostMapping("/generar")
    @PreAuthorize("hasAuthority('DOCUMENTO_CREAR')")
    public ResponseEntity<EscritoGenerado> generar(@Valid @RequestBody GenerarRequest request) {
        return ResponseEntity.ok(service.generar(request));
    }

    @PostMapping(value = "/exportar/word", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('DOCUMENTO_CREAR')")
    public ResponseEntity<byte[]> exportarWord(@Valid @RequestBody ExportarEscritoRequest request) {
        return respuestaArchivo(documentoService.generarWord(request.titulo(), request.contenido()));
    }

    @PostMapping(value = "/exportar/pdf", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('DOCUMENTO_CREAR')")
    public ResponseEntity<byte[]> exportarPdf(@Valid @RequestBody ExportarEscritoRequest request) {
        return respuestaArchivo(documentoService.generarPdf(request.titulo(), request.contenido()));
    }

    private ResponseEntity<byte[]> respuestaArchivo(ArchivoGenerado archivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.mimeType()))
                .contentLength(archivo.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(archivo.nombreArchivo(), StandardCharsets.UTF_8)
                                .build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0")
                .body(archivo.bytes());
    }
}
