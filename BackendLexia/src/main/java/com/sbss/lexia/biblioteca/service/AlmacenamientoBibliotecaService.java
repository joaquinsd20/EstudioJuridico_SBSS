package com.sbss.lexia.biblioteca.service;

import com.sbss.lexia.shared.almacenamiento.AlmacenamientoRemoto;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.sbss.lexia.documental.service.AlmacenamientoDocumentoService;
import com.sbss.lexia.documental.service.AlmacenamientoDocumentoService.ArchivoGuardado;

/**
 * Almacenamiento exclusivo de Biblioteca Jurídica.
 *
 * Seguridad:
 * - no confía en el atributo accept del navegador;
 * - lista blanca de extensiones;
 * - valida la firma real del archivo;
 * - limita tamaño;
 * - usa nombres físicos aleatorios;
 * - impide path traversal;
 * - conserva compatibilidad con rutas antiguas de Documentos.
 */
@Service
public class AlmacenamientoBibliotecaService {

    private static final String PREFIJO = "biblioteca-v1/";

    /**
     * Formatos documentales autorizados en Biblioteca Jurídica.
     * No se permiten PHP, HTML, JS, EXE, BAT, CMD, ZIP, RAR, JAR,
     * APK, scripts ni formatos Office con macros.
     * Biblioteca acepta únicamente PDF, DOC y DOCX.
     */
    private static final Set<String> EXTENSIONES_PERMITIDAS = Set.of(
            "pdf",
            "doc",
            "docx"
    );

    private static final Map<String, String> MIME_POR_EXTENSION = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private static final byte[] FIRMA_OLE = new byte[] {
            (byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1
    };

    private static final long MAX_OOXML_DESCOMPRIMIDO = 250L * 1024L * 1024L;
    private static final long MAX_ENTRADA_OOXML = 100L * 1024L * 1024L;
    private static final long MAX_RATIO_COMPRESION = 200L;

    /** Prefijo de las claves de este modulo en el almacenamiento remoto. */
    private static final String PREFIJO_REMOTO = "biblioteca/";

    private final AlmacenamientoDocumentoService documentos;
    private final AlmacenamientoRemoto remoto;
    private final Path raiz;
    private final long maxBytes;

    public AlmacenamientoBibliotecaService(
            @Value("${lexia.biblioteca.storage-dir:storage/biblioteca}") String directorio,
            @Value("${lexia.documentos.storage-dir:storage/documentos}") String directorioDocumentos,
            @Value("${lexia.biblioteca.max-file-size-bytes:52428800}") long maxBytes,
            AlmacenamientoDocumentoService documentos,
            AlmacenamientoRemoto remoto) {
        this.remoto = remoto;

        try {
            this.raiz = Path.of(directorio).toAbsolutePath().normalize();
            Files.createDirectories(this.raiz);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el directorio de Biblioteca Jurídica", e);
        }

        Path raizDocumentos = Path.of(directorioDocumentos).toAbsolutePath().normalize();
        if (raiz.startsWith(raizDocumentos) || raizDocumentos.startsWith(raiz)) {
            throw new IllegalStateException(
                    "Biblioteca y Documentos deben usar carpetas independientes");
        }

        if (maxBytes <= 0) {
            throw new IllegalStateException("lexia.biblioteca.max-file-size-bytes debe ser mayor que cero");
        }

        this.maxBytes = maxBytes;
        this.documentos = documentos;
    }

    public ArchivoGuardado guardar(
            MultipartFile archivo,
            String codigo,
            Long recursoId,
            int version) {

        if (archivo == null || archivo.isEmpty()) {
            throw badRequest("Debes seleccionar un archivo");
        }

        if (archivo.getSize() <= 0) {
            throw badRequest("El archivo está vacío");
        }

        if (archivo.getSize() > maxBytes) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "El archivo supera el tamaño máximo permitido de " + (maxBytes / 1024 / 1024) + " MB");
        }

        String nombreOriginal = nombreSeguro(archivo.getOriginalFilename());
        String extension = extension(nombreOriginal);
        validarExtension(extension);

        Path carpeta = raiz
                .resolve(carpetaCodigo(codigo))
                .resolve("recurso-" + recursoId)
                .normalize();
        validarDentroDeRaiz(carpeta);

        Path temporal = null;

