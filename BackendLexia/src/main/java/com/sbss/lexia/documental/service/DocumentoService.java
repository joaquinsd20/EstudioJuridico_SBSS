package com.sbss.lexia.documental.service;

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
import com.sbss.lexia.documental.entity.Documento;
import com.sbss.lexia.documental.entity.DocumentoVersion;
import com.sbss.lexia.documental.repository.DocumentoConsultaRepository;
import com.sbss.lexia.documental.repository.DocumentoRepository;
import com.sbss.lexia.documental.repository.DocumentoVersionRepository;
import com.sbss.lexia.documental.service.AlmacenamientoDocumentoService.ArchivoGuardado;
import com.sbss.lexia.documental.service.ExportacionDocumentoService.ArchivoExportado;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DocumentoService {

    private static final Set<String> CATEGORIAS = Set.of(
            "DEMANDA", "CONTESTACION", "RECURSO", "ESCRITO", "RESOLUCION", "NOTIFICACION",
            "CARGO", "CONTRATO", "PODER", "PRUEBA", "INFORME", "MULTIMEDIA", "CORREO", "CLIENTE", "OTRO");

    private static final Set<String> ESTADOS = Set.of(
            "BORRADOR", "EN_REVISION", "OBSERVADO", "APROBADO", "FIRMADO", "PRESENTADO", "CERRADO");

    private final DocumentoRepository documentoRepository;
    private final DocumentoVersionRepository versionRepository;
    private final DocumentoConsultaRepository consultaRepository;
    private final AlmacenamientoDocumentoService almacenamiento;
    private final EditorDocumentoService editor;
    private final ExportacionDocumentoService exportacion;

    @Value("${lexia.documentos.papelera-dias:7}")
    private int papeleraDias;

    @Transactional(readOnly = true)
    public List<DocumentoResponse> listar(
            String texto,
            String categoria,
            String estado,
            Long expedienteId,
            Long clienteId,
            Long creadorId,
            String extension,
            Boolean confidencial,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            boolean papelera,
            int limite) {
        return consultaRepository.listar(texto, normalizarOpcional(categoria), normalizarOpcional(estado),
                expedienteId, clienteId, creadorId, normalizarOpcional(extension), confidencial,
                fechaDesde, fechaHasta, papelera, limite);
    }

    @Transactional(readOnly = true)
    public DocumentoResponse obtener(String uuid) {
        DocumentoResponse r = consultaRepository.obtenerPorUuid(uuid);
        if (r == null) throw noEncontrado();
        return r;
    }

    @Transactional(readOnly = true)
    public DocumentoEstadisticasResponse estadisticas() {
        DocumentoEstadisticasResponse e = consultaRepository.estadisticas();
        return e == null ? new DocumentoEstadisticasResponse(0, 0, 0, 0) : e;
    }

    @Transactional
    public DocumentoResponse crear(DocumentoCrearRequest req, MultipartFile archivo, Long usuarioId) {
        validarRelacion(req.expedienteId(), req.actuacionId(), req.tareaId());
        String categoria = validarCategoria(req.categoria());
        String estado = validarEstado(req.estado() == null || req.estado().isBlank() ? "BORRADOR" : req.estado());

        Documento d = new Documento();
        d.setExpedienteId(req.expedienteId());
        d.setActuacionId(req.actuacionId());
        d.setTareaId(req.tareaId());
        d.setNombre(req.nombre().trim());
        d.setDescripcion(limpiar(req.descripcion()));
        d.setCategoria(categoria);
        d.setEstado(estado);
        d.setEsConfidencial(Boolean.TRUE.equals(req.esConfidencial()));
        d.setEnPapelera(false);
        d = documentoRepository.saveAndFlush(d);

        ArchivoGuardado guardado = null;
        try {
            guardado = almacenamiento.guardar(archivo, d.getExpedienteId(), d.getId(), 1);
            DocumentoVersion v = nuevaVersion(d.getId(), 1, guardado, usuarioId, req.observaciones());
            v = versionRepository.saveAndFlush(v);
            d.setVersionVigenteId(v.getId());
            documentoRepository.saveAndFlush(d);
        } catch (RuntimeException e) {
            if (guardado != null) {
                try { almacenamiento.eliminar(guardado.rutaRelativa()); } catch (RuntimeException ignored) { }
            }
            throw e;
        }
        return obtener(d.getUuidPublico());
    }

    @Transactional
    public DocumentoResponse actualizar(String uuid, DocumentoActualizarRequest req) {
        Documento d = obtenerEntidad(uuid);
        asegurarFueraPapelera(d);
        validarRelacion(req.expedienteId(), req.actuacionId(), req.tareaId());
        d.setExpedienteId(req.expedienteId());
        d.setActuacionId(req.actuacionId());
        d.setTareaId(req.tareaId());
        d.setNombre(req.nombre().trim());
        d.setDescripcion(limpiar(req.descripcion()));
        d.setCategoria(validarCategoria(req.categoria()));
        d.setEstado(validarEstado(req.estado()));
        d.setEsConfidencial(Boolean.TRUE.equals(req.esConfidencial()));
        documentoRepository.saveAndFlush(d);
        return obtener(uuid);
    }

    @Transactional
    public DocumentoVersionResponse subirVersion(String uuid, MultipartFile archivo, String observaciones, Long usuarioId) {
        Documento d = obtenerEntidad(uuid);
        asegurarFueraPapelera(d);
        int numero = versionRepository.obtenerUltimaVersion(d.getId()) + 1;
        ArchivoGuardado guardado = almacenamiento.guardar(archivo, d.getExpedienteId(), d.getId(), numero);
        try {
            DocumentoVersion v = nuevaVersion(d.getId(), numero, guardado, usuarioId, observaciones);
            v = versionRepository.saveAndFlush(v);
            d.setVersionVigenteId(v.getId());
            documentoRepository.saveAndFlush(d);
            return aVersionResponse(v, d.getVersionVigenteId(), usuarioId);
        } catch (RuntimeException e) {
            try { almacenamiento.eliminar(guardado.rutaRelativa()); } catch (RuntimeException ignored) { }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentoVersionResponse> versiones(String uuid) {
        Documento d = obtenerEntidad(uuid);
        return consultaRepository.versiones(d.getId(), d.getVersionVigenteId());
    }

    @Transactional(readOnly = true)
    public DocumentoContenidoResponse contenido(String uuid) {
        Documento d = obtenerEntidad(uuid);
        asegurarFueraPapelera(d);
        DocumentoVersion v = versionVigente(d);
        if (!editor.esEditable(v.getExtension())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Este formato no se puede editar. Puedes visualizarlo, descargarlo o exportarlo");
        }

        String extension = v.getExtension() == null ? "" : v.getExtension().toLowerCase(Locale.ROOT);
        String html;

        // IMPORTANTE: el editor web NO usa la conversión PDF -> DOCX de alta
        // fidelidad. Esa conversión puede representar páginas complejas como
        // imágenes completas y, en documentos grandes, vuelve el navegador muy
        // pesado y además esas páginas dejan de ser editables.
        //
        // Para editar un PDF usamos el HTML liviano y editable generado por
        // EditorDocumentoService. La exportación PDF -> Word mantiene, por
        // separado, el modo de alta fidelidad.
        html = editor.extraerHtml(v);

        return new DocumentoContenidoResponse(
                uuid, d.getNombre(), v.getExtension(), html, true);
    }

    @Transactional
    public DocumentoVersionResponse guardarContenido(
            String uuid,
            DocumentoContenidoRequest req,
            Long usuarioId) {
        Documento d = obtenerEntidad(uuid);
        asegurarFueraPapelera(d);
        int numero = versionRepository.obtenerUltimaVersion(d.getId()) + 1;
        Path temp = editor.crearDocxDesdeHtml(req.html());
        ArchivoGuardado guardado = null;
        try {
            String nombre = nombreBase(d.getNombre()) + "_v" + numero + ".docx";
            guardado = almacenamiento.guardar(
                    temp, nombre,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    d.getExpedienteId(), d.getId(), numero);
            DocumentoVersion v = nuevaVersion(d.getId(), numero, guardado, usuarioId,
                    req.observaciones() == null || req.observaciones().isBlank()
                            ? "Edicion realizada desde LEXIA" : req.observaciones());
            v = versionRepository.saveAndFlush(v);
            d.setVersionVigenteId(v.getId());
            documentoRepository.saveAndFlush(d);
            return aVersionResponse(v, d.getVersionVigenteId(), usuarioId);
        } catch (RuntimeException e) {
            if (guardado != null) {
                try { almacenamiento.eliminar(guardado.rutaRelativa()); } catch (RuntimeException ignored) { }
            }
            throw e;
        } finally {
            try { Files.deleteIfExists(temp); } catch (Exception ignored) { }
        }
    }

    @Transactional(readOnly = true)
    public ArchivoDocumento archivoVigente(String uuid) {
        Documento d = obtenerEntidad(uuid);
        asegurarFueraPapelera(d);
        DocumentoVersion v = versionVigente(d);
        Path ruta = almacenamiento.resolver(v.getRutaAlmacenamiento());
        return new ArchivoDocumento(ruta, v.getNombreArchivo(), v.getMimeType());
    }

    @Transactional(readOnly = true)
    public ArchivoExportado exportar(String uuid, String formato) {
        Documento d = obtenerEntidad(uuid);
        asegurarFueraPapelera(d);
        return exportacion.exportar(versionVigente(d), formato, d.getNombre());
    }

    @Transactional
    public void enviarPapelera(String uuid) {
        Documento d = obtenerEntidad(uuid);
        if (Boolean.TRUE.equals(d.getEnPapelera())) return;
        d.setEnPapelera(true);
        d.setFechaPapelera(LocalDateTime.now());
        documentoRepository.save(d);
    }

    @Transactional
    public void restaurar(String uuid) {
        Documento d = obtenerEntidad(uuid);
        d.setEnPapelera(false);
        d.setFechaPapelera(null);
        documentoRepository.save(d);
    }

    @Transactional
    public void eliminarDefinitivamente(String uuid) {
        Documento d = obtenerEntidad(uuid);
        if (!Boolean.TRUE.equals(d.getEnPapelera())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Primero debes enviar el documento a la papelera");
        }
        eliminarEntidad(d);
    }

    @Transactional
    public int eliminarExpirados() {
        LocalDateTime limite = LocalDateTime.now().minusDays(Math.max(1, papeleraDias));
        List<Documento> expirados = documentoRepository.findByEnPapeleraTrueAndFechaPapeleraBefore(limite);
        for (Documento d : expirados) eliminarEntidad(d);
        return expirados.size();
    }

    @Transactional(readOnly = true)
    public List<ExpedienteOpcion> expedientes() {
        return consultaRepository.expedientes();
    }

    @Transactional(readOnly = true)
    public List<UsuarioOpcion> creadores() {
        return consultaRepository.creadores();
    }

    @Transactional(readOnly = true)
    public List<RelacionOpcion> actuaciones(Long expedienteId) {
        return consultaRepository.actuaciones(expedienteId);
    }

    @Transactional(readOnly = true)
    public List<RelacionOpcion> tareas(Long expedienteId) {
        return consultaRepository.tareas(expedienteId);
    }

    private void eliminarEntidad(Documento d) {
        List<DocumentoVersion> versiones = versionRepository.findByDocumentoIdOrderByNumeroVersionDesc(d.getId());
        for (DocumentoVersion v : versiones) {
            try { almacenamiento.eliminar(v.getRutaAlmacenamiento()); } catch (RuntimeException ignored) { }
        }
        d.setVersionVigenteId(null);
        documentoRepository.saveAndFlush(d);
        documentoRepository.delete(d);
        documentoRepository.flush();
    }

    private Documento obtenerEntidad(String uuid) {
        return documentoRepository.findByUuidPublicoAndActivoTrue(uuid).orElseThrow(this::noEncontrado);
    }

    private DocumentoVersion versionVigente(Documento d) {
        if (d.getVersionVigenteId() == null) {
            return versionRepository.findFirstByDocumentoIdOrderByNumeroVersionDesc(d.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "El documento no tiene versiones"));
        }
        return versionRepository.findById(d.getVersionVigenteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontro la version vigente"));
    }

    private void validarRelacion(Long expedienteId, Long actuacionId, Long tareaId) {
        if (!consultaRepository.existeExpediente(expedienteId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El expediente no existe o esta inactivo");
        }
        if (!consultaRepository.actuacionPertenece(actuacionId, expedienteId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La actuacion seleccionada no pertenece al expediente");
        }
        if (!consultaRepository.tareaPertenece(tareaId, expedienteId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La tarea seleccionada no pertenece al expediente");
        }
    }

    private static DocumentoVersion nuevaVersion(
            Long documentoId,
            int numero,
            ArchivoGuardado a,
            Long usuarioId,
            String observaciones) {
        DocumentoVersion v = new DocumentoVersion();
        v.setDocumentoId(documentoId);
        v.setNumeroVersion(numero);
        v.setNombreArchivo(a.nombreOriginal());
        v.setRutaAlmacenamiento(a.rutaRelativa());
        v.setExtension(a.extension());
        v.setMimeType(a.mimeType());
        v.setTamanoBytes(a.tamanoBytes());
        v.setHashSha256(a.sha256());
        v.setUsuarioCargaId(usuarioId);
        v.setObservaciones(limpiar(observaciones));
        return v;
    }

    private static DocumentoVersionResponse aVersionResponse(DocumentoVersion v, Long vigenteId, Long usuarioId) {
        return new DocumentoVersionResponse(
                v.getId(), v.getNumeroVersion(), v.getNombreArchivo(), v.getExtension(), v.getMimeType(),
                v.getTamanoBytes(), v.getHashSha256(), "Usuario " + usuarioId,
                v.getFechaCarga(), v.getObservaciones(), v.getId().equals(vigenteId));
    }

    private static void asegurarFueraPapelera(Documento d) {
        if (Boolean.TRUE.equals(d.getEnPapelera())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El documento esta en la papelera. Restauralo antes de usarlo");
        }
    }

    private ResponseStatusException noEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento no encontrado");
    }

    private static String validarCategoria(String valor) {
        String v = normalizar(valor);
        if (!CATEGORIAS.contains(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria de documento invalida");
        }
        return v;
    }

    private static String validarEstado(String valor) {
        String v = normalizar(valor);
        if (!ESTADOS.contains(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de documento invalido");
        }
        return v;
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizarOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim().toUpperCase(Locale.ROOT);
    }

    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static String nombreBase(String nombre) {
        String n = nombre == null || nombre.isBlank() ? "documento" : nombre.trim();
        n = n.replaceAll("[<>:\"/\\\\|?*]", "_");
        return n.length() > 160 ? n.substring(0, 160) : n;
    }

    public record ArchivoDocumento(Path ruta, String nombreArchivo, String mimeType) {
    }
}
