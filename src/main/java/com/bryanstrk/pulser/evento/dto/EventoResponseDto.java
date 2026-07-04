package com.bryanstrk.pulser.evento.dto;

import com.bryanstrk.pulser.evento.CategoriaEvento;
import com.bryanstrk.pulser.evento.EstadoEvento;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Detalle completo de un evento, con su organizador y sus tipos de entrada.
 */
public record EventoResponseDto(
        Long id,
        String nombre,
        String descripcion,
        String recinto,
        String ciudad,
        LocalDateTime fechaEvento,
        CategoriaEvento categoria,
        String imagenUrl,
        EstadoEvento estado,
        OrganizadorDto organizador,
        LocalDateTime createdAt,
        List<TipoEntradaResponseDto> tiposEntrada
) {
}
