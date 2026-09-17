package com.sbss.lexia.biblioteca.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
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
import com.sbss.lexia.biblioteca.dto.RecursoFiltroRequest;
import com.sbss.lexia.biblioteca.entity.RecursoJuridico;
import com.sbss.lexia.biblioteca.service.BibliotecaJuridicaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/biblioteca")
@RequiredArgsConstructor
public class BibliotecaJuridicaController {

    private final BibliotecaJuridicaService servicio;

    // =========================================================
    // ESTADÍSTICAS
    // =========================================================

    @PreAuthorize("hasAuthority('BIBLIOTECA_LEER')")
    @GetMapping("/estadisticas")
    public ResponseEntity<BibliotecaEstadisticasResponse> obtenerEstadisticas() {
        return ResponseEntity.ok(servicio.obtenerEstadisticas());
    }

    // =========================================================
    // LISTAR RECURSOS
    // =========================================================

    @PreAuthorize("hasAuthority('BIBLIOTECA_LEER')")
    @GetMapping("/recursos")
    public ResponseEntity<Page<RecursoJuridico>> listarRecursos(
            RecursoFiltroRequest filtro,
            Pageable pageable) {

        return ResponseEntity.ok(servicio.listar(filtro, pageable));
    }

    // =========================================================
    // OBTENER POR ID
    // =========================================================

    @PreAuthorize("hasAuthority('BIBLIOTECA_LEER')")
    @GetMapping("/recursos/{id}")
    public ResponseEntity<RecursoJuridico> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(servicio.obtenerPorId(id));
    }

    // =========================================================
    // CREAR RECURSO
    // =========================================================

    @PreAuthorize("hasAuthority('BIBLIOTECA_GESTIONAR')")
    @PostMapping(
            value = "/recursos",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<RecursoJuridico> crearRecurso(
            @RequestPart("data") RecursoCrearRequest request,
            @RequestPart(value = "archivo", required = false) MultipartFile archivo) {

        return ResponseEntity.ok(servicio.crear(request, archivo));
    }

    // =========================================================
    // ACTUALIZAR RECURSO
    // =========================================================

    @PreAuthorize("hasAuthority('BIBLIOTECA_GESTIONAR')")
    @PutMapping(
            value = "/recursos/{id}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<RecursoJuridico> actualizarRecurso(
            @PathVariable Long id,
            @RequestPart("data") RecursoCrearRequest request,
            @RequestPart(value = "archivo", required = false) MultipartFile archivo) {

        return ResponseEntity.ok(servicio.actualizar(id, request, archivo));
    }

    // =========================================================
    // VISTA PREVIA
    // =========================================================

    @PreAuthorize("hasAuthority('BIBLIOTECA_LEER')")
    @GetMapping("/recursos/{id}/ver")
    public ResponseEntity<Resource> verArchivo(@PathVariable Long id) {
        return respuestaArchivo(id, true);
    }

    // =========================================================
    // DESCARGAR
    // =========================================================

    @PreAuthorize("hasAuthority('BIBLIOTECA_LEER')")
    @GetMapping("/recursos/{id}/descargar")
    public ResponseEntity<Resource> descargarArchivo(@PathVariable Long id) {
        return respuestaArchivo(id, false);
    }

    // =========================================================
    // RESPUESTA DEL ARCHIVO (STREAMING EFICIENTE CON RESOURCE)
    // =========================================================
        private ResponseEntity<Resource> respuestaArchivo(Long id, boolean inline) {
        RecursoJuridico recurso = servicio.obtenerPorId(id);
        Path rutaArchivo = servicio.obtenerRutaArchivo(id);

        try {
            Resource resource = new UrlResource(rutaArchivo.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "El archivo físico no existe o no se puede leer"
                );
            }

            // Detección del MIME Type directo desde el archivo en disco
            String mimeType = java.nio.file.Files.probeContentType(rutaArchivo);
            if (mimeType == null) {
                mimeType = obtenerMimeType(recurso);
            }

            ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                    .filename(
                            recurso.getNombreArchivo() != null ? recurso.getNombreArchivo() : "archivo",
                            StandardCharsets.UTF_8
                    )
                    .build();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mimeType))
                    .contentLength(resource.contentLength())
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(resource);

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al procesar el archivo del recurso",
                    e
            );
        }
    }

        // =========================================================
        // MIME TYPE MEJORADO
        // =========================================================

    private String obtenerMimeType(RecursoJuridico recurso) {
        String extension = recurso.getExtension();

        // Si extension viene nula o vacía, la extraemos del nombre del archivo
        if ((extension == null || extension.isBlank()) && recurso.getNombreArchivo() != null) {
            int i = recurso.getNombreArchivo().lastIndexOf('.');
            if (i > 0) {
                extension = recurso.getNombreArchivo().substring(i + 1);
            }
        }

        if (extension == null || extension.isBlank()) {
            return "application/octet-stream";
        }

        // Limpieza de formato
        String extLimpia = extension.toLowerCase().replace(".", "").trim();

        return switch (extLimpia) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "txt" -> "text/plain";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
    }

    // =========================================================
    // ELIMINAR
    // =========================================================

    @PreAuthorize("hasAuthority('BIBLIOTECA_GESTIONAR')")
    @DeleteMapping("/recursos/{id}")
    public ResponseEntity<Void> eliminarRecurso(@PathVariable Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}