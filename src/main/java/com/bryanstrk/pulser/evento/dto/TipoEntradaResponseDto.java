package com.bryanstrk.pulser.evento.dto;

import java.math.BigDecimal;

public record TipoEntradaResponseDto(
        Long id,
        String nombre,
        BigDecimal precio,
        Integer aforo,
        Integer vendidas,
        String descripcion
) {
}
