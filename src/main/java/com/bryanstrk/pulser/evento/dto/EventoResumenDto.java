package com.bryanstrk.pulser.evento.dto;

import com.bryanstrk.pulser.evento.CategoriaEvento;
import com.bryanstrk.pulser.evento.EstadoEvento;

import java.time.LocalDateTime;

/**
 * Item de listado (sin descripcion ni tipos de entrada, para respuestas paginadas ligeras).
 * 'aforoTotal' y 'entradasVendidas' son la ocupacion agregada de sus tipos de entrada (0/0 si
 * el evento no tiene ninguno), para la barra de aforo del frontend.
 */
public record EventoResumenDto(
        Long id,
        String nombre,
        String ciudad,
        LocalDateTime fechaEvento,
        CategoriaEvento categoria,
        EstadoEvento estado,
        String imagenUrl,
        long aforoTotal,
        long entradasVendidas
) {
}
