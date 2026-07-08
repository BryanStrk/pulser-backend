package com.bryanstrk.pulser.checkin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Peticion de validacion en puerta. 'token' es el QR firmado leido por el escaner;
 * 'puerta' es opcional (identificador legible del acceso, p. ej. "Puerta A").
 */
public record CheckInRequestDto(
        @NotBlank String token,
        String puerta
) {
}
