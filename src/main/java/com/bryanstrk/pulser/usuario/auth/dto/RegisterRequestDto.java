package com.bryanstrk.pulser.usuario.auth.dto;

import com.bryanstrk.pulser.usuario.RolUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registro publico. El campo rol es OPCIONAL y se restringe por whitelist en servidor
 * (solo ASISTENTE u ORGANIZADOR); ADMIN nunca es asignable via API.
 */
public record RegisterRequestDto(

        @NotBlank
        String nombre,

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(min = 8)
        String password,

        RolUsuario rol
) {
}
