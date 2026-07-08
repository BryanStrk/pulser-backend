package com.bryanstrk.pulser.shared.websocket;

import com.bryanstrk.pulser.evento.EventoRepository;
import com.bryanstrk.pulser.shared.security.JwtService;
import com.bryanstrk.pulser.usuario.RolUsuario;
import com.bryanstrk.pulser.usuario.Usuario;
import com.bryanstrk.pulser.usuario.UsuarioRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seguridad de la capa STOMP. Un unico interceptor del canal inbound que resuelve dos comandos:
 *
 * - CONNECT  -> AUTENTICACION: valida el JWT que viaja en la cabecera nativa 'Authorization'
 *   del frame (Bearer), reutilizando JwtService (no reimplementa la firma), y fija el Principal
 *   (WsUser) de la sesion. Si no hay token valido, el CONNECT se rechaza (el cliente recibe ERROR
 *   y la sesion nunca queda CONNECTED).
 *
 * - SUBSCRIBE -> AUTORIZACION: exige que el Principal fijado en el CONNECT sea el ORGANIZADOR del
 *   evento del destino (/topic/eventos/{id}/checkins) o un ADMIN. La comprobacion es en servidor,
 *   nunca por confianza en el cliente. Un SUBSCRIBE sin CONNECT previo valido (getUser()==null) o a
 *   un destino que no casa el patron se rechaza con AccessDeniedException -> ERROR frame limpio,
 *   sin NPE y sin tumbar el broker.
 *
 * Nunca se registran el token ni la firma.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    // Unico patron de destino permitido: el feed de check-ins por evento.
    private static final Pattern DESTINO_FEED = Pattern.compile("^/topic/eventos/(\\d+)/checkins$");

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final EventoRepository eventoRepository;

    public StompAuthChannelInterceptor(JwtService jwtService,
                                       UsuarioRepository usuarioRepository,
                                       EventoRepository eventoRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.eventoRepository = eventoRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        // Frames sin accessor STOMP o sin comando (p. ej. heartbeats): dejar pasar.
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> autenticar(accessor);
            case SUBSCRIBE -> autorizarSuscripcion(accessor);
            default -> {
                // Otros comandos (DISCONNECT, UNSUBSCRIBE, ...): sin control adicional.
            }
        }
        return message;
    }

    // ------------------------------------------------------------ CONNECT: autenticacion

    private void autenticar(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("Falta el token en el CONNECT");
        }
        String token = authHeader.substring(BEARER_PREFIX.length());

        Claims claims;
        try {
            claims = jwtService.parseClaims(token);
        } catch (JwtException ex) {
            // Firma invalida / expirado / malformado. No registrar el token.
            throw new AccessDeniedException("Token WebSocket invalido");
        }

        String email = claims.getSubject();
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("Usuario no encontrado"));

        accessor.setUser(new WsUser(usuario.getId(), usuario.getEmail(), usuario.getRol()));
    }

    // ------------------------------------------------------------ SUBSCRIBE: autorizacion

    private void autorizarSuscripcion(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        // Sin CONNECT valido previo no hay Principal: rechazo explicito (no NPE).
        if (!(user instanceof WsUser wsUser)) {
            throw new AccessDeniedException("Suscripcion sin sesion autenticada");
        }

        Long eventoId = extraerEventoId(accessor.getDestination());
        if (eventoId == null) {
            // Deny-by-default: cualquier destino que no sea el feed de check-ins.
            throw new AccessDeniedException("Destino de suscripcion no permitido");
        }

        boolean esAdmin = wsUser.rol() == RolUsuario.ADMIN;
        boolean esOrganizador = eventoRepository.existsByIdAndOrganizador_Id(eventoId, wsUser.id());
        if (!esAdmin && !esOrganizador) {
            throw new AccessDeniedException("No puedes suscribirte al feed de este evento");
        }
    }

    private Long extraerEventoId(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = DESTINO_FEED.matcher(destination);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
