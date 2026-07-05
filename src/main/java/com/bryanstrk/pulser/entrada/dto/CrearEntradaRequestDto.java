package com.bryanstrk.pulser.entrada.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Compra de una entrada. El evento viene en el path; aqui solo el tipo de entrada elegido.
 */
public record CrearEntradaRequestDto(

        @NotNull
        Long tipoEntradaId
) {
}
