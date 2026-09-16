package com.sbss.lexia.shared.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
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
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CargaArchivoExceptionHandler {

    @Value("${lexia.documentos.max-upload-mb:250}")
    private long maxUploadMb;

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> manejarTamanoExcedido(
            MaxUploadSizeExceededException ex) {
        return respuesta(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "El archivo supera el tamaño máximo permitido de " + maxUploadMb + " MB.");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> manejarMultipart(MultipartException ex) {
        if (esProblemaDeTamano(ex)) {
            return respuesta(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "El archivo supera el tamaño máximo permitido de " + maxUploadMb + " MB.");
        }

        return respuesta(
                HttpStatus.BAD_REQUEST,
                "No se pudo procesar el archivo enviado. Verifica el formato e inténtalo nuevamente.");
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

    private static ResponseEntity<Map<String, Object>> respuesta(
            HttpStatus estado,
            String mensaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", LocalDateTime.now());
        cuerpo.put("status", estado.value());
        cuerpo.put("error", estado.getReasonPhrase());
        cuerpo.put("mensaje", mensaje);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
