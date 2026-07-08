package com.bryanstrk.pulser.checkin.dto;

import com.bryanstrk.pulser.checkin.ResultadoCheckIn;

import java.util.UUID;

/**
 * Resultado de un intento de validacion en puerta.
 * 'entradaId', 'nombreEvento' y 'tipoEntrada' solo se rellenan cuando hay una entrada
 * resuelta (firma valida + entrada existente); en un INVALIDA por firma rota o entrada
 * inexistente llegan null: no hay entrada de la que informar y no queda rastro en auditoria.
 */
public record CheckInResponseDto(
        ResultadoCheckIn resultado,
        UUID entradaId,
        String mensaje,
        String nombreEvento,
        String tipoEntrada
) {

    /**
     * INVALIDA sin entrada resuelta (firma rota / formato invalido / entrada inexistente):
     * no genera fila de CheckIn ni datos de pantalla.
     */
    public static CheckInResponseDto sinEntrada(String mensaje) {
        return new CheckInResponseDto(ResultadoCheckIn.INVALIDA, null, mensaje, null, null);
    }
}
