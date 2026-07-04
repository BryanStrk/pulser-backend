package com.bryanstrk.pulser.evento.dto;

import com.bryanstrk.pulser.evento.CategoriaEvento;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Alta y edicion de un evento. NO incluye estado (nace BORRADOR; se cambia via PATCH)
 * ni organizador (se asigna desde el usuario autenticado).
 */
public record EventoRequestDto(

        @NotBlank
        String nombre,

        String descripcion,

        @NotBlank
        String recinto,

        @NotBlank
        String ciudad,

        @NotNull
        @Future
        LocalDateTime fechaEvento,

        @NotNull
        CategoriaEvento categoria,

        String imagenUrl
) {
}
