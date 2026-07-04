package com.bryanstrk.pulser.evento.dto;

import com.bryanstrk.pulser.evento.CategoriaEvento;
import com.bryanstrk.pulser.evento.EstadoEvento;

import java.time.LocalDateTime;

/**
 * Item de listado (sin descripcion ni tipos de entrada, para respuestas paginadas ligeras).
 */
public record EventoResumenDto(
        Long id,
        String nombre,
        String ciudad,
        LocalDateTime fechaEvento,
        CategoriaEvento categoria,
        EstadoEvento estado,
        String imagenUrl
) {
}
