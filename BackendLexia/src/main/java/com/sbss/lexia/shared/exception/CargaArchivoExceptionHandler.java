package com.sbss.lexia.shared.exception;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * Devuelve mensajes entendibles al frontend cuando falla una carga multipart.
 * Tiene prioridad sobre el manejador general para que un archivo demasiado
 * grande no termine convertido en un mensaje genérico.
 *
 * Responde con {@link ErrorRespuesta}, el mismo formato que el resto de la API
 * ({@code timestamp, estado, codigo, mensaje, detalles}) — ver §6 de CLAUDE.md.
 * Angular tiene una interfaz con esos campos exactos en auth.models.ts, así que
 * un cuerpo con otras claves (por ejemplo `status` en vez de `estado`) le llega
 * con todo en undefined y no puede mostrar nada útil.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CargaArchivoExceptionHandler {

    @Value("${lexia.documentos.max-upload-mb:250}")
    private long maxUploadMb;

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorRespuesta> manejarTamanoExcedido(
            MaxUploadSizeExceededException ex) {
        return archivoDemasiadoGrande();
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorRespuesta> manejarMultipart(MultipartException ex) {
        if (esProblemaDeTamano(ex)) {
            return archivoDemasiadoGrande();
        }

        return ResponseEntity.badRequest().body(ErrorRespuesta.de(
                HttpStatus.BAD_REQUEST.value(),
                "ARCHIVO_INVALIDO",
                "No se pudo procesar el archivo enviado. Verifica el formato e inténtalo nuevamente."));
    }

    private ResponseEntity<ErrorRespuesta> archivoDemasiadoGrande() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ErrorRespuesta.de(
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "ARCHIVO_DEMASIADO_GRANDE",
                "El archivo supera el tamaño máximo permitido de " + maxUploadMb + " MB."));
    }

    private static boolean esProblemaDeTamano(Throwable error) {
        Throwable actual = error;
        while (actual != null) {
            if (actual instanceof MaxUploadSizeExceededException) return true;
            String mensaje = actual.getMessage();
            if (mensaje != null) {
                String normal = mensaje.toLowerCase();
                if (normal.contains("size limit")
                        || normal.contains("maximum permitted size")
                        || normal.contains("request was rejected because its size")
                        || normal.contains("too large")) {
                    return true;
                }
            }
            actual = actual.getCause();
        }
        return false;
    }
}