        try {
            Files.createDirectories(carpeta);

            // Primero se copia a un temporal dentro de la propia raíz.
            // Solo se convierte en archivo definitivo si supera todas las validaciones.
            temporal = Files.createTempFile(carpeta, "upload-", ".tmp");

            try (InputStream in = archivo.getInputStream()) {
                Files.copy(in, temporal, StandardCopyOption.REPLACE_EXISTING);
            }

            long tamanoReal = Files.size(temporal);
            if (tamanoReal <= 0) {
                throw badRequest("El archivo está vacío");
            }
            if (tamanoReal > maxBytes) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "El archivo supera el tamaño máximo permitido");
            }

            // Defensa principal contra archivos renombrados, por ejemplo:
            // malware.php -> sentencia.pdf
            validarContenidoReal(temporal, extension);

            String sha256 = calcularSha256(temporal);

            String nombreFisico = "v%03d_%s.%s".formatted(
                    Math.max(version, 1),
                    UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                    extension);

            Path destino = carpeta.resolve(nombreFisico).normalize();
            validarDentroDeRaiz(destino);

            moverSeguro(temporal, destino);
            temporal = null;

            String relativa = raiz.relativize(destino)
                    .toString()
                    .replace('\\', '/');

            try {
                remoto.subir(PREFIJO_REMOTO + relativa, destino, MIME_POR_EXTENSION.get(extension));
            } catch (RuntimeException e) {
                // Sin copia permanente se perderia en el proximo reinicio.
                Files.deleteIfExists(destino);
                throw e;
            }

            return new ArchivoGuardado(
                    nombreOriginal,
                    PREFIJO + relativa,
                    extension,
                    MIME_POR_EXTENSION.get(extension),
                    tamanoReal,
                    sha256);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo guardar el archivo de Biblioteca Jurídica",
                    e);
        } finally {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException ignored) {
                    // La limpieza puede realizarse posteriormente.
                }
            }
        }
    }

    /**
     * Valida un archivo ya almacenado antes de entregarlo al navegador.
     * También protege recursos antiguos creados antes de endurecer la subida.
     */
    public void validarArchivoExistente(
            Path archivo,
            String nombreOriginal,
            String extensionRegistrada) {

        if (archivo == null || !Files.isRegularFile(archivo)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "El archivo físico no existe en el servidor");
        }

        String extension = normalizarExtension(extensionRegistrada);
        if (extension == null) {
            extension = extension(nombreSeguro(nombreOriginal));
        }

        validarExtension(extension);

        try {
            long tamano = Files.size(archivo);
            if (tamano <= 0) {
                throw badRequest("El archivo almacenado está vacío");
            }
            if (tamano > maxBytes) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "El archivo almacenado supera el tamaño máximo permitido");
            }

            validarContenidoReal(archivo, extension);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo validar el archivo almacenado",
                    e);
        }
    }

    public Path resolver(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "El recurso no tiene archivo asociado");
        }

        if (ruta.startsWith(PREFIJO)) {
            String relativa = ruta.substring(PREFIJO.length());
            Path archivo = raiz.resolve(relativa).normalize();
            validarDentroDeRaiz(archivo);

            if (!remoto.traerSiFalta(PREFIJO_REMOTO + relativa, archivo)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "El archivo físico no existe en el servidor");
            }

            return archivo;
        }

        // Compatibilidad con recursos antiguos que apuntaban a Documentos.
        Path copia = rutaAntigua(ruta);
        if (!Files.isRegularFile(copia)) {
            Path origen = documentos.resolver(ruta);

            try {
                Files.createDirectories(copia.getParent());
                Path temporal = Files.createTempFile(copia.getParent(), "biblioteca-legacy-", ".tmp");

                try {
                    Files.copy(origen, temporal, StandardCopyOption.REPLACE_EXISTING);
                    moverSeguro(temporal, copia);
                } finally {
                    Files.deleteIfExists(temporal);
                }
            } catch (IOException e) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo copiar el archivo antiguo a Biblioteca Jurídica",
                        e);
            }
        }

        return copia;
    }

    public void eliminar(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            return;
        }

        if (ruta.startsWith(PREFIJO)) {
            Path archivo = raiz.resolve(ruta.substring(PREFIJO.length())).normalize();
            validarDentroDeRaiz(archivo);

            try {
                Files.deleteIfExists(archivo);
                limpiarCarpetasVacias(archivo.getParent());
            } catch (IOException e) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo eliminar el archivo físico",
                        e);
            }
            remoto.eliminar(PREFIJO_REMOTO + ruta.substring(PREFIJO.length()));

            return;
        }

        Path copia = rutaAntigua(ruta);
        try {
            Files.deleteIfExists(copia);
            limpiarCarpetasVacias(copia.getParent());
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo eliminar la copia antigua de Biblioteca Jurídica",
                    e);
        }

        // El original de Documentos no se elimina porque puede estar referenciado allí.
    }

    private void validarContenidoReal(Path archivo, String extension) throws IOException {
        switch (extension) {
            case "pdf" -> validarPdf(archivo);
            case "doc" -> validarOle(archivo);
            case "docx" -> validarOoxml(archivo, "word/document.xml", "DOCX");
            default -> throw badRequest("Formato de archivo no permitido");
        }
    }

    private void validarPdf(Path archivo) throws IOException {
        byte[] cabecera = leerCabecera(archivo, 5);
        byte[] pdf = new byte[] {'%', 'P', 'D', 'F', '-'};

        if (!empiezaCon(cabecera, pdf)) {
            throw badRequest("El contenido del archivo no corresponde a un PDF válido");
        }

        // Un PDF normal termina con %%EOF. Comprobar la cola evita aceptar
        // archivos arbitrarios que solo tengan una cabecera PDF falsificada.
        byte[] cola = leerCola(archivo, 8192);
        if (!contieneSecuencia(cola, new byte[] {'%', '%', 'E', 'O', 'F'})) {
            throw badRequest("El archivo no contiene una terminación PDF válida");
        }
    }

    private void validarOle(Path archivo) throws IOException {
        byte[] cabecera = leerCabecera(archivo, FIRMA_OLE.length);

        if (!empiezaCon(cabecera, FIRMA_OLE)) {
            throw badRequest(
                    "El contenido no corresponde a un documento Word DOC válido");
        }
    }

    private void validarOoxml(Path archivo, String entradaObligatoria, String tipo) throws IOException {
        boolean contentTypes = false;
        boolean entradaTipo = false;
        int entradas = 0;
        long totalDescomprimido = 0L;

        try (ZipFile zip = new ZipFile(archivo.toFile())) {
            var enumeracion = zip.entries();

            while (enumeracion.hasMoreElements()) {
                ZipEntry entry = enumeracion.nextElement();
                entradas++;

                if (entradas > 10000) {
                    throw badRequest("El documento Office contiene demasiadas entradas");
                }

                String nombre = entry.getName();
                String nombreLower = nombre.toLowerCase(Locale.ROOT);

                // Biblioteca admite DOCX sin macros. Un DOCM renombrado
                // a .docx queda bloqueado aquí.
                if (nombreLower.endsWith("vbaproject.bin")
                        || nombreLower.contains("/activex/")
                        || nombreLower.startsWith("activex/")) {
                    throw badRequest("No se permiten documentos Office con macros o contenido activo");
                }

                long size = entry.getSize();
                long compressed = entry.getCompressedSize();

                if (size > MAX_ENTRADA_OOXML) {
                    throw badRequest("El documento Office contiene una entrada demasiado grande");
                }

                if (size > 0) {
                    totalDescomprimido += size;
                    if (totalDescomprimido > MAX_OOXML_DESCOMPRIMIDO) {
                        throw badRequest("El documento Office excede el tamaño descomprimido permitido");
                    }

                    if (compressed > 0 && size / Math.max(compressed, 1L) > MAX_RATIO_COMPRESION) {
                        throw badRequest("El documento Office presenta una relación de compresión insegura");
                    }
                }

                if ("[Content_Types].xml".equals(nombre)) {
                    contentTypes = true;
                }
                if (entradaObligatoria.equals(nombre)) {
                    entradaTipo = true;
                }
            }
        } catch (java.util.zip.ZipException e) {
            throw badRequest("El contenido no corresponde a un archivo " + tipo + " válido");
        }

        if (!contentTypes || !entradaTipo) {
            throw badRequest("El contenido no corresponde a un archivo " + tipo + " válido");
        }
    }

    private static byte[] leerCabecera(Path archivo, int longitud) throws IOException {
        byte[] resultado = new byte[longitud];
        int leidos;

        try (InputStream in = Files.newInputStream(archivo)) {
            leidos = in.read(resultado);
        }

        if (leidos < longitud) {
            return leidos <= 0 ? new byte[0] : java.util.Arrays.copyOf(resultado, leidos);
        }

        return resultado;
    }

    private static byte[] leerCola(Path archivo, int maximo) throws IOException {
        long tamano = Files.size(archivo);
        int longitud = (int) Math.min(tamano, maximo);
        byte[] resultado = new byte[longitud];

        try (InputStream in = Files.newInputStream(archivo)) {
            long omitir = Math.max(0L, tamano - longitud);
            in.skipNBytes(omitir);

            int offset = 0;
            while (offset < longitud) {
                int leidos = in.read(resultado, offset, longitud - offset);
                if (leidos < 0) break;
                offset += leidos;
            }

            if (offset < longitud) {
                return java.util.Arrays.copyOf(resultado, offset);
            }
        }

        return resultado;
    }

    private static boolean contieneSecuencia(byte[] valor, byte[] secuencia) {
        if (secuencia.length == 0 || valor.length < secuencia.length) {
            return false;
        }

        for (int i = 0; i <= valor.length - secuencia.length; i++) {
            boolean coincide = true;
            for (int j = 0; j < secuencia.length; j++) {
                if (valor[i + j] != secuencia[j]) {
                    coincide = false;
                    break;
                }
            }
            if (coincide) return true;
        }

        return false;
    }

    private static boolean empiezaCon(byte[] valor, byte[] prefijo) {
        if (valor.length < prefijo.length) {
            return false;
        }

        for (int i = 0; i < prefijo.length; i++) {
            if (valor[i] != prefijo[i]) {
                return false;
            }
        }
        return true;
    }

    private static void validarExtension(String extension) {
        if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
            throw badRequest(
                    "Formato no permitido. Solo se aceptan PDF, DOC y DOCX");
        }
    }

    private static String normalizarExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return null;
        }

        String limpia = extension
                .toLowerCase(Locale.ROOT)
                .replace(".", "")
                .trim();

        if (!limpia.matches("[a-z0-9]{1,10}")) {
            throw badRequest("Extensión de archivo inválida");
        }

        return limpia;
    }

    private static String extension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        if (punto < 0 || punto == nombre.length() - 1) {
            throw badRequest("El archivo debe tener una extensión permitida");
        }

        String extension = nombre.substring(punto + 1)
                .toLowerCase(Locale.ROOT)
                .trim();

        // Evita nombres ambiguos o extensiones con caracteres extraños.
        if (!extension.matches("[a-z0-9]{1,10}")) {
            throw badRequest("Extensión de archivo inválida");
        }

        return extension;
    }

    private static String nombreSeguro(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw badRequest("El archivo debe tener nombre");
        }

        String limpio = Path.of(nombre)
                .getFileName()
                .toString()
                .replaceAll("[\\r\\n\\t]", "_")
                .replaceAll("[<>:\"/\\\\|?*]", "_")
                .trim();

        if (limpio.isBlank()) {
            throw badRequest("Nombre de archivo inválido");
        }

        return limpio.length() > 220
                ? limpio.substring(limpio.length() - 220)
                : limpio;
    }

    private static String carpetaCodigo(String codigo) {
        String valor = codigo == null ? "" : codigo.trim();
        valor = valor.replaceAll("[^A-Za-z0-9._-]", "_");

        if (valor.isBlank()) {
            valor = "BIBLIOTECA";
        }

        return valor.length() > 80
                ? valor.substring(0, 80)
                : valor;
    }

    private void validarDentroDeRaiz(Path ruta) {
        if (!ruta.toAbsolutePath().normalize().startsWith(raiz)) {
            throw badRequest("Ruta de archivo inválida");
        }
    }

    private Path rutaAntigua(String ruta) {
        Path relativa = Path.of(ruta);
        Path legacy = raiz.resolve("legacy").normalize();
        Path destino = legacy.resolve(relativa).normalize();

        if (relativa.isAbsolute() || !destino.startsWith(legacy)) {
            throw badRequest("Ruta de archivo inválida");
        }

        return destino;
    }

    private static String calcularSha256(Path archivo) throws IOException {
        MessageDigest digest = sha256();

        try (InputStream in = Files.newInputStream(archivo);
             DigestInputStream digestInput = new DigestInputStream(in, digest)) {
            digestInput.transferTo(java.io.OutputStream.nullOutputStream());
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static void moverSeguro(Path origen, Path destino) throws IOException {
        try {
            Files.move(
                    origen,
                    destino,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(
                    origen,
                    destino,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void limpiarCarpetasVacias(Path carpeta) throws IOException {
        Path actual = carpeta;

        while (actual != null
                && !actual.equals(raiz)
                && actual.startsWith(raiz)) {

            try (var contenido = Files.list(actual)) {
                if (contenido.findAny().isPresent()) {
                    break;
                }
            }

            Files.deleteIfExists(actual);
            actual = actual.getParent();
        }
    }

    private static ResponseStatusException badRequest(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
