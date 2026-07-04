package com.bryanstrk.pulser.usuario.auth.dto;

import com.bryanstrk.pulser.usuario.RolUsuario;

public record AuthResponseDto(
        String token,
        String tipo,
        Long id,
        String nombre,
        String email,
        RolUsuario rol
) {
}
