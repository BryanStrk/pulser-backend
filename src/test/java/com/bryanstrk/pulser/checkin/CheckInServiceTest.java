package com.bryanstrk.pulser.checkin;

import com.bryanstrk.pulser.checkin.dto.CheckInResponseDto;
import com.bryanstrk.pulser.entrada.Entrada;
import com.bryanstrk.pulser.entrada.EntradaRepository;
import com.bryanstrk.pulser.entrada.EstadoEntrada;
import com.bryanstrk.pulser.evento.Evento;
import com.bryanstrk.pulser.evento.TipoEntrada;
import com.bryanstrk.pulser.shared.security.CurrentUserService;
import com.bryanstrk.pulser.shared.security.QrPayload;
import com.bryanstrk.pulser.shared.security.QrSigningService;
import com.bryanstrk.pulser.usuario.RolUsuario;
import com.bryanstrk.pulser.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {

    private static final Long EVENTO_ID = 10L;
    private static final Long ORGANIZADOR_ID = 1L;
    private static final String TOKEN = "payload.firma";
    private static final String PUERTA = "Puerta A";

    @Mock
    private EntradaRepository entradaRepository;
    @Mock
    private CheckInRepository checkInRepository;
    @Mock
    private QrSigningService qrSigningService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CheckInService checkInService;

    private CheckInService service() {
        return new CheckInService(entradaRepository, checkInRepository, qrSigningService,
                currentUserService, eventPublisher);
    }

    // ---------------------------------------------------------------- helpers

    private Usuario usuario(Long id, RolUsuario rol) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre("Usuario " + id);
        usuario.setRol(rol);
        return usuario;
    }

    private Evento evento(Usuario organizador) {
        Evento evento = new Evento();
        evento.setId(EVENTO_ID);
        evento.setNombre("Concierto");
        evento.setOrganizador(organizador);
        return evento;
    }

    private TipoEntrada tipoEntrada() {
        TipoEntrada tipo = new TipoEntrada();
        tipo.setId(5L);
        tipo.setNombre("General");
        return tipo;
    }

    private Entrada entrada(UUID id, Evento evento, EstadoEntrada estado) {
        Entrada entrada = new Entrada();
        entrada.setId(id);
        entrada.setEvento(evento);
        entrada.setTipoEntrada(tipoEntrada());
        entrada.setEstado(estado);
        return entrada;
    }

    private QrPayload payload(UUID entradaId) {
        return new QrPayload(entradaId, EVENTO_ID, Instant.now());
    }

    private CheckIn capturarCheckIn() {
        ArgumentCaptor<CheckIn> captor = ArgumentCaptor.forClass(CheckIn.class);
        verify(checkInRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- casos

    @Test
    void validar_entradaValida_devuelveValidoYLaMarcaUsada() {
        checkInService = service();
        Usuario organizador = usuario(ORGANIZADOR_ID, RolUsuario.ORGANIZADOR);
        UUID id = UUID.randomUUID();
        Entrada entrada = entrada(id, evento(organizador), EstadoEntrada.VALIDA);

        when(qrSigningService.verificar(TOKEN)).thenReturn(Optional.of(payload(id)));
        when(entradaRepository.findConEventoYOrganizador(id)).thenReturn(Optional.of(entrada));
        when(currentUserService.getCurrentUsuario()).thenReturn(organizador);
        when(entradaRepository.marcarUsada(eq(id), any(LocalDateTime.class))).thenReturn(1);
        when(entradaRepository.getReferenceById(id)).thenReturn(entrada);

        CheckInResponseDto response = checkInService.validar(TOKEN, PUERTA);

        // marcado atomico invocado -> la entrada queda USADA
        verify(entradaRepository).marcarUsada(eq(id), any(LocalDateTime.class));
        assertThat(response.resultado()).isEqualTo(ResultadoCheckIn.VALIDO);
        assertThat(response.entradaId()).isEqualTo(id);
        assertThat(response.nombreEvento()).isEqualTo("Concierto");
        assertThat(response.tipoEntrada()).isEqualTo("General");

        CheckIn registrado = capturarCheckIn();
        assertThat(registrado.getResultado()).isEqualTo(ResultadoCheckIn.VALIDO);
        assertThat(registrado.getPuerta()).isEqualTo(PUERTA);
    }

    @Test
    void validar_comoAdmin_aunqueNoSeaOrganizador_ok() {
        checkInService = service();
        Usuario admin = usuario(99L, RolUsuario.ADMIN);
        Usuario otroOrganizador = usuario(ORGANIZADOR_ID, RolUsuario.ORGANIZADOR);
        UUID id = UUID.randomUUID();
        Entrada entrada = entrada(id, evento(otroOrganizador), EstadoEntrada.VALIDA);

        when(qrSigningService.verificar(TOKEN)).thenReturn(Optional.of(payload(id)));
        when(entradaRepository.findConEventoYOrganizador(id)).thenReturn(Optional.of(entrada));
        when(currentUserService.getCurrentUsuario()).thenReturn(admin);
        when(entradaRepository.marcarUsada(eq(id), any(LocalDateTime.class))).thenReturn(1);
        when(entradaRepository.getReferenceById(id)).thenReturn(entrada);

        assertThat(checkInService.validar(TOKEN, PUERTA).resultado()).isEqualTo(ResultadoCheckIn.VALIDO);
    }

    @Test
    void validar_entradaYaUsada_devuelveYaUsadaSinMarcar() {
        checkInService = service();
        Usuario organizador = usuario(ORGANIZADOR_ID, RolUsuario.ORGANIZADOR);
        UUID id = UUID.randomUUID();
        Entrada entrada = entrada(id, evento(organizador), EstadoEntrada.USADA);

        when(qrSigningService.verificar(TOKEN)).thenReturn(Optional.of(payload(id)));
        when(entradaRepository.findConEventoYOrganizador(id)).thenReturn(Optional.of(entrada));
        when(currentUserService.getCurrentUsuario()).thenReturn(organizador);
        when(entradaRepository.getReferenceById(id)).thenReturn(entrada);

        CheckInResponseDto response = checkInService.validar(TOKEN, PUERTA);

        verify(entradaRepository, never()).marcarUsada(any(), any());
        assertThat(response.resultado()).isEqualTo(ResultadoCheckIn.YA_USADA);
        assertThat(capturarCheckIn().getResultado()).isEqualTo(ResultadoCheckIn.YA_USADA);
    }

    @Test
    void validar_dobleUsoConcurrente_updateAfecta0_devuelveYaUsada() {
        checkInService = service();
        Usuario organizador = usuario(ORGANIZADOR_ID, RolUsuario.ORGANIZADOR);
        UUID id = UUID.randomUUID();
        Entrada entrada = entrada(id, evento(organizador), EstadoEntrada.VALIDA);

        when(qrSigningService.verificar(TOKEN)).thenReturn(Optional.of(payload(id)));
        when(entradaRepository.findConEventoYOrganizador(id)).thenReturn(Optional.of(entrada));
        when(currentUserService.getCurrentUsuario()).thenReturn(organizador);
        // otra validacion concurrente ya la marco USADA: el UPDATE condicional afecta 0 filas
        when(entradaRepository.marcarUsada(eq(id), any(LocalDateTime.class))).thenReturn(0);
        when(entradaRepository.getReferenceById(id)).thenReturn(entrada);

        CheckInResponseDto response = checkInService.validar(TOKEN, PUERTA);

        assertThat(response.resultado()).isEqualTo(ResultadoCheckIn.YA_USADA);
        assertThat(capturarCheckIn().getResultado()).isEqualTo(ResultadoCheckIn.YA_USADA);
    }

    @Test
    void validar_firmaRota_devuelveInvalidaSinCargarEntrada() {
        checkInService = service();
        when(qrSigningService.verificar(TOKEN)).thenReturn(Optional.empty());

        CheckInResponseDto response = checkInService.validar(TOKEN, PUERTA);

        assertThat(response.resultado()).isEqualTo(ResultadoCheckIn.INVALIDA);
        assertThat(response.entradaId()).isNull();
        // no se carga entrada por un id no fiable, ni se audita, ni se resuelve el usuario
        verify(entradaRepository, never()).findConEventoYOrganizador(any());
        verifyNoInteractions(checkInRepository, currentUserService);
    }

    @Test
    void validar_entradaInexistente_devuelveInvalidaSinAuditar() {
        checkInService = service();
        UUID id = UUID.randomUUID();
        when(qrSigningService.verificar(TOKEN)).thenReturn(Optional.of(payload(id)));
        when(entradaRepository.findConEventoYOrganizador(id)).thenReturn(Optional.empty());

        CheckInResponseDto response = checkInService.validar(TOKEN, PUERTA);

        assertThat(response.resultado()).isEqualTo(ResultadoCheckIn.INVALIDA);
        assertThat(response.entradaId()).isNull();
        verifyNoInteractions(checkInRepository);
    }

    @Test
    void validar_entradaPendientePago_devuelveInvalidaYAudita() {
        checkInService = service();
        Usuario organizador = usuario(ORGANIZADOR_ID, RolUsuario.ORGANIZADOR);
        UUID id = UUID.randomUUID();
        Entrada entrada = entrada(id, evento(organizador), EstadoEntrada.PENDIENTE_PAGO);

        when(qrSigningService.verificar(TOKEN)).thenReturn(Optional.of(payload(id)));
        when(entradaRepository.findConEventoYOrganizador(id)).thenReturn(Optional.of(entrada));
        when(currentUserService.getCurrentUsuario()).thenReturn(organizador);
        when(entradaRepository.getReferenceById(id)).thenReturn(entrada);

        CheckInResponseDto response = checkInService.validar(TOKEN, PUERTA);

        verify(entradaRepository, never()).marcarUsada(any(), any());
        assertThat(response.resultado()).isEqualTo(ResultadoCheckIn.INVALIDA);
        assertThat(response.entradaId()).isEqualTo(id);
        assertThat(capturarCheckIn().getResultado()).isEqualTo(ResultadoCheckIn.INVALIDA);
    }

    @Test
    void validar_entradaCancelada_devuelveInvalidaYAudita() {
        checkInService = service();
        Usuario organizador = usuario(ORGANIZADOR_ID, RolUsuario.ORGANIZADOR);
        UUID id = UUID.randomUUID();
        Entrada entrada = entrada(id, evento(organizador), EstadoEntrada.CANCELADA);

        when(qrSigningService.verificar(TOKEN)).thenReturn(Optional.of(payload(id)));
        when(entradaRepository.findConEventoYOrganizador(id)).thenReturn(Optional.of(entrada));
        when(currentUserService.getCurrentUsuario()).thenReturn(organizador);
        when(entradaRepository.getReferenceById(id)).thenReturn(entrada);

        CheckInResponseDto response = checkInService.validar(TOKEN, PUERTA);

        verify(entradaRepository, never()).marcarUsada(any(), any());
        assertThat(response.resultado()).isEqualTo(ResultadoCheckIn.INVALIDA);
        assertThat(capturarCheckIn().getResultado()).isEqualTo(ResultadoCheckIn.INVALIDA);
    }

    @Test
    void validar_organizadorAjeno_lanzaAccessDeniedSinAuditarNiMarcar() {
        checkInService = service();
        Usuario dueño = usuario(ORGANIZADOR_ID, RolUsuario.ORGANIZADOR);
        Usuario ajeno = usuario(2L, RolUsuario.ORGANIZADOR);
        UUID id = UUID.randomUUID();
        Entrada entrada = entrada(id, evento(dueño), EstadoEntrada.VALIDA);

        when(qrSigningService.verificar(TOKEN)).thenReturn(Optional.of(payload(id)));
        when(entradaRepository.findConEventoYOrganizador(id)).thenReturn(Optional.of(entrada));
        when(currentUserService.getCurrentUsuario()).thenReturn(ajeno);

        assertThatThrownBy(() -> checkInService.validar(TOKEN, PUERTA))
                .isInstanceOf(AccessDeniedException.class);

        verify(entradaRepository, never()).marcarUsada(any(), any());
        verifyNoInteractions(checkInRepository);
    }
}
