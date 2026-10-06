package com.sbss.lexia.biblioteca.service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Resuelve el PDF que será mostrado por el visor de Biblioteca Jurídica.
 *
 * Nuevos recursos:
 * - PDF se almacena como PDF.
 * - DOC / DOCX se convierten a PDF al momento de subirlos.
 *
 * Compatibilidad:
 * - Si existe un recurso antiguo guardado como DOC/DOCX, se convierte bajo
 *   demanda usando ExportacionBibliotecaService, que reutiliza exactamente
 *   ExportacionDocumentoService del Panel Documentos.
 */
@Service
@RequiredArgsConstructor
public class VistaPreviaBibliotecaService {

    private final ExportacionBibliotecaService exportacionBibliotecaService;

    private final ConcurrentHashMap<String, ReentrantLock> bloqueos = new ConcurrentHashMap<>();

    public Path obtenerPdf(Path original, String extension, String nombreOriginal) {
        if (original == null || !Files.isRegularFile(original)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "El archivo original del recurso no existe");
        }

        String ext = normalizarExtension(extension, nombreOriginal);

        if ("pdf".equals(ext)) {
            return original;
        }

        if (!"doc".equals(ext) && !"docx".equals(ext)) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Biblioteca Jurídica solo permite vista previa de PDF, DOC y DOCX");
        }

        try {
            Path carpetaCache = original.getParent().resolve(".lexia-preview").normalize();
            Files.createDirectories(carpetaCache);

            String idOriginal = sha256Texto(original.toAbsolutePath().normalize().toString())
                    .substring(0, 20);
            long tamano = Files.size(original);
            long modificado = Files.getLastModifiedTime(original).toMillis();
            String version = idOriginal + "-" + tamano + "-" + modificado;
            Path destino = carpetaCache.resolve(version + ".pdf").normalize();

            if (!destino.startsWith(carpetaCache)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Ruta de vista previa inválida");
            }

            if (pdfValido(destino)) {
                return destino;
            }

            ReentrantLock lock = bloqueos.computeIfAbsent(version, k -> new ReentrantLock());
            lock.lock();
            try {
                if (pdfValido(destino)) {
                    return destino;
                }

                byte[] pdf = exportacionBibliotecaService.convertirWordExistenteAPdf(
                        original,
                        nombreOriginal,
                        ext);

                escribirAtomico(destino, pdf);

                if (!pdfValido(destino)) {
                    Files.deleteIfExists(destino);
                    throw new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "No se pudo generar una vista previa PDF válida");
                }

                return destino;
            } finally {
                lock.unlock();
                if (!lock.hasQueuedThreads()) {
                    bloqueos.remove(version, lock);
                }
            }

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se pudo convertir el documento Word a PDF para vista previa",
                    e);
        }
    }

    private static void escribirAtomico(Path destino, byte[] contenido) throws IOException {
        if (contenido == null || contenido.length == 0) {
            throw new IOException("El conversor generó un PDF vacío");
        }

        Path temporal = Files.createTempFile(destino.getParent(), "preview-", ".tmp");
        try {
            Files.write(temporal, contenido);
            try {
                Files.move(
                        temporal,
                        destino,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    private static boolean pdfValido(Path archivo) {
        if (archivo == null || !Files.isRegularFile(archivo)) {
            return false;
        }

        try {
            if (Files.size(archivo) < 5) {
                return false;
            }

            byte[] cabecera = new byte[5];
            try (var in = Files.newInputStream(archivo)) {
                if (in.read(cabecera) != 5) {
                    return false;
                }
            }

            return cabecera[0] == '%'
                    && cabecera[1] == 'P'
                    && cabecera[2] == 'D'
                    && cabecera[3] == 'F'
                    && cabecera[4] == '-';
        } catch (IOException e) {
            return false;
        }
    }

    private static String normalizarExtension(String extension, String nombreOriginal) {
        String ext = extension;

        if ((ext == null || ext.isBlank()) && nombreOriginal != null) {
            int punto = nombreOriginal.lastIndexOf('.');
            if (punto >= 0 && punto + 1 < nombreOriginal.length()) {
                ext = nombreOriginal.substring(punto + 1);
            }
        }

        if (ext == null || ext.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se pudo determinar el formato del archivo");
        }

        return ext.toLowerCase(Locale.ROOT).replace(".", "").trim();
    }

    private static String sha256Texto(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(texto.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
