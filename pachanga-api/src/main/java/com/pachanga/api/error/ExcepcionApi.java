package com.pachanga.api.error;

import org.springframework.http.HttpStatus;

/**
 * Error "esperado" que queremos devolver a la app con un código HTTP y un mensaje claro.
 * Por ejemplo: new ExcepcionApi(HttpStatus.CONFLICT, "Ya hay una cuenta con ese email").
 * ManejadorErrores lo convierte en la respuesta JSON.
 */
public class ExcepcionApi extends RuntimeException {

    private final HttpStatus estado;

    public ExcepcionApi(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public HttpStatus getEstado() {
        return estado;
    }
}
