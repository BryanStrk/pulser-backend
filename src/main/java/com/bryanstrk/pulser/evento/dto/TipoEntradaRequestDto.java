package com.bryanstrk.pulser.evento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Alta y edicion de un tipo de entrada. 'vendidas' lo gestiona el servidor (no viaja en el DTO).
 */
public record TipoEntradaRequestDto(

        @NotBlank
        String nombre,

        @NotNull
        @PositiveOrZero
        BigDecimal precio,

        @NotNull
        @Positive
        Integer aforo,

        String descripcion
) {
}
