package com.bryanstrk.pulser.shared.exception;

/**
 * Entrada del cliente invalida que la validacion declarativa (@Valid) no cubre
 * (p. ej. un fichero subido con content-type o tamaño no permitido). Se mapea a 400.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
