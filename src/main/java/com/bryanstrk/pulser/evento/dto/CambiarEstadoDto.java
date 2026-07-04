package com.bryanstrk.pulser.evento.dto;

import com.bryanstrk.pulser.evento.EstadoEvento;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoDto(

        @NotNull
        EstadoEvento nuevoEstado
) {
}
