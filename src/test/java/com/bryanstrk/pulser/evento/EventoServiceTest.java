package com.bryanstrk.pulser.evento;

import com.bryanstrk.pulser.evento.dto.CambiarEstadoDto;
import com.bryanstrk.pulser.evento.dto.EventoRequestDto;
import com.bryanstrk.pulser.evento.dto.EventoResponseDto;
import com.bryanstrk.pulser.shared.exception.BusinessException;
import com.bryanstrk.pulser.shared.exception.ResourceNotFoundException;
import com.bryanstrk.pulser.shared.security.CurrentUserService;
import com.bryanstrk.pulser.usuario.RolUsuario;
import com.bryanstrk.pulser.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventoServiceTest {

    @Mock
    private EventoRepository eventoRepository;
    @Mock
    private TipoEntradaRepository tipoEntradaRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ImageStorageService imageStorageService;

    private EventoService eventoService;

    @BeforeEach
    void setUp() {
        // Mappers reales: queremos afirmar sobre el estado de las entidades.
        EventoMapper eventoMapper = new EventoMapper(new TipoEntradaMapper());
        eventoService = new EventoService(
                eventoRepository, tipoEntradaRepository, eventoMapper, currentUserService, imageStorageService);
    }

    // ---------------------------------------------------------------- helpers

    private Usuario usuario(Long id, RolUsuario rol) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre("Usuario " + id);
        usuario.setEmail("user" + id + "@test.com");
        usuario.setRol(rol);
        return usuario;
    }

    private Evento evento(Long id, EstadoEvento estado, Usuario organizador) {
        Evento evento = new Evento();
        evento.setId(id);
        evento.setNombre("Concierto");
        evento.setRecinto("WiZink");
        evento.setCiudad("Madrid");
        evento.setFechaEvento(LocalDateTime.of(2027, 5, 1, 21, 0));
        evento.setCategoria(CategoriaEvento.CONCIERTO);
        evento.setEstado(estado);
        evento.setOrganizador(organizador);
        return evento;
    }

    private EventoRequestDto request(String nombre) {
        return new EventoRequestDto(
                nombre, "desc", "WiZink", "Madrid",
                LocalDateTime.of(2027, 5, 1, 21, 0), CategoriaEvento.CONCIERTO, null);
    }

    // ---------------------------------------------------------------- obtener

    @Test
    void obtener_publicadoSinAuth_ok() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        when(eventoRepository.findById(10L)).thenReturn(Optional.of(evento(10L, EstadoEvento.PUBLICADO, dueno)));
        when(tipoEntradaRepository.findByEventoId(10L)).thenReturn(List.of());

        EventoResponseDto response = eventoService.obtener(10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.estado()).isEqualTo(EstadoEvento.PUBLICADO);
    }

    @Test
    void obtener_noPublicadoComoDueno_ok() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        when(eventoRepository.findById(10L)).thenReturn(Optional.of(evento(10L, EstadoEvento.BORRADOR, dueno)));
        when(currentUserService.getCurrentUsuarioOptional()).thenReturn(Optional.of(dueno));
        when(tipoEntradaRepository.findByEventoId(10L)).thenReturn(List.of());

        EventoResponseDto response = eventoService.obtener(10L);

        assertThat(response.estado()).isEqualTo(EstadoEvento.BORRADOR);
    }

    @Test
    void obtener_noPublicadoComoAdmin_ok() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        Usuario admin = usuario(2L, RolUsuario.ADMIN);
        when(eventoRepository.findById(10L)).thenReturn(Optional.of(evento(10L, EstadoEvento.BORRADOR, dueno)));
        when(currentUserService.getCurrentUsuarioOptional()).thenReturn(Optional.of(admin));
        when(tipoEntradaRepository.findByEventoId(10L)).thenReturn(List.of());

        EventoResponseDto response = eventoService.obtener(10L);

        assertThat(response.estado()).isEqualTo(EstadoEvento.BORRADOR);
    }

    @Test
    void obtener_noPublicadoComoTercero_lanza404Enmascarado() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        Usuario tercero = usuario(3L, RolUsuario.ORGANIZADOR);
        when(eventoRepository.findById(10L)).thenReturn(Optional.of(evento(10L, EstadoEvento.BORRADOR, dueno)));
        when(currentUserService.getCurrentUsuarioOptional()).thenReturn(Optional.of(tercero));

        assertThatThrownBy(() -> eventoService.obtener(10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void obtener_noPublicadoAnonimo_lanza404Enmascarado() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        when(eventoRepository.findById(10L)).thenReturn(Optional.of(evento(10L, EstadoEvento.BORRADOR, dueno)));
        when(currentUserService.getCurrentUsuarioOptional()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventoService.obtener(10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- crear

    @Test
    void crear_asignaOrganizadorDelContexto_yNaceBorrador() {
        Usuario organizador = usuario(1L, RolUsuario.ORGANIZADOR);
        when(currentUserService.getCurrentUsuario()).thenReturn(organizador);
        when(eventoRepository.save(any(Evento.class))).thenAnswer(invocation -> {
            Evento e = invocation.getArgument(0);
            e.setId(100L);
            return e;
        });

        EventoResponseDto response = eventoService.crear(request("Nuevo"));

        ArgumentCaptor<Evento> captor = ArgumentCaptor.forClass(Evento.class);
        verify(eventoRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoEvento.BORRADOR);
        assertThat(captor.getValue().getOrganizador()).isSameAs(organizador);
        assertThat(response.estado()).isEqualTo(EstadoEvento.BORRADOR);
        assertThat(response.organizador().id()).isEqualTo(1L);
    }

    // ---------------------------------------------------------------- actualizar

    @Test
    void actualizar_comoDueno_ok() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        when(eventoRepository.findById(10L)).thenReturn(Optional.of(evento(10L, EstadoEvento.BORRADOR, dueno)));
        when(currentUserService.getCurrentUsuario()).thenReturn(dueno);
        when(tipoEntradaRepository.findByEventoId(10L)).thenReturn(List.of());

        EventoResponseDto response = eventoService.actualizar(10L, request("Editado"));

        assertThat(response.nombre()).isEqualTo("Editado");
    }

    @Test
    void actualizar_comoTercero_lanzaAccessDenied() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        Usuario tercero = usuario(3L, RolUsuario.ORGANIZADOR);
        when(eventoRepository.findById(10L)).thenReturn(Optional.of(evento(10L, EstadoEvento.BORRADOR, dueno)));
        when(currentUserService.getCurrentUsuario()).thenReturn(tercero);

        assertThatThrownBy(() -> eventoService.actualizar(10L, request("Editado")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void actualizar_eventoFinalizado_lanzaBusinessException() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        when(eventoRepository.findById(10L)).thenReturn(Optional.of(evento(10L, EstadoEvento.FINALIZADO, dueno)));
        when(currentUserService.getCurrentUsuario()).thenReturn(dueno);

        assertThatThrownBy(() -> eventoService.actualizar(10L, request("Editado")))
                .isInstanceOf(BusinessException.class);
    }

    // ---------------------------------------------------------------- cambiarEstado

    @Test
    void cambiarEstado_transicionValida_ok() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        when(eventoRepository.findById(10L)).thenReturn(Optional.of(evento(10L, EstadoEvento.BORRADOR, dueno)));
        when(currentUserService.getCurrentUsuario()).thenReturn(dueno);
        when(tipoEntradaRepository.findByEventoId(10L)).thenReturn(List.of());

        EventoResponseDto response = eventoService.cambiarEstado(10L, new CambiarEstadoDto(EstadoEvento.PUBLICADO));

        assertThat(response.estado()).isEqualTo(EstadoEvento.PUBLICADO);
    }

    @Test
    void cambiarEstado_transicionInvalida_lanzaBusinessException() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        when(eventoRepository.findById(10L)).thenReturn(Optional.of(evento(10L, EstadoEvento.PUBLICADO, dueno)));
        when(currentUserService.getCurrentUsuario()).thenReturn(dueno);

        assertThatThrownBy(() -> eventoService.cambiarEstado(10L, new CambiarEstadoDto(EstadoEvento.BORRADOR)))
                .isInstanceOf(BusinessException.class);
    }

    // ---------------------------------------------------------------- eliminar

    @Test
    void eliminar_borrador_borraTiposAntesQueElEvento() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        Evento evento = evento(10L, EstadoEvento.BORRADOR, dueno);
        TipoEntrada tipo = new TipoEntrada();
        tipo.setId(1L);
        tipo.setEvento(evento);
        when(eventoRepository.findById(10L)).thenReturn(Optional.of(evento));
        when(currentUserService.getCurrentUsuario()).thenReturn(dueno);
        when(tipoEntradaRepository.findByEventoId(10L)).thenReturn(List.of(tipo));

        eventoService.eliminar(10L);

        InOrder inOrder = inOrder(tipoEntradaRepository, eventoRepository);
        inOrder.verify(tipoEntradaRepository).deleteAll(List.of(tipo));
        inOrder.verify(eventoRepository).delete(evento);
    }

    @Test
    void eliminar_publicado_lanzaBusinessException() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        when(eventoRepository.findById(10L)).thenReturn(Optional.of(evento(10L, EstadoEvento.PUBLICADO, dueno)));
        when(currentUserService.getCurrentUsuario()).thenReturn(dueno);

        assertThatThrownBy(() -> eventoService.eliminar(10L))
                .isInstanceOf(BusinessException.class);
        verify(eventoRepository, never()).delete(any(Evento.class));
    }

    // ---------------------------------------------------------------- subirImagen

    @Test
    void subirImagen_comoDueno_guardaSecureUrl_conPublicIdDeterminista() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        Evento evento = evento(5L, EstadoEvento.BORRADOR, dueno);
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(eventoRepository.findById(5L)).thenReturn(Optional.of(evento));
        when(currentUserService.getCurrentUsuario()).thenReturn(dueno);
        when(tipoEntradaRepository.findByEventoId(5L)).thenReturn(List.of());
        when(imageStorageService.subir(any(MultipartFile.class), anyString()))
                .thenReturn("https://res.cloudinary.com/pulser/eventos/5.jpg");

        EventoResponseDto response = eventoService.subirImagen(5L, file);

        ArgumentCaptor<String> publicIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(imageStorageService).subir(eq(file), publicIdCaptor.capture());
        assertThat(publicIdCaptor.getValue()).isEqualTo("pulser/eventos/5");
        assertThat(response.imagenUrl()).isEqualTo("https://res.cloudinary.com/pulser/eventos/5.jpg");
        assertThat(evento.getImagenUrl()).isEqualTo("https://res.cloudinary.com/pulser/eventos/5.jpg");
    }

    @Test
    void subirImagen_eventoTerminal_lanzaBusinessException() {
        Usuario dueno = usuario(1L, RolUsuario.ORGANIZADOR);
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(eventoRepository.findById(5L)).thenReturn(Optional.of(evento(5L, EstadoEvento.CANCELADO, dueno)));
        when(currentUserService.getCurrentUsuario()).thenReturn(dueno);

        assertThatThrownBy(() -> eventoService.subirImagen(5L, file))
                .isInstanceOf(BusinessException.class);
        verify(imageStorageService, never()).subir(any(), anyString());
    }
}
