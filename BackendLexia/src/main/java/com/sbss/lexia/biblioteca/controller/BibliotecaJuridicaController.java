package com.sbss.lexia.biblioteca.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.sbss.lexia.biblioteca.dto.BibliotecaEstadisticasResponse;
import com.sbss.lexia.biblioteca.dto.RecursoCrearRequest;
import com.sbss.lexia.biblioteca.dto.RecursoDetalleResponse;
import com.sbss.lexia.biblioteca.dto.RecursoFiltroRequest;
import com.sbss.lexia.biblioteca.dto.RecursoListaResponse;
import com.sbss.lexia.biblioteca.service.BibliotecaJuridicaService;
import com.sbss.lexia.shared.dto.PaginaDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/biblioteca")
@RequiredArgsConstructor
public class BibliotecaJuridicaController {

    private final BibliotecaJuridicaService servicio;

    @PreAuthorize("hasAuthority('BIBLIOTECA_LEER')")
    @GetMapping("/estadisticas")
    public BibliotecaEstadisticasResponse obtenerEstadisticas() {
        return servicio.obtenerEstadisticas();
    }

    @PreAuthorize("hasAuthority('BIBLIOTECA_LEER')")
    @GetMapping("/recursos")
    public PaginaDto<RecursoListaResponse> listarRecursos(
            RecursoFiltroRequest filtro,
            Pageable pageable) {
        return servicio.listar(filtro, pageable);
    }

    @PreAuthorize("hasAuthority('BIBLIOTECA_LEER')")
    @GetMapping("/recursos/{uuid}")
    public RecursoDetalleResponse obtener(@PathVariable String uuid) {
        return servicio.obtener(uuid);
    }

    @PreAuthorize("hasAuthority('BIBLIOTECA_GESTIONAR')")
    @PostMapping(value = "/recursos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RecursoDetalleResponse crear(
            @RequestPart("data") RecursoCrearRequest request,
            @RequestPart(value = "archivo", required = false) MultipartFile archivo) {
        return servicio.crear(request, archivo);
    }

    @PreAuthorize("hasAuthority('BIBLIOTECA_GESTIONAR')")
    @PutMapping(value = "/recursos/{uuid}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RecursoDetalleResponse actualizar(
            @PathVariable String uuid,
            @RequestPart("data") RecursoCrearRequest request,
            @RequestPart(value = "archivo", required = false) MultipartFile archivo) {
        return servicio.actualizar(uuid, request, archivo);
    }

    @PreAuthorize("hasAuthority('BIBLIOTECA_LEER')")
    @GetMapping("/recursos/{uuid}/ver")
    public ResponseEntity<Resource> verArchivo(@PathVariable String uuid) {
        RecursoDetalleResponse recurso = servicio.obtener(uuid);
        Path pdf = servicio.obtenerRutaVistaPreviaPdf(uuid);
        String nombrePdf = nombreBase(recurso.nombreArchivo(), recurso.titulo()) + ".pdf";
        return respuestaPdfInline(pdf, nombrePdf);
    }

    @PreAuthorize("hasAuthority('BIBLIOTECA_LEER')")
    @GetMapping("/recursos/{uuid}/descargar")
    public ResponseEntity<Resource> descargarArchivo(@PathVariable String uuid) {
        return respuestaOriginal(uuid);
    }

    @PreAuthorize("hasAuthority('BIBLIOTECA_GESTIONAR')")
    @DeleteMapping("/recursos/{uuid}")
    public ResponseEntity<Void> eliminar(@PathVariable String uuid) {
        servicio.eliminar(uuid);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Resource> respuestaPdfInline(Path ruta, String nombrePdf) {
        try {
            Resource resource = new UrlResource(ruta.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "La vista previa PDF no existe o no se puede leer");
            }

            ContentDisposition disposition = ContentDisposition.inline()
                    .filename(nombrePdf, StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(resource.contentLength())
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                    .header("Content-Security-Policy", "sandbox")
                    .body(resource);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al procesar la vista previa PDF",
                    e);
        }
    }

    private ResponseEntity<Resource> respuestaOriginal(String uuid) {
        RecursoDetalleResponse recurso = servicio.obtener(uuid);
        Path ruta = servicio.obtenerRutaArchivo(uuid);

        try {
            Resource resource = new UrlResource(ruta.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "El archivo físico no existe o no se puede leer");
            }

            String mime = Files.probeContentType(ruta);
            if (mime == null) {
                mime = mimePorExtension(recurso.extension(), recurso.nombreArchivo());
            }

            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(
                            recurso.nombreArchivo() == null ? "archivo" : recurso.nombreArchivo(),
                            StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mime))
                    .contentLength(resource.contentLength())
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(resource);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al descargar el archivo original del recurso",
                    e);
        }
    }

    private String nombreBase(String nombreArchivo, String titulo) {
        String nombre = nombreArchivo;
        if (nombre == null || nombre.isBlank()) {
            nombre = titulo == null || titulo.isBlank() ? "vista-previa" : titulo.trim();
        }

        int punto = nombre.lastIndexOf('.');
        if (punto > 0) {
            nombre = nombre.substring(0, punto);
        }

        nombre = nombre.replaceAll("[\\/:*?\"<>|\r\n]+", "_").trim();
        return nombre.isBlank() ? "vista-previa" : nombre;
    }

    private String mimePorExtension(String extension, String nombreArchivo) {
        String ext = extension;
        if ((ext == null || ext.isBlank()) && nombreArchivo != null) {
            int i = nombreArchivo.lastIndexOf('.');
            if (i >= 0 && i + 1 < nombreArchivo.length()) {
                ext = nombreArchivo.substring(i + 1);
            }
        }

        if (ext == null || ext.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return switch (ext.toLowerCase().replace(".", "").trim()) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }
}
