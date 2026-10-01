package com.sbss.lexia.biblioteca.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.sbss.lexia.documental.service.AlmacenamientoDocumentoService;
import com.sbss.lexia.documental.service.AlmacenamientoDocumentoService.ArchivoGuardado;

/** Almacenamiento independiente; las rutas antiguas se copian al primer acceso. */
@Service
public class AlmacenamientoBibliotecaService {
    private static final String PREFIJO = "biblioteca-v1/";
    private final AlmacenamientoDocumentoService biblioteca;
    private final AlmacenamientoDocumentoService documentos;
    private final Path raiz;

    public AlmacenamientoBibliotecaService(
            @Value("${lexia.biblioteca.storage-dir:storage/biblioteca}") String directorio,
            @Value("${lexia.documentos.storage-dir:storage/documentos}") String directorioDocumentos,
            AlmacenamientoDocumentoService documentos) {
        this.raiz = Path.of(directorio).toAbsolutePath().normalize();
        Path raizDocumentos = Path.of(directorioDocumentos).toAbsolutePath().normalize();
        if (raiz.startsWith(raizDocumentos) || raizDocumentos.startsWith(raiz)) {
            throw new IllegalStateException("Biblioteca y documentos deben usar carpetas independientes");
        }
        this.biblioteca = new AlmacenamientoDocumentoService(directorio);
        this.documentos = documentos;
    }

    public ArchivoGuardado guardar(MultipartFile archivo, String codigo, Long recursoId, int version) {
        ArchivoGuardado a = biblioteca.guardar(archivo, codigo, recursoId, version);
        // Marca virtual en BD: no forma parte de la carpeta física.
        return new ArchivoGuardado(a.nombreOriginal(), PREFIJO + a.rutaRelativa(),
                a.extension(), a.mimeType(), a.tamanoBytes(), a.sha256());
    }

    public synchronized Path resolver(String ruta) {
        if (ruta == null || ruta.isBlank()) return biblioteca.resolver(ruta);
        if (ruta.startsWith(PREFIJO)) return biblioteca.resolver(ruta.substring(PREFIJO.length()));
        Path copia = rutaAntigua(ruta);
        if (!Files.isRegularFile(copia)) {
            Path origen = documentos.resolver(ruta);
            try {
                Files.createDirectories(copia.getParent());
                Path temporal = Files.createTempFile(copia.getParent(), "biblioteca-", ".tmp");
                try {
                    Files.copy(origen, temporal, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    Files.move(temporal, copia);
                } finally {
                    Files.deleteIfExists(temporal);
                }
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo copiar el archivo antiguo a biblioteca", e);
            }
        }
        return biblioteca.resolver(raiz.relativize(copia).toString());
    }

    public void eliminar(String ruta) {
        if (ruta == null || ruta.isBlank()) return;
        if (ruta.startsWith(PREFIJO)) {
            biblioteca.eliminar(ruta.substring(PREFIJO.length()));
        } else {
            Path copia = rutaAntigua(ruta);
            if (Files.exists(copia)) biblioteca.eliminar(raiz.relativize(copia).toString());
            // Los originales antiguos se conservan: podrían estar referenciados por Documentos.
        }
    }

    private Path rutaAntigua(String ruta) {
        Path relativa = Path.of(ruta);
        Path destino = raiz.resolve("legacy").resolve(relativa).normalize();
        if (relativa.isAbsolute() || !destino.startsWith(raiz.resolve("legacy"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ruta de archivo invalida");
        }
        return destino;
    }
}
