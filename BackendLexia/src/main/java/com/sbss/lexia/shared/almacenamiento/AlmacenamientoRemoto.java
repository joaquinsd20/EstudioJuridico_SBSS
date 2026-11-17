package com.sbss.lexia.shared.almacenamiento;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Copia permanente de los archivos subidos (Documentos y Biblioteca) en un
 * servicio compatible con S3. En produccion es Supabase Storage.
 *
 * POR QUE EXISTE. El disco del servidor de produccion (Render, plan gratis) se
 * borra en cada reinicio o despliegue. Los servicios de almacenamiento siguen
 * trabajando sobre su carpeta local como siempre; esta clase solo:
 *   - sube cada archivo nuevo al bucket justo despues de escribirlo en disco,
 *   - lo baja del bucket a la carpeta local cuando alguien lo pide y ya no
 *     esta en disco (por ejemplo, despues de un reinicio),
 *   - lo borra del bucket cuando se borra del disco.
 * Asi el resto del codigo sigue recibiendo un {@link Path} y no cambia.
 *
 * Si falta alguna variable LEXIA_S3_*, queda desactivada y todo funciona solo
 * con el disco local, como en desarrollo.
 */
@Component
public class AlmacenamientoRemoto {

    private static final Logger log = LoggerFactory.getLogger(AlmacenamientoRemoto.class);

    private final String bucket;
    private final S3Client cliente;

    public AlmacenamientoRemoto(
            @Value("${lexia.almacenamiento.s3.endpoint:}") String endpoint,
            @Value("${lexia.almacenamiento.s3.region:us-east-1}") String region,
            @Value("${lexia.almacenamiento.s3.bucket:}") String bucket,
            @Value("${lexia.almacenamiento.s3.access-key:}") String accessKey,
            @Value("${lexia.almacenamiento.s3.secret-key:}") String secretKey) {

        boolean configurado = !endpoint.isBlank() && !bucket.isBlank()
                && !accessKey.isBlank() && !secretKey.isBlank();
        this.bucket = bucket;
        this.cliente = configurado
                ? S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    // Supabase y la mayoria de servicios S3 que no son AWS
                    // exigen el bucket en la ruta, no como subdominio.
                    .forcePathStyle(true)
                    .httpClient(UrlConnectionHttpClient.create())
                    .build()
                : null;

        if (configurado) {
            log.info("Almacenamiento remoto activo: bucket '{}'", bucket);
        }
    }

    public boolean activo() {
        return cliente != null;
    }

    /** Sube el archivo local bajo la clave dada. No hace nada si esta desactivado. */
    public void subir(String clave, Path archivo, String mimeType) {
        if (!activo()) return;
        try {
            cliente.putObject(b -> b.bucket(bucket).key(clave)
                            .contentType(mimeType == null || mimeType.isBlank()
                                    ? "application/octet-stream" : mimeType),
                    RequestBody.fromFile(archivo));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo guardar el archivo en el almacenamiento permanente", e);
        }
    }

    /**
     * Si el archivo no esta en disco, lo baja del bucket a {@code destino}.
     *
     * @return true si al terminar el archivo esta en disco.
     */
    public boolean traerSiFalta(String clave, Path destino) {
        if (Files.isRegularFile(destino)) return true;
        if (!activo()) return false;

        Path temporal = null;
        try {
            Files.createDirectories(destino.getParent());
            temporal = Files.createTempFile(destino.getParent(), "lexia-descarga-", ".tmp");
            Files.delete(temporal);
            cliente.getObject(b -> b.bucket(bucket).key(clave), temporal);
            // Atomico: nadie ve un archivo a medio bajar.
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            temporal = null;
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return false;
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo leer el archivo del almacenamiento permanente", e);
        } catch (IOException | RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo leer el archivo del almacenamiento permanente", e);
        } finally {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException ignored) {
                    // Un temporal suelto no afecta al funcionamiento.
                }
            }
        }
    }

    /** Borra el archivo del bucket. Un fallo se registra pero no interrumpe. */
    public void eliminar(String clave) {
        if (!activo()) return;
        try {
            cliente.deleteObject(b -> b.bucket(bucket).key(clave));
        } catch (RuntimeException e) {
            log.warn("No se pudo borrar '{}' del almacenamiento permanente: {}", clave, e.getMessage());
        }
    }
}
