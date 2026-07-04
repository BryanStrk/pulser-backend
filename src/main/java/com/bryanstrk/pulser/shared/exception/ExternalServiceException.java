package com.bryanstrk.pulser.shared.exception;

/**
 * Fallo al hablar con un servicio externo (p. ej. Cloudinary). Se mapea a 503.
 * El mensaje real (cause) queda en el log, nunca en la respuesta HTTP.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
