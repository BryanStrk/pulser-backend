package com.bryanstrk.pulser.evento;

import com.bryanstrk.pulser.evento.dto.TipoEntradaRequestDto;
import com.bryanstrk.pulser.evento.dto.TipoEntradaResponseDto;
import com.bryanstrk.pulser.shared.exception.BusinessException;
import com.bryanstrk.pulser.shared.security.CurrentUserService;
import com.bryanstrk.pulser.usuario.RolUsuario;
import com.bryanstrk.pulser.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TipoEntradaServiceTest {

    @Mock
    private TipoEntradaRepository tipoEntradaRepository;
    @Mock
    private EventoRepository eventoRepository;
    @Mock
    private CurrentUserService currentUserService;

    private TipoEntradaService tipoEntradaService;

    @BeforeEach
    void setUp() {
        tipoEntradaService = new TipoEntradaService(
                tipoEntradaRepository, eventoRepository, new TipoEntradaMapper(), currentUserService);
    }

    // ---------------------------------------------------------------- helpers

    private Usuario usuario(Long id, RolUsuario rol) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre("Usuario " + id);
        usuario.setRol(rol);
        return usuario;
    }

    private Evento evento(EstadoEvento estado, Usuario organizador) {
        Evento evento = new Evento();
        evento.setId(10L);
        evento.setEstado(estado);
        evento.setOrganizador(organizador);
        return evento;
    }

    private TipoEntrada tipoEntrada(int vendidas, Evento evento) {
        TipoEntrada tipo = new TipoEntrada();
        tipo.setId(1L);
        tipo.setEvento(evento);
        tipo.setNombre("General");
        tipo.setPrecio(new BigDecimal("30.00"));
        tipo.setAforo(100);
        tipo.setVendidas(vendidas);
        return tipo;
    }

    private TipoEntradaRequestDto request(int aforo) {
        return new TipoEntradaRequestDto("VIP", new BigDecimal("50.00"), aforo, "zona vip");
    }

    // ---------------------------------------------------------------- crear

    @Test
    void crear_comoDueno_ok() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        when(eventoRepository.findById(10L)).thenReturn(Optional.of(evento(EstadoEvento.BORRADOR, dueno)));
        when(currentUserService.getCurrentUsuario()).thenReturn(dueno);
        when(tipoEntradaRepository.save(any(TipoEntrada.class))).thenAnswer(invocation -> {
            TipoEntrada t = invocation.getArgument(0);
            t.setId(50L);
            return t;
        });

        TipoEntradaResponseDto response = tipoEntradaService.crear(10L, request(200));

        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.nombre()).isEqualTo("VIP");
        assertThat(response.aforo()).isEqualTo(200);
        assertThat(response.vendidas()).isZero();
    }

    @Test
    void crear_comoTercero_lanzaAccessDenied() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        Usuario tercero = usuario(3L, RolUsuario.ORGANIZADOR);
        when(eventoRepository.findById(10L)).thenReturn(Optional.of(evento(EstadoEvento.BORRADOR, dueno)));
        when(currentUserService.getCurrentUsuario()).thenReturn(tercero);

        assertThatThrownBy(() -> tipoEntradaService.crear(10L, request(200)))
                .isInstanceOf(AccessDeniedException.class);
        verify(tipoEntradaRepository, never()).save(any());
    }

    @Test
    void crear_eventoCancelado_lanzaBusinessException() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        when(eventoRepository.findById(10L)).thenReturn(Optional.of(evento(EstadoEvento.CANCELADO, dueno)));
        when(currentUserService.getCurrentUsuario()).thenReturn(dueno);

        assertThatThrownBy(() -> tipoEntradaService.crear(10L, request(200)))
                .isInstanceOf(BusinessException.class);
        verify(tipoEntradaRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- actualizar

    @Test
    void actualizar_aforoMayorOigualQueVendidas_ok() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        Evento evento = evento(EstadoEvento.BORRADOR, dueno);
        when(tipoEntradaRepository.findById(1L)).thenReturn(Optional.of(tipoEntrada(10, evento)));
        when(currentUserService.getCurrentUsuario()).thenReturn(dueno);

        TipoEntradaResponseDto response = tipoEntradaService.actualizar(1L, request(20));

        assertThat(response.aforo()).isEqualTo(20);
    }

    @Test
    void actualizar_aforoMenorQueVendidas_lanzaBusinessException() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        Evento evento = evento(EstadoEvento.BORRADOR, dueno);
        when(tipoEntradaRepository.findById(1L)).thenReturn(Optional.of(tipoEntrada(10, evento)));
        when(currentUserService.getCurrentUsuario()).thenReturn(dueno);

        assertThatThrownBy(() -> tipoEntradaService.actualizar(1L, request(5)))
                .isInstanceOf(BusinessException.class);
    }

    // ---------------------------------------------------------------- eliminar

    @Test
    void eliminar_sinVentas_ok() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        Evento evento = evento(EstadoEvento.BORRADOR, dueno);
        TipoEntrada tipo = tipoEntrada(0, evento);
        when(tipoEntradaRepository.findById(1L)).thenReturn(Optional.of(tipo));
        when(currentUserService.getCurrentUsuario()).thenReturn(dueno);

        tipoEntradaService.eliminar(1L);

        verify(tipoEntradaRepository).delete(tipo);
    }

    @Test
    void eliminar_conVentas_lanzaBusinessException() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        Evento evento = evento(EstadoEvento.BORRADOR, dueno);
        when(tipoEntradaRepository.findById(1L)).thenReturn(Optional.of(tipoEntrada(5, evento)));
        when(currentUserService.getCurrentUsuario()).thenReturn(dueno);

        assertThatThrownBy(() -> tipoEntradaService.eliminar(1L))
                .isInstanceOf(BusinessException.class);
        verify(tipoEntradaRepository, never()).delete(any());
    }
}
