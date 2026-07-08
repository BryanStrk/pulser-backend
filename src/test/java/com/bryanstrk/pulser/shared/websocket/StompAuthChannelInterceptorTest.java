package com.bryanstrk.pulser.shared.websocket;

import com.bryanstrk.pulser.shared.security.JwtService;
import com.bryanstrk.pulser.usuario.RolUsuario;
import com.bryanstrk.pulser.usuario.Usuario;
import com.bryanstrk.pulser.usuario.UsuarioRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    private static final String TOKEN = "un.jwt.valido";
    private static final String EMAIL = "org@pulser.test";
    private static final Long EVENTO_ID = 10L;
    private static final String DESTINO = "/topic/eventos/10/checkins";

    @Mock
    private JwtService jwtService;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private SubscriptionAuthorizationService subscriptionAuthorizationService;
    @Mock
    private MessageChannel channel;

    private StompAuthChannelInterceptor interceptor() {
        return new StompAuthChannelInterceptor(jwtService, usuarioRepository, subscriptionAuthorizationService);
    }

    // Construye un Message STOMP con accessor mutable, para que setUser() dentro del preSend
    // se refleje en el mismo accessor que inspeccionamos despues.
    private Message<byte[]> mensaje(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Usuario usuario(Long id, RolUsuario rol) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setEmail(EMAIL);
        usuario.setRol(rol);
        return usuario;
    }

    // ---------------------------------------------------------------- CONNECT

    @Test
    void connect_tokenValido_fijaWsUserComoPrincipal() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn(EMAIL);
        when(jwtService.parseClaims(TOKEN)).thenReturn(claims);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario(1L, RolUsuario.ORGANIZADOR)));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + TOKEN);

        interceptor().preSend(mensaje(accessor), channel);

        assertThat(accessor.getUser()).isInstanceOf(WsUser.class);
        WsUser user = (WsUser) accessor.getUser();
        assertThat(user.id()).isEqualTo(1L);
        assertThat(user.email()).isEqualTo(EMAIL);
        assertThat(user.rol()).isEqualTo(RolUsuario.ORGANIZADOR);
    }

    @Test
    void connect_sinCabeceraAuthorization_rechazaSinTocarJwt() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);

        assertThatThrownBy(() -> interceptor().preSend(mensaje(accessor), channel))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jwtService, usuarioRepository);
    }

    @Test
    void connect_tokenInvalido_rechaza() {
        when(jwtService.parseClaims(TOKEN)).thenThrow(new JwtException("firma invalida"));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + TOKEN);

        assertThatThrownBy(() -> interceptor().preSend(mensaje(accessor), channel))
                .isInstanceOf(AccessDeniedException.class);
        verify(usuarioRepository, never()).findByEmail(any());
    }

    @Test
    void connect_usuarioInexistente_rechaza() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn(EMAIL);
        when(jwtService.parseClaims(TOKEN)).thenReturn(claims);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + TOKEN);

        assertThatThrownBy(() -> interceptor().preSend(mensaje(accessor), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------------------------------------------------------------- SUBSCRIBE

    @Test
    void subscribe_autorizado_delegaEnServicioYPasa() {
        WsUser user = new WsUser(1L, EMAIL, RolUsuario.ORGANIZADOR);
        when(subscriptionAuthorizationService.puedeSuscribirse(EVENTO_ID, user)).thenReturn(true);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(user);
        accessor.setDestination(DESTINO);

        interceptor().preSend(mensaje(accessor), channel);

        verify(subscriptionAuthorizationService).puedeSuscribirse(EVENTO_ID, user);
    }

    @Test
    void subscribe_noAutorizado_rechaza() {
        WsUser user = new WsUser(2L, EMAIL, RolUsuario.ORGANIZADOR);
        when(subscriptionAuthorizationService.puedeSuscribirse(EVENTO_ID, user)).thenReturn(false);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(user);
        accessor.setDestination(DESTINO);

        assertThatThrownBy(() -> interceptor().preSend(mensaje(accessor), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribe_sinSesionAutenticada_rechazaSinNpeNiConsultar() {
        // SUBSCRIBE sin CONNECT previo: getUser() == null.
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(DESTINO);

        assertThatThrownBy(() -> interceptor().preSend(mensaje(accessor), channel))
                .isInstanceOf(AccessDeniedException.class);
        verify(subscriptionAuthorizationService, never()).puedeSuscribirse(anyLong(), any());
    }

    @Test
    void subscribe_destinoMalformado_rechazaSinConsultar() {
        WsUser user = new WsUser(1L, EMAIL, RolUsuario.ORGANIZADOR);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(user);
        accessor.setDestination("/topic/otra-cosa");

        assertThatThrownBy(() -> interceptor().preSend(mensaje(accessor), channel))
                .isInstanceOf(AccessDeniedException.class);
        verify(subscriptionAuthorizationService, never()).puedeSuscribirse(anyLong(), any());
    }

    @Test
    void subscribe_destinoDeOtroEvento_extraeIdCorrecto() {
        WsUser user = new WsUser(1L, EMAIL, RolUsuario.ADMIN);
        when(subscriptionAuthorizationService.puedeSuscribirse(eq(42L), any())).thenReturn(true);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(user);
        accessor.setDestination("/topic/eventos/42/checkins");

        interceptor().preSend(mensaje(accessor), channel);

        verify(subscriptionAuthorizationService).puedeSuscribirse(42L, user);
    }
}
