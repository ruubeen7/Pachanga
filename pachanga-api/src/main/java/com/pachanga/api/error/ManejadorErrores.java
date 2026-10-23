package com.pachanga.api.error;

import com.pachanga.api.dto.ErrorRespuesta;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Atrapa las excepciones de todos los controladores y las convierte en un JSON
 * con el mismo formato siempre, para que la app sepa leerlo:
 * { "mensaje": "...", "campos": { "email": "..." } }
 */
@RestControllerAdvice
public class ManejadorErrores {

    /** Nuestros errores con código y mensaje (409 email repetido, 401 login incorrecto...). */
    @ExceptionHandler(ExcepcionApi.class)
    public ResponseEntity<ErrorRespuesta> errorApi(ExcepcionApi e) {
        return ResponseEntity.status(e.getEstado()).body(new ErrorRespuesta(e.getMessage(), Map.of()));
    }

    /** Fallos de @Valid: devuelve 400 con el error de cada campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorRespuesta> datosNoValidos(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> campos.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErrorRespuesta("Revisa los datos del formulario", campos));
    }

    /**
     * Falta un parámetro de la URL (?lat=...) o no tiene el tipo esperado (?lat=hola).
     * Sin esto, Spring respondería con su propio formato de error y la app no sabría leerlo.
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorRespuesta> parametroNoValido(Exception e) {
        String parametro = e instanceof MissingServletRequestParameterException falta
                ? falta.getParameterName()
                : ((MethodArgumentTypeMismatchException) e).getName();
        return ResponseEntity.badRequest()
                .body(new ErrorRespuesta("Falta el dato '" + parametro + "' o no es válido", Map.of()));
    }

    /** La base de datos ha rechazado algo por una restricción UNIQUE (dos peticiones casi a la vez). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorRespuesta> datoDuplicado(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorRespuesta("Ese dato ya existe", Map.of()));
    }
}
