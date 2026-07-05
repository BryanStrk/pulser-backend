package com.bryanstrk.pulser.entrada.dto;

import com.bryanstrk.pulser.entrada.EstadoEntrada;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record EntradaResponseDto(
        UUID id,
        EstadoEntrada estado,
        BigDecimal precio,
        String tipoEntradaNombre,
        String codigoQr,
        LocalDateTime fechaCompra,
        LocalDateTime fechaUso,
        EventoEntradaDto evento
) {
}
