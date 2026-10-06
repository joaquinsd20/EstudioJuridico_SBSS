package com.sbss.lexia.biblioteca.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.sbss.lexia.documental.service.ExportacionDocumentoService;


/**
 * Conversión de archivos de Biblioteca Jurídica.
 *
 * Biblioteca acepta únicamente:
 * - PDF: se conserva como PDF.
 * - DOC / DOCX: se convierten a PDF usando EXACTAMENTE el mismo conversor
 *   que utiliza el Panel Documentos (ExportacionDocumentoService).
 *
 * ExportacionDocumentoService no se modifica.
 */
@Service
public class ExportacionBibliotecaService {

    private final ExportacionDocumentoService exportacionDocumentoService;
    private final long maxBytes;

    public ExportacionBibliotecaService(
            ExportacionDocumentoService exportacionDocumentoService,
            @Value("${lexia.biblioteca.max-file-size-bytes:52428800}") long maxBytes) {
        this.exportacionDocumentoService = exportacionDocumentoService;
        this.maxBytes = maxBytes;
    }

    /**
     * Normaliza un archivo de entrada para que Biblioteca lo almacene como PDF.
     *
     * PDF se devuelve sin alterar.
     * DOC y DOCX se convierten con el mismo servicio de Panel Documentos.
     */
    public MultipartFile normalizarAPdf(MultipartFile archivo) {
        validarArchivo(archivo);
        if (archivo.getSize() > maxBytes) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "El archivo supera el tamaño máximo permitido de " + (maxBytes / 1024 / 1024) + " MB");
        }

        String nombre = nombreSeguro(archivo.getOriginalFilename());
        String extension = extension(nombre);

        if ("pdf".equals(extension)) {
            return archivo;
        }

        if (!"doc".equals(extension) && !"docx".equals(extension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Formato no permitido. Solo se aceptan PDF, DOC y DOCX");
        }

        byte[] pdf = exportacionDocumentoService.convertirWordSubidoAPdf(archivo, extension);
        String nombrePdf = nombreSinExtension(nombre) + ".pdf";

        return new MultipartFileEnMemoria(
                "archivo",
                nombrePdf,
                "application/pdf",
                pdf);
    }

    /**
     * Convierte un Word antiguo ya almacenado a PDF para poder mostrarlo en el
     * visor. Esto mantiene compatibilidad con recursos creados antes de que
     * Biblioteca normalizara DOC/DOCX a PDF al momento de subirlos.
     */
    public byte[] convertirWordExistenteAPdf(
            Path archivo,
            String nombreOriginal,
            String extensionRegistrada) {

        if (archivo == null || !Files.isRegularFile(archivo)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "El archivo original no existe");
        }

        String extension = normalizarExtension(extensionRegistrada);
        String nombre = nombreOriginal;

        if (extension == null || extension.isBlank()) {
            nombre = nombreSeguro(nombreOriginal != null ? nombreOriginal : archivo.getFileName().toString());
            extension = extension(nombre);
        }

        if (!"doc".equals(extension) && !"docx".equals(extension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Solo se puede convertir Word DOC o DOCX");
        }

        if (nombre == null || nombre.isBlank()) {
            nombre = "documento." + extension;
        }

        MultipartFile multipart = new MultipartFileDesdeRuta(
                "archivo",
                nombre,
                mimeWord(extension),
                archivo);

        return exportacionDocumentoService.convertirWordSubidoAPdf(multipart, extension);
    }

    private static void validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty() || archivo.getSize() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debes seleccionar un archivo válido");
        }
    }

    private static String nombreSeguro(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo debe tener nombre");
        }

        String seguro = Path.of(nombre)
                .getFileName()
                .toString()
                .replaceAll("[\\r\\n\\t]", "_")
                .trim();

        if (seguro.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo debe tener nombre");
        }

        return seguro;
    }

    private static String extension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        if (punto < 0 || punto == nombre.length() - 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El archivo debe tener extensión PDF, DOC o DOCX");
        }

        return normalizarExtension(nombre.substring(punto + 1));
    }

    private static String normalizarExtension(String extension) {
        if (extension == null) {
            return null;
        }
        return extension
                .toLowerCase(Locale.ROOT)
                .replace(".", "")
                .trim();
    }

    private static String nombreSinExtension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto > 0 ? nombre.substring(0, punto) : nombre;
    }

    private static String mimeWord(String extension) {
        return "docx".equals(extension)
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                : "application/msword";
    }

    private static final class MultipartFileEnMemoria implements MultipartFile {
        private final String campo;
        private final String nombreOriginal;
        private final String contentType;
        private final byte[] contenido;

        private MultipartFileEnMemoria(
                String campo,
                String nombreOriginal,
                String contentType,
                byte[] contenido) {
            this.campo = campo;
            this.nombreOriginal = nombreOriginal;
            this.contentType = contentType;
            this.contenido = contenido == null ? new byte[0] : contenido;
        }

        @Override
        public String getName() {
            return campo;
        }

        @Override
        public String getOriginalFilename() {
            return nombreOriginal;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return contenido.length == 0;
        }

        @Override
        public long getSize() {
            return contenido.length;
        }

        @Override
        public byte[] getBytes() {
            return contenido.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(contenido);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.write(dest.toPath(), contenido);
        }
    }

    private static final class MultipartFileDesdeRuta implements MultipartFile {
        private final String campo;
        private final String nombreOriginal;
        private final String contentType;
        private final Path archivo;

        private MultipartFileDesdeRuta(
                String campo,
                String nombreOriginal,
                String contentType,
                Path archivo) {
            this.campo = campo;
            this.nombreOriginal = nombreOriginal;
            this.contentType = contentType;
            this.archivo = archivo;
        }

        @Override
        public String getName() {
            return campo;
        }

        @Override
        public String getOriginalFilename() {
            return nombreOriginal;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            try {
                return !Files.isRegularFile(archivo) || Files.size(archivo) <= 0;
            } catch (IOException e) {
                return true;
            }
        }

        @Override
        public long getSize() {
            try {
                return Files.size(archivo);
            } catch (IOException e) {
                return 0L;
            }
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(archivo);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(archivo);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.copy(archivo, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
