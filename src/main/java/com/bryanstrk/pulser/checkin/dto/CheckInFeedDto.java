package com.bryanstrk.pulser.checkin.dto;

import com.bryanstrk.pulser.checkin.ResultadoCheckIn;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mensaje que se empuja al dashboard del organizador por cada validacion en puerta (VALIDO,
 * YA_USADA o INVALIDA). Sin datos sensibles del comprador: solo lo que la pantalla de control
 * necesita. 'timestamp' es el instante de la validacion.
 */
public record CheckInFeedDto(
        ResultadoCheckIn resultado,
        UUID entradaId,
        String nombreEvento,
        String tipoEntrada,
        String puerta,
        LocalDateTime timestamp
) {
}
