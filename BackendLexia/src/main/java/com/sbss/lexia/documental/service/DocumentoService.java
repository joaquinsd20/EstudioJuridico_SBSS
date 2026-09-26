package com.sbss.lexia.documental.service;

import com.sbss.lexia.documental.dto.CatalogoDocumentoResponse.ExpedienteOpcion;
import com.sbss.lexia.documental.dto.CatalogoDocumentoResponse.RelacionOpcion;
import com.sbss.lexia.documental.dto.CatalogoDocumentoResponse.UsuarioOpcion;
import com.sbss.lexia.documental.dto.DocumentoActualizarRequest;
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
import java.io.IOException;
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

    private static final Set<String> FORMATOS_DOCUMENTALES = Set.of(
            "pdf", "doc", "docx", "jpg", "jpeg", "png");

    private static final Set<String> FORMATOS_MULTIMEDIA = Set.of(
            "jpg", "jpeg", "png", "mp3", "wav", "mp4");

    private static final Set<String> FORMATOS_DOCUMENTALES_Y_MULTIMEDIA = Set.of(
            "pdf", "doc", "docx", "jpg", "jpeg", "png", "mp3", "wav", "mp4");

    private static final Set<String> FORMATOS_AUDIO_VIDEO = Set.of("mp3", "wav", "mp4");
    private static final Set<String> FORMATOS_IMAGEN = Set.of("jpg", "jpeg", "png");

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
            int limite,
            int desplazamiento) {
        return consultaRepository.listar(texto, normalizarOpcional(categoria), normalizarOpcional(estado),
                expedienteId, clienteId, creadorId, normalizarOpcional(extension), confidencial,
                fechaDesde, fechaHasta, papelera, limite, desplazamiento);
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
            guardado = guardarNormalizado(archivo, d, 1);
            DocumentoVersion v = nuevaVersion(d.getId(), 1, guardado, usuarioId,
                    observacionONormal(req.observaciones(), "Carga inicial del documento"));
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
        String nuevaCategoria = validarCategoria(req.categoria());
        validarCategoriaContraVersionVigente(d, nuevaCategoria);
        d.setCategoria(nuevaCategoria);
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
        ArchivoGuardado guardado = guardarNormalizado(archivo, d, numero);
        try {
            DocumentoVersion v = nuevaVersion(d.getId(), numero, guardado, usuarioId,
                    observacionONormal(observaciones, "Nueva versión cargada"));
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

    @Transactional
    public DocumentoVersionResponse restaurarVersion(
            String uuid,
            Long versionId,
            String observaciones,
            Long usuarioId) {
        Documento d = obtenerEntidad(uuid);
        asegurarFueraPapelera(d);

        DocumentoVersion origen = versionRepository.findById(versionId)
                .filter(v -> v.getDocumentoId().equals(d.getId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "La versión no pertenece a este documento"));

        if (origen.getId().equals(d.getVersionVigenteId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La versión seleccionada ya es la vigente");
        }

        int numero = versionRepository.obtenerUltimaVersion(d.getId()) + 1;
        ArchivoGuardado guardado = copiarVersionNormalizada(origen, d, numero);
        try {
            String detalle = "Restaurada desde la versión v" + origen.getNumeroVersion();
            String obs = observaciones == null || observaciones.isBlank()
                    ? detalle
                    : detalle + ". " + observaciones.trim();
            DocumentoVersion nueva = nuevaVersion(d.getId(), numero, guardado, usuarioId, obs);
            nueva = versionRepository.saveAndFlush(nueva);
            d.setVersionVigenteId(nueva.getId());
            documentoRepository.saveAndFlush(d);
            return aVersionResponse(nueva, d.getVersionVigenteId(), usuarioId);
        } catch (RuntimeException e) {
            try { almacenamiento.eliminar(guardado.rutaRelativa()); } catch (RuntimeException ignored) { }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public DocumentoContenidoResponse contenido(String uuid) {
        Documento d = obtenerEntidad(uuid);
        asegurarFueraPapelera(d);
        DocumentoVersion v = versionVigente(d);
        if (!editor.esEditable(v.getExtension())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se puede generar una vista previa HTML para este formato");
        }

        String html;

        // Se conserva únicamente para previsualizar versiones antiguas que no sean PDF.
        // La edición del contenido del archivo fue retirada del módulo.
        html = editor.extraerHtml(v);

        return new DocumentoContenidoResponse(
                uuid, d.getNombre(), v.getExtension(), html, false);
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

    private ArchivoGuardado guardarNormalizado(
            MultipartFile archivo, Documento documento, int numeroVersion) {
        String extension = extensionCarga(archivo);
        validarFormatoPorCategoria(documento.getCategoria(), extension);

        String expedienteCodigo = consultaRepository.codigoCarpetaExpediente(documento.getExpedienteId());

        if ("pdf".equals(extension)) {
            validarFirmaPdf(archivo);
            return almacenamiento.guardar(archivo, expedienteCodigo, documento.getId(), numeroVersion);
        }

        if ("doc".equals(extension) || "docx".equals(extension)) {
            byte[] pdf = exportacion.convertirWordSubidoAPdf(archivo, extension);
            return guardarBytesComoPdf(
                    pdf,
                    archivo.getOriginalFilename(),
                    "lexia-word-a-pdf-",
                    "No se pudo preparar el PDF convertido desde Word",
                    expedienteCodigo,
                    documento.getId(),
                    numeroVersion);
        }

        if (FORMATOS_IMAGEN.contains(extension)) {
            byte[] pdf = exportacion.convertirImagenSubidaAPdf(archivo, extension);
            return guardarBytesComoPdf(
                    pdf,
                    archivo.getOriginalFilename(),
                    "lexia-imagen-a-pdf-",
                    "No se pudo preparar el PDF convertido desde imagen",
                    expedienteCodigo,
                    documento.getId(),
                    numeroVersion);
        }

        if (FORMATOS_AUDIO_VIDEO.contains(extension)) {
            validarFirmaMultimedia(archivo, extension);
            return almacenamiento.guardar(archivo, expedienteCodigo, documento.getId(), numeroVersion);
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Formato no permitido para la categoría seleccionada");
    }

    private ArchivoGuardado guardarBytesComoPdf(
            byte[] pdf,
            String nombreOriginal,
            String prefijoTemporal,
            String mensajeError,
            String expedienteCodigo,
            Long documentoId,
            int numeroVersion) {
        Path temporal = null;
        try {
            temporal = Files.createTempFile(prefijoTemporal, ".pdf");
            Files.write(temporal, pdf);
            String nombrePdf = nombreSinExtension(nombreOriginal) + ".pdf";
            return almacenamiento.guardar(temporal, nombrePdf, "application/pdf",
                    expedienteCodigo, documentoId, numeroVersion);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, mensajeError, e);
        } finally {
            if (temporal != null) {
                try { Files.deleteIfExists(temporal); } catch (IOException ignored) { }
            }
        }
    }

    private ArchivoGuardado copiarVersionNormalizada(
            DocumentoVersion origen, Documento documento, int numeroVersion) {
        String extension = normalizarExtension(origen.getExtension());
        validarVersionRestaurableEnCategoria(documento.getCategoria(), extension);

        String expedienteCodigo = consultaRepository.codigoCarpetaExpediente(documento.getExpedienteId());
        Path temporal = null;
        try {
            Path original = almacenamiento.resolver(origen.getRutaAlmacenamiento());

            if ("pdf".equals(extension) || FORMATOS_AUDIO_VIDEO.contains(extension)) {
                String mime = origen.getMimeType();
                if (mime == null || mime.isBlank()) {
                    mime = "application/octet-stream";
                }
                String nombre = nombreBase(documento.getNombre())
                        + "_restaurada_v" + origen.getNumeroVersion() + "." + extension;
                return almacenamiento.guardar(original, nombre, mime,
                        expedienteCodigo, documento.getId(), numeroVersion);
            }

            ArchivoExportado convertido = exportacion.exportar(origen, "pdf", documento.getNombre());
            temporal = Files.createTempFile("lexia-restaurar-version-", ".pdf");
            Files.write(temporal, convertido.bytes());
            return almacenamiento.guardar(temporal,
                    nombreBase(documento.getNombre()) + "_restaurada_v" + origen.getNumeroVersion() + ".pdf",
                    "application/pdf", expedienteCodigo, documento.getId(), numeroVersion);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo restaurar la versión seleccionada", e);
        } finally {
            if (temporal != null) {
                try { Files.deleteIfExists(temporal); } catch (IOException ignored) { }
            }
        }
    }

    private static String extensionCarga(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes seleccionar un archivo");
        }
        String nombre = archivo.getOriginalFilename();
        if (nombre == null || nombre.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo no tiene nombre");
        }
        int punto = nombre.lastIndexOf('.');
        if (punto < 0 || punto == nombre.length() - 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El archivo debe tener una extensión válida");
        }
        return nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    private static void validarFormatoPorCategoria(String categoria, String extension) {
        Set<String> permitidos = formatosEntradaPorCategoria(categoria);
        if (!permitidos.contains(extension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Formato no permitido para " + etiquetaCategoria(categoria)
                            + ". Permitidos: " + formatosPermitidosTexto(categoria));
        }
    }

    private void validarCategoriaContraVersionVigente(Documento documento, String nuevaCategoria) {
        if (documento.getVersionVigenteId() == null) return;

        DocumentoVersion vigente = versionVigente(documento);
        String ext = normalizarExtension(vigente.getExtension());

        if ("pdf".equals(ext)) return;
        if (FORMATOS_AUDIO_VIDEO.contains(ext)
                && Set.of("PRUEBA", "MULTIMEDIA", "OTRO").contains(nuevaCategoria)) {
            return;
        }

        // Compatibilidad con versiones antiguas que pudieron quedar guardadas
        // antes de normalizar Word e imágenes a PDF.
        if (formatosEntradaPorCategoria(nuevaCategoria).contains(ext)) return;

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "La versión vigente (" + ext.toUpperCase(Locale.ROOT)
                        + ") no es compatible con la categoría "
                        + etiquetaCategoria(nuevaCategoria));
    }

    private static void validarVersionRestaurableEnCategoria(String categoria, String extension) {
        if ("pdf".equals(extension)) return;

        if (FORMATOS_AUDIO_VIDEO.contains(extension)) {
            if (!Set.of("PRUEBA", "MULTIMEDIA", "OTRO").contains(categoria)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Esta versión multimedia no puede restaurarse en la categoría "
                                + etiquetaCategoria(categoria));
            }
            return;
        }

        validarFormatoPorCategoria(categoria, extension);
    }

    private static Set<String> formatosEntradaPorCategoria(String categoria) {
        return switch (categoria) {
            case "MULTIMEDIA" -> FORMATOS_MULTIMEDIA;
            case "PRUEBA", "OTRO" -> FORMATOS_DOCUMENTALES_Y_MULTIMEDIA;
            default -> FORMATOS_DOCUMENTALES;
        };
    }

    private static String formatosPermitidosTexto(String categoria) {
        return switch (categoria) {
            case "MULTIMEDIA" -> "JPG, JPEG, PNG, MP3, WAV, MP4";
            case "PRUEBA", "OTRO" -> "PDF, DOC, DOCX, JPG, JPEG, PNG, MP3, WAV, MP4";
            default -> "PDF, DOC, DOCX, JPG, JPEG, PNG";
        };
    }

    private static String etiquetaCategoria(String categoria) {
        if (categoria == null || categoria.isBlank()) return "la categoría seleccionada";
        String normal = categoria.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(normal.charAt(0)) + normal.substring(1);
    }

    private static String normalizarExtension(String extension) {
        return extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT).replace(".", "");
    }

    private static void validarFirmaMultimedia(MultipartFile archivo, String extension) {
        try (var in = archivo.getInputStream()) {
            byte[] cabecera = in.readNBytes(64);
            boolean valida = switch (extension) {
                case "mp3" -> esMp3(cabecera);
                case "wav" -> esWav(cabecera);
                case "mp4" -> esMp4(cabecera);
                default -> false;
            };
            if (!valida) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El archivo ." + extension + " no tiene una firma válida o está dañado");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se pudo validar el archivo multimedia", e);
        }
    }

    private static boolean esMp3(byte[] b) {
        if (b.length >= 3 && b[0] == 'I' && b[1] == 'D' && b[2] == '3') return true;
        return b.length >= 2
                && (b[0] & 0xFF) == 0xFF
                && ((b[1] & 0xE0) == 0xE0);
    }

    private static boolean esWav(byte[] b) {
        return b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E';
    }

    private static boolean esMp4(byte[] b) {
        return b.length >= 12
                && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
    }

    private static void validarFirmaPdf(MultipartFile archivo) {
        try (var in = archivo.getInputStream()) {
            byte[] cabecera = in.readNBytes(1024);
            String firma = new String(cabecera, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (!firma.contains("%PDF-")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El archivo indicado como PDF no es un PDF válido");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se pudo validar el archivo PDF", e);
        }
    }

    private static String nombreSinExtension(String nombre) {
        String n = nombre == null || nombre.isBlank() ? "documento" : nombre.trim();
        int punto = n.lastIndexOf('.');
        if (punto > 0) n = n.substring(0, punto);
        return nombreBase(n);
    }

    private static String observacionONormal(String valor, String defecto) {
        String limpio = limpiar(valor);
        return limpio == null ? defecto : limpio;
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

    /**
     * Unico punto por el que todas las operaciones resuelven un uuid a su
     * entidad: editar, subir version, restaurar, papelera, borrar, descargar.
     *
     * findByUuidPublicoAndActivoTrue es una consulta JPA que no sabe nada de
     * estudios ni del portal del cliente, asi que por si sola aceptaria el uuid
     * de un documento ajeno. Se comprueba primero la visibilidad con la
     * consulta acotada — donde vive esa regla, en un solo sitio — y recien
     * despues se carga la entidad. Cuesta una consulta extra por operacion de
     * escritura, a cambio de no duplicar las reglas de visibilidad en dos
     * lenguajes distintos (SQL y JPA), que es como terminan divergiendo.
     *
     * No distingue "no existe" de "no es tuyo": las dos salen 404. Un 403
     * confirmaria que el documento existe en otro estudio.
     */
    private Documento obtenerEntidad(String uuid) {
        if (consultaRepository.obtenerPorUuid(uuid) == null) {
            throw noEncontrado();
        }
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
