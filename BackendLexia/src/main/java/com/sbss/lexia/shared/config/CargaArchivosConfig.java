package com.sbss.lexia.shared.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configura el límite de carga para documentos y archivos multimedia.
 *
 * El límite puede cambiarse sin recompilar definiendo, por ejemplo:
 * lexia.documentos.max-upload-mb=300
 */
@Configuration
public class CargaArchivosConfig {

    private static final long BYTES_POR_MB = 1024L * 1024L;
    private static final long MARGEN_REQUEST_MB = 10L;
    private static final int UMBRAL_DISCO_BYTES = 1024 * 1024;

    @Bean
    public MultipartConfigElement multipartConfigElement(
            @Value("${lexia.documentos.max-upload-mb:250}") long maxUploadMb) {

        if (maxUploadMb < 1) {
            throw new IllegalArgumentException("lexia.documentos.max-upload-mb debe ser mayor que 0");
        }

        long maxFileSize = Math.multiplyExact(maxUploadMb, BYTES_POR_MB);
        long maxRequestSize = Math.addExact(
                maxFileSize,
                Math.multiplyExact(MARGEN_REQUEST_MB, BYTES_POR_MB));

        return new MultipartConfigElement(
                "",
                maxFileSize,
                maxRequestSize,
                UMBRAL_DISCO_BYTES);
    }
}
