package com.bryanstrk.pulser.entrada;

import com.bryanstrk.pulser.entrada.dto.CrearEntradaRequestDto;
import com.bryanstrk.pulser.entrada.dto.EntradaResponseDto;
import com.bryanstrk.pulser.evento.CategoriaEvento;
import com.bryanstrk.pulser.evento.EstadoEvento;
import com.bryanstrk.pulser.evento.Evento;
import com.bryanstrk.pulser.evento.EventoRepository;
import com.bryanstrk.pulser.evento.TipoEntrada;
import com.bryanstrk.pulser.evento.TipoEntradaRepository;
import com.bryanstrk.pulser.shared.exception.BusinessException;
import com.bryanstrk.pulser.shared.exception.ResourceNotFoundException;
import com.bryanstrk.pulser.shared.security.CurrentUserService;
import com.bryanstrk.pulser.usuario.RolUsuario;
import com.bryanstrk.pulser.usuario.Usuario;
import com.bryanstrk.pulser.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntradaServiceTest {

    private static final Long EVENTO_ID = 10L;
    private static final Long TIPO_ID = 5L;

    @Mock
    private EntradaRepository entradaRepository;
    @Mock
    private EventoRepository eventoRepository;
    @Mock
    private TipoEntradaRepository tipoEntradaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private CurrentUserService currentUserService;

    private EntradaService entradaService;

    @BeforeEach
    void setUp() {
        entradaService = new EntradaService(
                entradaRepository, eventoRepository, tipoEntradaRepository,
                usuarioRepository, new EntradaMapper(), currentUserService);
    }

    // ---------------------------------------------------------------- helpers

    private Usuario usuario(Long id, RolUsuario rol) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre("Usuario " + id);
        usuario.setRol(rol);
        return usuario;
    }

    private Evento evento(Long id, EstadoEvento estado, LocalDateTime fecha) {
        Evento evento = new Evento();
        evento.setId(id);
        evento.setNombre("Concierto");
        evento.setRecinto("WiZink");
        evento.setCiudad("Madrid");
        evento.setFechaEvento(fecha);
        evento.setCategoria(CategoriaEvento.CONCIERTO);
        evento.setEstado(estado);
        return evento;
    }

    private TipoEntrada tipoEntrada(Evento evento, BigDecimal precio) {
        TipoEntrada tipo = new TipoEntrada();
        tipo.setId(TIPO_ID);
        tipo.setEvento(evento);
        tipo.setNombre("General");
        tipo.setPrecio(precio);
        tipo.setAforo(100);
        tipo.setVendidas(0);
        return tipo;
    }

    private LocalDateTime futuro() {
        return LocalDateTime.now().plusDays(30);
    }

    // ---------------------------------------------------------------- comprar

    @Test
    void comprar_ok_creaEntradaValidaConSnapshotDePrecio() {
        Usuario comprador = usuario(1L, RolUsuario.ASISTENTE);
        Evento evento = evento(EVENTO_ID, EstadoEvento.PUBLICADO, futuro());
        TipoEntrada tipo = tipoEntrada(evento, new BigDecimal("30.00"));

        when(currentUserService.getCurrentUsuario()).thenReturn(comprador);
        when(eventoRepository.findById(EVENTO_ID)).thenReturn(Optional.of(evento));
        when(tipoEntradaRepository.findById(TIPO_ID)).thenReturn(Optional.of(tipo));
        when(entradaRepository.reservarPlaza(TIPO_ID)).thenReturn(1);
        when(eventoRepository.getReferenceById(EVENTO_ID)).thenReturn(evento);
        when(tipoEntradaRepository.getReferenceById(TIPO_ID)).thenReturn(tipo);
        when(usuarioRepository.getReferenceById(1L)).thenReturn(comprador);
        when(entradaRepository.saveAndFlush(any(Entrada.class))).thenAnswer(invocation -> {
            Entrada e = invocation.getArgument(0);
            e.setId(UUID.randomUUID());
            e.setFechaCompra(LocalDateTime.now());
            return e;
        });

        EntradaResponseDto response = entradaService.comprar(EVENTO_ID, new CrearEntradaRequestDto(TIPO_ID));

        ArgumentCaptor<Entrada> captor = ArgumentCaptor.forClass(Entrada.class);
        verify(entradaRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoEntrada.VALIDA);
        assertThat(captor.getValue().getPrecio()).isEqualByComparingTo("30.00");

        assertThat(response.estado()).isEqualTo(EstadoEntrada.VALIDA);
        assertThat(response.precio()).isEqualByComparingTo("30.00");
        assertThat(response.tipoEntradaNombre()).isEqualTo("General");
        assertThat(response.codigoQr()).isNull();
        assertThat(response.evento().id()).isEqualTo(EVENTO_ID);
    }

    @Test
    void comprar_eventoNoPublicado_lanzaBusinessException() {
        Usuario comprador = usuario(1L, RolUsuario.ASISTENTE);
        Evento evento = evento(EVENTO_ID, EstadoEvento.BORRADOR, futuro());
        TipoEntrada tipo = tipoEntrada(evento, new BigDecimal("30.00"));

        when(currentUserService.getCurrentUsuario()).thenReturn(comprador);
        when(eventoRepository.findById(EVENTO_ID)).thenReturn(Optional.of(evento));
        when(tipoEntradaRepository.findById(TIPO_ID)).thenReturn(Optional.of(tipo));

        assertThatThrownBy(() -> entradaService.comprar(EVENTO_ID, new CrearEntradaRequestDto(TIPO_ID)))
                .isInstanceOf(BusinessException.class);
        verify(entradaRepository, never()).reservarPlaza(anyLong());
    }

    @Test
    void comprar_fechaPasada_lanzaBusinessException() {
        Usuario comprador = usuario(1L, RolUsuario.ASISTENTE);
        Evento evento = evento(EVENTO_ID, EstadoEvento.PUBLICADO, LocalDateTime.now().minusDays(1));
        TipoEntrada tipo = tipoEntrada(evento, new BigDecimal("30.00"));

        when(currentUserService.getCurrentUsuario()).thenReturn(comprador);
        when(eventoRepository.findById(EVENTO_ID)).thenReturn(Optional.of(evento));
        when(tipoEntradaRepository.findById(TIPO_ID)).thenReturn(Optional.of(tipo));

        assertThatThrownBy(() -> entradaService.comprar(EVENTO_ID, new CrearEntradaRequestDto(TIPO_ID)))
                .isInstanceOf(BusinessException.class);
        verify(entradaRepository, never()).reservarPlaza(anyLong());
    }

    @Test
    void comprar_tipoDeOtroEvento_lanza404Enmascarado() {
        Usuario comprador = usuario(1L, RolUsuario.ASISTENTE);
        Evento evento = evento(EVENTO_ID, EstadoEvento.PUBLICADO, futuro());
        // El tipo pertenece a un evento distinto (99).
        TipoEntrada tipo = tipoEntrada(evento(99L, EstadoEvento.PUBLICADO, futuro()), new BigDecimal("30.00"));

        when(currentUserService.getCurrentUsuario()).thenReturn(comprador);
        when(eventoRepository.findById(EVENTO_ID)).thenReturn(Optional.of(evento));
        when(tipoEntradaRepository.findById(TIPO_ID)).thenReturn(Optional.of(tipo));

        assertThatThrownBy(() -> entradaService.comprar(EVENTO_ID, new CrearEntradaRequestDto(TIPO_ID)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(entradaRepository, never()).reservarPlaza(anyLong());
    }

    @Test
    void comprar_aforoAgotado_lanzaBusinessException() {
        Usuario comprador = usuario(1L, RolUsuario.ASISTENTE);
        Evento evento = evento(EVENTO_ID, EstadoEvento.PUBLICADO, futuro());
        TipoEntrada tipo = tipoEntrada(evento, new BigDecimal("30.00"));

        when(currentUserService.getCurrentUsuario()).thenReturn(comprador);
        when(eventoRepository.findById(EVENTO_ID)).thenReturn(Optional.of(evento));
        when(tipoEntradaRepository.findById(TIPO_ID)).thenReturn(Optional.of(tipo));
        when(entradaRepository.reservarPlaza(TIPO_ID)).thenReturn(0);

        assertThatThrownBy(() -> entradaService.comprar(EVENTO_ID, new CrearEntradaRequestDto(TIPO_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("agotad");
        verify(entradaRepository, never()).saveAndFlush(any());
    }

    // ---------------------------------------------------------------- mis-entradas

    @Test
    void misEntradas_devuelvePaginaDelUsuarioConEventoEmbebido() {
        Usuario comprador = usuario(1L, RolUsuario.ASISTENTE);
        Evento evento = evento(EVENTO_ID, EstadoEvento.PUBLICADO, futuro());
        TipoEntrada tipo = tipoEntrada(evento, new BigDecimal("30.00"));
        Entrada entrada = entrada(UUID.randomUUID(), comprador, evento, tipo);
        Pageable pageable = PageRequest.of(0, 20);

        when(currentUserService.getCurrentUsuario()).thenReturn(comprador);
        when(entradaRepository.findByUsuarioId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entrada), pageable, 1));

        Page<EntradaResponseDto> page = entradaService.misEntradas(pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).evento().id()).isEqualTo(EVENTO_ID);
        assertThat(page.getContent().get(0).tipoEntradaNombre()).isEqualTo("General");
    }

    // ---------------------------------------------------------------- obtener

    @Test
    void obtener_comoComprador_ok() {
        Usuario comprador = usuario(1L, RolUsuario.ASISTENTE);
        Evento evento = evento(EVENTO_ID, EstadoEvento.PUBLICADO, futuro());
        TipoEntrada tipo = tipoEntrada(evento, new BigDecimal("30.00"));
        UUID id = UUID.randomUUID();
        when(entradaRepository.findById(id)).thenReturn(Optional.of(entrada(id, comprador, evento, tipo)));
        when(currentUserService.getCurrentUsuario()).thenReturn(comprador);

        EntradaResponseDto response = entradaService.obtener(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.evento().id()).isEqualTo(EVENTO_ID);
    }

    @Test
    void obtener_deOtroUsuario_lanza404Enmascarado() {
        Usuario comprador = usuario(1L, RolUsuario.ASISTENTE);
        Usuario tercero = usuario(2L, RolUsuario.ASISTENTE);
        Evento evento = evento(EVENTO_ID, EstadoEvento.PUBLICADO, futuro());
        TipoEntrada tipo = tipoEntrada(evento, new BigDecimal("30.00"));
        UUID id = UUID.randomUUID();
        when(entradaRepository.findById(id)).thenReturn(Optional.of(entrada(id, comprador, evento, tipo)));
        when(currentUserService.getCurrentUsuario()).thenReturn(tercero);

        assertThatThrownBy(() -> entradaService.obtener(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Entrada entrada(UUID id, Usuario comprador, Evento evento, TipoEntrada tipo) {
        Entrada entrada = new Entrada();
        entrada.setId(id);
        entrada.setUsuario(comprador);
        entrada.setEvento(evento);
        entrada.setTipoEntrada(tipo);
        entrada.setPrecio(tipo.getPrecio());
        entrada.setEstado(EstadoEntrada.VALIDA);
        entrada.setFechaCompra(LocalDateTime.now());
        return entrada;
    }
}
