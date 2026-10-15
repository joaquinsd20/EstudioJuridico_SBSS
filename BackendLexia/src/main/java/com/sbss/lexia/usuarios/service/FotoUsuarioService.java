package com.sbss.lexia.usuarios.service;

import com.sbss.lexia.shared.security.SesionActual;
import com.sbss.lexia.usuarios.repository.UsuarioGestionRepositoryJdbc;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FotoUsuarioService {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELES = 16_000_000L;
    private static final int MAX_LADO = 8_000;

    private final UsuarioGestionRepositoryJdbc repository;
    private final NamedParameterJdbcTemplate jdbc;

    @Value("${lexia.usuarios.fotos-dir:./storage/usuarios}")
    private String directorio;

    public String guardar(String uuid, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una imagen.");
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto no puede superar 5 MB.");
        }

        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer la imagen.");
        }

        String extension = extensionImagen(bytes);
        if (extension == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato no permitido. Usa PNG, JPG, WEBP o GIF.");
        }
        validarDimensiones(bytes, extension);

        // Nunca se acepta el ID numérico enviado por el cliente: se deriva la
        // identidad y el estudio desde la sesión autenticada.
        Integer estudioId = SesionActual.estudioId();
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("uuid", uuid)
                .addValue("estudio", estudioId);
        Long id = jdbc.query("SELECT id FROM usuarios WHERE uuid_publico = :uuid "
                        + "AND estudio_id = :estudio AND tipo_usuario = 'INTERNO'",
                parametros, rs -> rs.next() ? rs.getLong(1) : null);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado.");
        }
        String fotoAnterior = jdbc.query("SELECT foto_url FROM usuarios WHERE uuid_publico = :uuid "
                        + "AND estudio_id = :estudio AND tipo_usuario = 'INTERNO'",
                parametros, rs -> rs.next() ? rs.getString(1) : null);

        String nombre = UUID.randomUUID() + "." + extension;
        Path directorioSeguro = Paths.get(directorio).toAbsolutePath().normalize();
        Path destino = directorioSeguro.resolve(nombre).normalize();
        if (!destino.getParent().equals(directorioSeguro)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ruta de imagen inválida.");
        }

        try {
            Files.createDirectories(directorioSeguro);
            Files.write(destino, bytes, java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar la foto.");
        }

        String url = "/uploads/usuarios/" + nombre;
        try {
            repository.actualizarFoto(uuid, estudioId, url, SesionActual.usuarioId());
        } catch (RuntimeException e) {
            // Si la escritura SQL falla, no se deja un archivo huérfano.
            try { Files.deleteIfExists(destino); } catch (IOException ignored) { /* limpieza best effort */ }
            throw e;
        }

        borrarFotoAnterior(fotoAnterior, destino);
        return url;
    }

    /**
     * Lee una foto solo si sigue asociada a un usuario del estudio de la sesión.
     * La URL antigua no es un acceso público: el frontend solicita el binario con JWT.
     */
    public ContenidoFoto leer(String nombre) {
        // Las fotografías corresponden al personal interno; no se exponen al portal cliente.
        if (SesionActual.esPortalCliente()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a las fotos del personal.");
        }
        if (nombre == null || !nombre.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(?:png|jpg|gif|webp)")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto no encontrada.");
        }

        String url = "/uploads/usuarios/" + nombre;
        Integer registrada = jdbc.query("SELECT 1 FROM usuarios WHERE estudio_id = :estudio "
                        + "AND tipo_usuario = 'INTERNO' AND foto_url = :url LIMIT 1",
                new MapSqlParameterSource().addValue("estudio", SesionActual.estudioId()).addValue("url", url),
                rs -> rs.next() ? rs.getInt(1) : null);
        if (registrada == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto no encontrada.");
        }

        Path archivo = localizarArchivo(nombre);
        if (archivo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El archivo de la foto no está disponible.");
        }
        try {
            long tamanio = Files.size(archivo);
            if (tamanio < 1 || tamanio > MAX_BYTES) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto no encontrada.");
            }
            String extension = nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
            MediaType tipo = switch (extension) {
                case "png" -> MediaType.IMAGE_PNG;
                case "gif" -> MediaType.IMAGE_GIF;
                case "webp" -> MediaType.parseMediaType("image/webp");
                default -> MediaType.IMAGE_JPEG;
            };
            return new ContenidoFoto(Files.readAllBytes(archivo), tipo);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer la foto.");
        }
    }

    private Path localizarArchivo(String nombre) {
        Path principal = Paths.get(directorio).toAbsolutePath().normalize();
        Path raiz = Paths.get("").toAbsolutePath().normalize();
        Set<Path> directorios = new LinkedHashSet<>();
        directorios.add(principal);
        directorios.add(raiz.resolve("storage/usuarios").normalize());
        directorios.add(raiz.resolve("uploads/usuarios").normalize());
        directorios.add(raiz.resolve("BackendLexia/storage/usuarios").normalize());
        directorios.add(raiz.resolve("BackendLexia/uploads/usuarios").normalize());
        if (principal.getParent() != null && principal.getParent().getParent() != null) {
            Path raizBackend = principal.getParent().getParent();
            directorios.add(raizBackend.resolve("storage/usuarios").normalize());
            directorios.add(raizBackend.resolve("uploads/usuarios").normalize());
        }
        for (Path directorioFoto : directorios) {
            Path candidato = directorioFoto.resolve(nombre).normalize();
            if (candidato.getParent().equals(directorioFoto) && Files.isRegularFile(candidato)) return candidato;
        }
        return null;
    }

    public record ContenidoFoto(byte[] contenido, MediaType tipoContenido) { }

    private void validarDimensiones(byte[] bytes, String extension) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (entrada != null) {
                Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
                if (lectores.hasNext()) {
                    ImageReader lector = lectores.next();
                    try {
                        lector.setInput(entrada, true, true);
                        validarTamanio(lector.getWidth(0), lector.getHeight(0));
                        return;
                    } finally {
                        lector.dispose();
                    }
                }
            }
        } catch (IOException e) {
            if (!"webp".equals(extension)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen está dañada o no es válida.");
            }
        }

        // Java estándar no incluye un lector WebP. Leemos las dimensiones de sus
        // cabeceras principales sin decodificar el contenido en memoria adicional.
        if ("webp".equals(extension)) {
            int[] dimensiones = dimensionesWebp(bytes);
            if (dimensiones != null) {
                validarTamanio(dimensiones[0], dimensiones[1]);
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen está dañada o no es válida.");
    }

    private void validarTamanio(int ancho, int alto) {
        if (ancho < 1 || alto < 1 || ancho > MAX_LADO || alto > MAX_LADO
                || (long) ancho * alto > MAX_PIXELES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La imagen tiene dimensiones demasiado grandes. Usa una imagen de hasta 16 megapíxeles.");
        }
    }

    private int[] dimensionesWebp(byte[] bytes) {
        if (bytes.length < 25) return null;
        String tipo = new String(bytes, 12, 4, StandardCharsets.US_ASCII);
        if ("VP8X".equals(tipo) && bytes.length >= 30) {
            int ancho = 1 + littleEndian24(bytes, 24);
            int alto = 1 + littleEndian24(bytes, 27);
            return new int[] { ancho, alto };
        }
        if ("VP8 ".equals(tipo) && bytes.length >= 30
                && (bytes[23] & 0xff) == 0x9d && (bytes[24] & 0xff) == 0x01
                && (bytes[25] & 0xff) == 0x2a) {
            int ancho = littleEndian16(bytes, 26) & 0x3fff;
            int alto = littleEndian16(bytes, 28) & 0x3fff;
            return new int[] { ancho, alto };
        }
        if ("VP8L".equals(tipo) && bytes.length >= 25 && (bytes[20] & 0xff) == 0x2f) {
            int b1 = bytes[21] & 0xff;
            int b2 = bytes[22] & 0xff;
            int b3 = bytes[23] & 0xff;
            int b4 = bytes[24] & 0xff;
            int ancho = 1 + b1 + ((b2 & 0x3f) << 8);
            int alto = 1 + (b2 >>> 6) + (b3 << 2) + ((b4 & 0x0f) << 10);
            return new int[] { ancho, alto };
        }
        return null;
    }

    private int littleEndian16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private int littleEndian24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16);
    }

    private void borrarFotoAnterior(String urlAnterior, Path destinoActual) {
        if (urlAnterior == null || !urlAnterior.startsWith("/uploads/usuarios/")) return;
        String nombreAnterior = urlAnterior.substring("/uploads/usuarios/".length());
        // Solo se eliminan nombres UUID creados por este servicio; nunca rutas arbitrarias.
        if (!nombreAnterior.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(?:png|jpg|gif|webp)")) return;
        // El mismo archivo podría estar referenciado por otra cuenta antigua; no
        // lo eliminamos hasta confirmar que la ruta ya no está asignada en la BD.
        Long referencias = jdbc.query("SELECT COUNT(*) FROM usuarios WHERE foto_url = :url",
                new MapSqlParameterSource().addValue("url", urlAnterior),
                rs -> rs.next() ? rs.getLong(1) : 0L);
        if (referencias != 0) return;
        Path anterior = localizarArchivo(nombreAnterior);
        if (anterior == null || anterior.normalize().equals(destinoActual.normalize())) return;
        try { Files.deleteIfExists(anterior); } catch (IOException ignored) { /* limpieza best effort */ }
    }

    private String extensionImagen(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 255) == 137 && bytes[1] == 80 && bytes[2] == 78
                && bytes[3] == 71 && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10) {
            return "png";
        }
        if (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216
                && (bytes[2] & 255) == 255) {
            return "jpg";
        }
        if (bytes.length >= 6 && new String(bytes, 0, 6, StandardCharsets.US_ASCII).matches("GIF8[79]a")) {
            return "gif";
        }
        if (bytes.length >= 12 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
            return "webp";
        }
        return null;
    }
}
