package com.bryanstrk.pulser.entrada.dto;

import java.time.LocalDateTime;

/**
 * Datos del evento embebidos en la respuesta de una entrada.
 */
public record EventoEntradaDto(
        Long id,
        String nombre,
        LocalDateTime fechaEvento,
        String recinto,
        String ciudad
) {
}
