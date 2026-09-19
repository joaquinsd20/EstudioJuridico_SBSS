package com.sbss.lexia.shared.exception;

import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
// En Spring Data 4 (Boot 4) esta clase se movio de
// org.springframework.data.mapping a org.springframework.data.core.
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/** Traduce excepciones al formato de error unico de la API. */
@RestControllerAdvice
public class ManejadorGlobalExcepciones {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalExcepciones.class);

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

    /**
     * Validacion de @RequestParam/@PathVariable con @Validated a nivel de
     * controller (p.ej. filtros de listado). Sin este handler, Spring devuelve
     * su propio cuerpo ProblemDetail en vez del formato ErrorRespuesta del resto
     * de la API.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorRespuesta> parametrosInvalidos(HandlerMethodValidationException e) {
        List<String> detalles = e.getAllErrors().stream()
                .map(err -> err.getDefaultMessage())
                .toList();

        return ResponseEntity.badRequest().body(new ErrorRespuesta(
                LocalDateTime.now(), 400, "DATOS_INVALIDOS",
                "La peticion tiene parametros invalidos", detalles));
    }

    /** El cuerpo de la peticion no es JSON valido, o no calza con el DTO esperado. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorRespuesta> jsonInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ErrorRespuesta.de(400, "JSON_INVALIDO",
                "El cuerpo de la peticion no se pudo interpretar"));
    }

    /**
     * `?sort=loQueSeaQueNoExiste` en un endpoint paginado. Sin este handler,
     * Spring Data deja la excepcion sin traducir y el endpoint responde 500 por
     * un simple typo del cliente en el nombre del campo.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorRespuesta> ordenInvalido(PropertyReferenceException e) {
        return ResponseEntity.badRequest().body(ErrorRespuesta.de(400, "ORDEN_INVALIDO",
                "El campo de ordenamiento indicado no existe"));
    }

    /**
     * Un INSERT/UPDATE viola una restriccion de la base: una UNIQUE KEY
     * (documento duplicado, correo duplicado) o un CHECK (formato invalido que
     * paso la validacion de Java pero no la de MySQL). Sin este handler, la
     * primera vez que alguien intente registrar un documento repetido, la API
     * devuelve 500 con el mensaje crudo de JDBC.
     *
     * Es deliberadamente generico (409, sin distinguir cual restriccion fallo):
     * un modulo que necesite un mensaje mas especifico — como "cliente
     * duplicado, aqui esta el uuid del existente" — debe detectar el caso ANTES
     * de insertar, en su propio service. Este handler es la red de seguridad
     * para lo que ese modulo no previno.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorRespuesta> datosEnConflicto(DataIntegrityViolationException e) {
        log.warn("Violacion de integridad de datos: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorRespuesta.de(409,
                "DATOS_EN_CONFLICTO",
                "El registro ya existe o viola una restriccion de la base de datos"));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorRespuesta> recursoNoEncontrado(RecursoNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorRespuesta.de(404,
                "RECURSO_NO_ENCONTRADO", e.getMessage()));
    }

    /**
     * Un parametro de la peticion no pasa una validacion propia del endpoint,
     * mas alla de lo que cubre @Valid — p.ej. un campo de ?sort= que existe en
     * la entidad pero no esta en la lista blanca de ordenamientos permitidos.
     */
    /** 400 con el codigo concreto del problema, para pintarlo bajo el campo correcto. */
    @ExceptionHandler(PasswordInvalidaException.class)
    public ResponseEntity<ErrorRespuesta> passwordInvalida(PasswordInvalidaException e) {
        return ResponseEntity.badRequest().body(ErrorRespuesta.de(400, e.getCodigo(), e.getMessage()));
    }

    @ExceptionHandler(ParametroInvalidoException.class)
    public ResponseEntity<ErrorRespuesta> parametroInvalido(ParametroInvalidoException e) {
        return ResponseEntity.badRequest().body(ErrorRespuesta.de(400,
                "PARAMETRO_INVALIDO", e.getMessage()));
    }

    /**
     * Cuando @PreAuthorize deniega, Spring Security la intercepta ANTES de que
     * llegue aqui (via ExceptionTranslationFilter -> ApiAccessDeniedHandler, que
     * arma el 403). Este handler solo se declara para que, si mas adelante se
     * agrega un catch-all de Exception mas abajo en este archivo, ese catch-all
     * no la capture primero y la convierta en un 500 silencioso. Relanzarla es
     * intencional: este metodo no arma la respuesta, solo evita que un handler
     * generico se adelante.
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public void accesoDenegado(AuthorizationDeniedException e) throws AuthorizationDeniedException {
        throw e;
    }
}
