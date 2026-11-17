package com.sbss.lexia.documental.service;

import com.sbss.lexia.shared.almacenamiento.AlmacenamientoRemoto;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlmacenamientoDocumentoService {

    private static final Set<String> EXTENSIONES_PERMITIDAS = Set.of("pdf", "mp3", "wav", "mp4");

    /** Prefijo de las claves de este modulo en el almacenamiento remoto. */
    private static final String PREFIJO_REMOTO = "documentos/";

    private final Path raiz;
    private final AlmacenamientoRemoto remoto;

    public AlmacenamientoDocumentoService(
            @Value("${lexia.documentos.storage-dir:storage/documentos}") String directorio,
            AlmacenamientoRemoto remoto) {
        this.remoto = remoto;
        try {
            this.raiz = Path.of(directorio).toAbsolutePath().normalize();
            Files.createDirectories(this.raiz);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el directorio de documentos", e);
        }
    }

    public ArchivoGuardado guardar(
            MultipartFile archivo,
            String expedienteCodigo,
            Long documentoId,
            int numeroVersion) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes seleccionar un archivo");
        }
        String nombreOriginal = nombreSeguro(archivo.getOriginalFilename());
        String extension = extension(nombreOriginal);
        validarExtension(extension);

        try (InputStream in = archivo.getInputStream()) {
            return guardarFlujo(in, archivo.getSize(), nombreOriginal, extension,
                    archivo.getContentType(), expedienteCodigo, documentoId, numeroVersion);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo guardar el archivo", e);
        }
    }

    public ArchivoGuardado guardar(
            Path archivoOrigen,
            String nombreOriginal,
            String mimeType,
            String expedienteCodigo,
            Long documentoId,
            int numeroVersion) {
        nombreOriginal = nombreSeguro(nombreOriginal);
        String extension = extension(nombreOriginal);
        validarExtension(extension);
        try (InputStream in = Files.newInputStream(archivoOrigen)) {
            return guardarFlujo(in, Files.size(archivoOrigen), nombreOriginal, extension,
                    mimeType, expedienteCodigo, documentoId, numeroVersion);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo guardar la nueva version", e);
        }
    }

    private ArchivoGuardado guardarFlujo(
            InputStream in,
            long tamano,
            String nombreOriginal,
            String extension,
            String mimeType,
            String expedienteCodigo,
            Long documentoId,
            int numeroVersion) throws IOException {

        Path carpeta = raiz
                .resolve(carpetaExpediente(expedienteCodigo))
                .resolve("documento-" + documentoId)
                .normalize();
        validarDentroDeRaiz(carpeta);
        Files.createDirectories(carpeta);

        String nombreFisico = "v%03d_%s.%s".formatted(
                numeroVersion,
                UUID.randomUUID().toString().substring(0, 12),
                extension);
        Path destino = carpeta.resolve(nombreFisico).normalize();
        validarDentroDeRaiz(destino);

        MessageDigest sha256 = sha256();
        try (DigestInputStream digest = new DigestInputStream(in, sha256)) {
            Files.copy(digest, destino, StandardCopyOption.REPLACE_EXISTING);
        }

        long tamanoReal = Files.size(destino);
        if (tamanoReal <= 0) {
            Files.deleteIfExists(destino);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo esta vacio");
        }

        String mime = mimeType;
        if (mime == null || mime.isBlank() || "application/octet-stream".equals(mime)) {
            mime = Files.probeContentType(destino);
        }
        if (mime == null || mime.isBlank()) {
            mime = mimePorExtension(extension);
        }

        String relativa = raiz.relativize(destino).toString().replace('\\', '/');
        try {
            remoto.subir(PREFIJO_REMOTO + relativa, destino, mime);
        } catch (RuntimeException e) {
            // Sin copia permanente el archivo se perderia en el proximo
            // reinicio: mejor fallar ahora que aparentar que se guardo.
            Files.deleteIfExists(destino);
            throw e;
        }
        return new ArchivoGuardado(
                nombreOriginal,
                relativa,
                extension,
                mime,
                tamano > 0 ? tamanoReal : tamanoReal,
                HexFormat.of().formatHex(sha256.digest()));
    }

    public Path resolver(String rutaRelativa) {
        if (rutaRelativa == null || rutaRelativa.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El documento no tiene archivo asociado");
        }
        Path ruta = raiz.resolve(rutaRelativa).normalize();
        // Una ruta fuera de la carpeta (p.ej. las '/almacen/...' de la semilla
        // de desarrollo, que nunca tuvieron archivo) no tiene nada que leer.
        if (!ruta.startsWith(raiz)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Este documento no tiene un archivo cargado");
        }
        if (!remoto.traerSiFalta(PREFIJO_REMOTO + rutaRelativa, ruta)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "El archivo fisico no existe en el servidor");
        }
        return ruta;
    }

    public void eliminar(String rutaRelativa) {
        if (rutaRelativa == null || rutaRelativa.isBlank()) return;
        Path ruta = raiz.resolve(rutaRelativa).normalize();
        validarDentroDeRaiz(ruta);
        try {
            Files.deleteIfExists(ruta);
            limpiarCarpetasVacias(ruta.getParent());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo eliminar el archivo fisico", e);
        }
        remoto.eliminar(PREFIJO_REMOTO + rutaRelativa);
    }

    private void limpiarCarpetasVacias(Path carpeta) throws IOException {
        Path actual = carpeta;
        while (actual != null && !actual.equals(raiz) && actual.startsWith(raiz)) {
            try (var contenido = Files.list(actual)) {
                if (contenido.findAny().isPresent()) break;
            }
            Files.deleteIfExists(actual);
            actual = actual.getParent();
        }
    }

    private static String carpetaExpediente(String codigo) {
        String valor = codigo == null ? "" : codigo.trim();
        valor = valor.replaceAll("[^A-Za-z0-9._-]", "_");
        if (valor.isBlank()) valor = "sin-codigo";
        return valor.length() > 80 ? valor.substring(0, 80) : valor;
    }

    private void validarDentroDeRaiz(Path ruta) {
        if (!ruta.toAbsolutePath().normalize().startsWith(raiz)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ruta de archivo invalida");
        }
    }

    private static String nombreSeguro(String nombre) {
        if (nombre == null || nombre.isBlank()) return "documento";
        String limpio = Path.of(nombre).getFileName().toString()
                .replaceAll("[\\r\\n\\t]", "_")
                .replaceAll("[<>:\"/\\\\|?*]", "_");
        return limpio.length() > 250 ? limpio.substring(limpio.length() - 250) : limpio;
    }

    private static String extension(String nombre) {
        int p = nombre.lastIndexOf('.');
        if (p < 0 || p == nombre.length() - 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El archivo debe tener extension");
        }
        return nombre.substring(p + 1).toLowerCase(Locale.ROOT);
    }

    private static void validarExtension(String extension) {
        if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato interno no permitido. Solo se almacenan PDF, MP3, WAV o MP4");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static String mimePorExtension(String ext) {
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            case "txt" -> "text/plain";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "mp4" -> "video/mp4";
            default -> "application/octet-stream";
        };
    }

    public record ArchivoGuardado(
            String nombreOriginal,
            String rutaRelativa,
            String extension,
            String mimeType,
            long tamanoBytes,
            String sha256) {
    }
}
