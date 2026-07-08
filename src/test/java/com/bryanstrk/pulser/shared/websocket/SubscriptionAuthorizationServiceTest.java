package com.bryanstrk.pulser.shared.websocket;

import com.bryanstrk.pulser.evento.EventoRepository;
import com.bryanstrk.pulser.usuario.RolUsuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionAuthorizationServiceTest {

    private static final Long EVENTO_ID = 10L;

    @Mock
    private EventoRepository eventoRepository;

    private SubscriptionAuthorizationService service() {
        return new SubscriptionAuthorizationService(eventoRepository);
    }

    private WsUser user(Long id, RolUsuario rol) {
        return new WsUser(id, "user" + id + "@pulser.test", rol);
    }

    @Test
    void admin_siemprePuede_sinConsultarBd() {
        boolean puede = service().puedeSuscribirse(EVENTO_ID, user(99L, RolUsuario.ADMIN));

        assertThat(puede).isTrue();
        verifyNoInteractions(eventoRepository);
    }

    @Test
    void organizadorDueno_puede() {
        WsUser organizador = user(1L, RolUsuario.ORGANIZADOR);
        when(eventoRepository.existsByIdAndOrganizador_Id(EVENTO_ID, 1L)).thenReturn(true);

        assertThat(service().puedeSuscribirse(EVENTO_ID, organizador)).isTrue();
    }

    @Test
    void organizadorAjeno_noPuede() {
        WsUser ajeno = user(2L, RolUsuario.ORGANIZADOR);
        when(eventoRepository.existsByIdAndOrganizador_Id(EVENTO_ID, 2L)).thenReturn(false);

        assertThat(service().puedeSuscribirse(EVENTO_ID, ajeno)).isFalse();
    }

    @Test
    void asistente_noPuede() {
        WsUser asistente = user(3L, RolUsuario.ASISTENTE);
        when(eventoRepository.existsByIdAndOrganizador_Id(EVENTO_ID, 3L)).thenReturn(false);

        assertThat(service().puedeSuscribirse(EVENTO_ID, asistente)).isFalse();
        verify(eventoRepository).existsByIdAndOrganizador_Id(EVENTO_ID, 3L);
        verify(eventoRepository, never()).findById(EVENTO_ID);
    }
}
