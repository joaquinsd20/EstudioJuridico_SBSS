package com.sbss.lexia.shared.exception;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduce excepciones al formato de error unico de la API. */
@RestControllerAdvice
public class ManejadorGlobalExcepciones {

    /**
     * Credenciales invalidas. El mensaje es deliberadamente generico: decir "ese
     * correo no existe" le confirmaria a un atacante que correos son validos.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorRespuesta> credencialesInvalidas(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorRespuesta.de(401, "CREDENCIALES_INVALIDAS",
                        "Correo o contrasena incorrectos"));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorRespuesta> cuentaInactiva(DisabledException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorRespuesta.de(403, "CUENTA_INACTIVA",
                        "La cuenta esta inactiva. Comunicate con el administrador"));
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorRespuesta> cuentaBloqueada(LockedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorRespuesta.de(403, "CUENTA_BLOQUEADA",
                        "La cuenta esta bloqueada. Comunicate con el administrador"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorRespuesta> datosInvalidos(MethodArgumentNotValidException e) {
        List<String> detalles = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();

        return ResponseEntity.badRequest().body(new ErrorRespuesta(
                LocalDateTime.now(), 400, "DATOS_INVALIDOS",
                "La peticion tiene campos invalidos", detalles));
    }
}
