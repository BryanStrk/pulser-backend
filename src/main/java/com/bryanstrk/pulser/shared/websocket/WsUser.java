package com.bryanstrk.pulser.shared.websocket;

import com.bryanstrk.pulser.usuario.RolUsuario;

import java.security.Principal;

/**
 * Principal autenticado de una sesion WebSocket/STOMP. Lo fija el interceptor en el frame
 * CONNECT (a partir del JWT) y Spring lo repuebla en los frames posteriores (SUBSCRIBE),
 * de modo que la autorizacion de suscripcion dispone de id y rol sin volver a tocar BD.
 * getName() devuelve el email (identidad = subject del JWT), coherente con el resto del sistema.
 */
public record WsUser(Long id, String email, RolUsuario rol) implements Principal {

    @Override
    public String getName() {
        return email;
    }
}

