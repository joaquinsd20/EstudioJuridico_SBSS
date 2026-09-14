package com.sbss.lexia.documental.controller;

import com.sbss.lexia.documental.dto.CatalogoDocumentoResponse.ExpedienteOpcion;
import com.sbss.lexia.documental.dto.CatalogoDocumentoResponse.RelacionOpcion;
import com.sbss.lexia.documental.dto.CatalogoDocumentoResponse.UsuarioOpcion;
import com.sbss.lexia.documental.dto.DocumentoActualizarRequest;
import com.sbss.lexia.documental.dto.DocumentoContenidoRequest;
import com.sbss.lexia.documental.dto.DocumentoContenidoResponse;
import com.sbss.lexia.documental.dto.DocumentoCrearRequest;
import com.sbss.lexia.documental.dto.DocumentoEstadisticasResponse;
import com.sbss.lexia.documental.dto.DocumentoResponse;
import com.sbss.lexia.documental.dto.DocumentoVersionResponse;
import com.sbss.lexia.documental.service.DocumentoService;
import com.sbss.lexia.documental.service.DocumentoService.ArchivoDocumento;
import com.sbss.lexia.documental.service.ExportacionDocumentoService.ArchivoExportado;
import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/documentos")
@RequiredArgsConstructor
public class DocumentoController {

    private final DocumentoService documentoService;

    @GetMapping
    public List<DocumentoResponse> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long expedienteId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long creadorId,
            @RequestParam(required = false) String extension,
            @RequestParam(required = false) Boolean confidencial,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(defaultValue = "200") int limite) {
        return documentoService.listar(texto, categoria, estado, expedienteId, clienteId, creadorId,
                extension, confidencial, fechaDesde, fechaHasta, false, limite);
    }

    @GetMapping("/papelera")
    public List<DocumentoResponse> papelera(@RequestParam(defaultValue = "200") int limite) {
        return documentoService.listar(null, null, null, null, null, null,
                null, null, null, null, true, limite);
    }

    @GetMapping("/recientes")
    public List<DocumentoResponse> recientes(@RequestParam(defaultValue = "6") int limite) {
        return documentoService.listar(null, null, null, null, null, null,
                null, null, LocalDate.now().minusDays(7), null, false, limite);
    }

    @GetMapping("/estadisticas")
    public DocumentoEstadisticasResponse estadisticas() {
        return documentoService.estadisticas();
    }

    @GetMapping("/catalogos/expedientes")
    public List<ExpedienteOpcion> expedientes() {
        return documentoService.expedientes();
    }

    @GetMapping("/catalogos/creadores")
    public List<UsuarioOpcion> creadores() {
        return documentoService.creadores();
    }

    @GetMapping("/catalogos/expedientes/{expedienteId}/actuaciones")
    public List<RelacionOpcion> actuaciones(@PathVariable Long expedienteId) {
        return documentoService.actuaciones(expedienteId);
    }

    @GetMapping("/catalogos/expedientes/{expedienteId}/tareas")
    public List<RelacionOpcion> tareas(@PathVariable Long expedienteId) {
        return documentoService.tareas(expedienteId);
    }

    @GetMapping("/{uuid}")
    public DocumentoResponse obtener(@PathVariable String uuid) {
        return documentoService.obtener(uuid);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentoResponse crear(
            @RequestPart("datos") @Valid DocumentoCrearRequest datos,
            @RequestPart("archivo") MultipartFile archivo,
            @AuthenticationPrincipal UsuarioPrincipal usuario) {
        return documentoService.crear(datos, archivo, usuario.getId());
    }

    @PutMapping("/{uuid}")
    public DocumentoResponse actualizar(
            @PathVariable String uuid,
            @RequestBody @Valid DocumentoActualizarRequest datos) {
        return documentoService.actualizar(uuid, datos);
    }

    @PostMapping(value = "/{uuid}/versiones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentoVersionResponse subirVersion(
            @PathVariable String uuid,
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(required = false) String observaciones,
            @AuthenticationPrincipal UsuarioPrincipal usuario) {
        return documentoService.subirVersion(uuid, archivo, observaciones, usuario.getId());
    }

    @GetMapping("/{uuid}/versiones")
    public List<DocumentoVersionResponse> versiones(@PathVariable String uuid) {
        return documentoService.versiones(uuid);
    }

    @GetMapping("/{uuid}/contenido")
    public DocumentoContenidoResponse contenido(@PathVariable String uuid) {
        return documentoService.contenido(uuid);
    }

    @PutMapping("/{uuid}/contenido")
    public DocumentoVersionResponse guardarContenido(
            @PathVariable String uuid,
            @RequestBody @Valid DocumentoContenidoRequest datos,
            @AuthenticationPrincipal UsuarioPrincipal usuario) {
        return documentoService.guardarContenido(uuid, datos, usuario.getId());
    }

    @GetMapping("/{uuid}/visualizar")
    public ResponseEntity<byte[]> visualizar(@PathVariable String uuid) {
        ArchivoDocumento archivo = documentoService.archivoVigente(uuid);
        return respuestaArchivo(archivo, true);
    }

    @GetMapping("/{uuid}/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable String uuid) {
        ArchivoDocumento archivo = documentoService.archivoVigente(uuid);
        return respuestaArchivo(archivo, false);
    }

    @GetMapping("/{uuid}/exportar")
    public ResponseEntity<Resource> exportar(
            @PathVariable String uuid,
            @RequestParam String formato) {
        ArchivoExportado archivo = documentoService.exportar(uuid, formato);
        ByteArrayResource recurso = new ByteArrayResource(archivo.bytes());
        return ResponseEntity.ok()
                .contentType(tipo(archivo.mimeType()))
                .contentLength(archivo.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(archivo.nombreArchivo(), StandardCharsets.UTF_8)
                                .build().toString())
                .body(recurso);
    }

    @PatchMapping("/{uuid}/papelera")
    public ResponseEntity<Void> enviarPapelera(@PathVariable String uuid) {
        documentoService.enviarPapelera(uuid);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{uuid}/restaurar")
    public ResponseEntity<Void> restaurar(@PathVariable String uuid) {
        documentoService.restaurar(uuid);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> eliminarDefinitivamente(@PathVariable String uuid) {
        documentoService.eliminarDefinitivamente(uuid);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<byte[]> respuestaArchivo(ArchivoDocumento archivo, boolean inline) {
        try {
            // Los documentos del sistema tienen un límite de tamaño, por lo que
            // devolver los bytes directamente evita fallos de streaming del
            // FileSystemResource y garantiza que descargar/visualizar responda
            // correctamente mientras el archivo exista físicamente.
            byte[] contenido = Files.readAllBytes(archivo.ruta());

            ContentDisposition disposition = inline
                    ? ContentDisposition.inline()
                            .filename(archivo.nombreArchivo(), StandardCharsets.UTF_8)
                            .build()
                    : ContentDisposition.attachment()
                            .filename(archivo.nombreArchivo(), StandardCharsets.UTF_8)
                            .build();

            return ResponseEntity.ok()
                    .contentType(tipo(archivo.mimeType()))
                    .contentLength(contenido.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(contenido);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo leer el archivo fisico del documento",
                    e);
        }
    }

    private static MediaType tipo(String mime) {
        try {
            return mime == null || mime.isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(mime);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
