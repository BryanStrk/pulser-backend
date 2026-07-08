package com.bryanstrk.pulser.checkin;

import com.bryanstrk.pulser.checkin.dto.CheckInFeedDto;
import com.bryanstrk.pulser.checkin.dto.CheckInResponseDto;
import com.bryanstrk.pulser.checkin.event.CheckInRegistradoEvent;
import com.bryanstrk.pulser.entrada.Entrada;
import com.bryanstrk.pulser.entrada.EntradaRepository;
import com.bryanstrk.pulser.entrada.EstadoEntrada;
import com.bryanstrk.pulser.evento.Evento;
import com.bryanstrk.pulser.shared.security.CurrentUserService;
import com.bryanstrk.pulser.shared.security.QrPayload;
import com.bryanstrk.pulser.shared.security.QrSigningService;
import com.bryanstrk.pulser.usuario.RolUsuario;
import com.bryanstrk.pulser.usuario.Usuario;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Validacion de entradas en puerta: verifica la firma del QR, comprueba el estado de la entrada,
 * la marca USADA de forma segura ante doble-uso concurrente y deja rastro de auditoria en CheckIn.
 *
 * Reglas de registro de auditoria:
 * - Firma rota / entrada inexistente -> INVALIDA SIN fila de CheckIn: el intento no es atribuible
 *   a ningun evento (no hay entrada), y CheckIn.entrada es obligatorio (FK no admite null).
 * - Firma valida + entrada existente -> SIEMPRE una fila de CheckIn (VALIDO, YA_USADA o INVALIDA),
 *   incluso en rechazos. Ese registro es el que alimentara el feed WebSocket del bloque siguiente.
 */
@Service
public class CheckInService {

    private final EntradaRepository entradaRepository;
    private final CheckInRepository checkInRepository;
    private final QrSigningService qrSigningService;
    private final CurrentUserService currentUserService;
    private final ApplicationEventPublisher eventPublisher;

    public CheckInService(EntradaRepository entradaRepository,
                          CheckInRepository checkInRepository,
                          QrSigningService qrSigningService,
                          CurrentUserService currentUserService,
                          ApplicationEventPublisher eventPublisher) {
        this.entradaRepository = entradaRepository;
        this.checkInRepository = checkInRepository;
        this.qrSigningService = qrSigningService;
        this.currentUserService = currentUserService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CheckInResponseDto validar(String token, String puerta) {
        // (a) verificar firma; si no cuadra NO confiamos en el payload ni cargamos por un id no fiable.
        Optional<QrPayload> payloadOpt = qrSigningService.verificar(token);
        if (payloadOpt.isEmpty()) {
            return CheckInResponseDto.sinEntrada("Codigo QR invalido o manipulado");
        }
        QrPayload payload = payloadOpt.get();

        // (b) cargar entrada (con evento + organizador + tipo ya inicializados).
        Optional<Entrada> entradaOpt = entradaRepository.findConEventoYOrganizador(payload.entradaId());
        if (entradaOpt.isEmpty()) {
            return CheckInResponseDto.sinEntrada("Entrada no encontrada");
        }
        Entrada entrada = entradaOpt.get();

        // (c) AUTORIZACION antes de cualquier mutacion: solo el organizador del evento o un ADMIN.
        //     Un acceso no autorizado NO es un intento legitimo de puerta -> 403 y no se audita.
        Usuario actual = currentUserService.getCurrentUsuario();
        Evento evento = entrada.getEvento();
        boolean esAdmin = actual.getRol() == RolUsuario.ADMIN;
        boolean esOrganizador = evento.getOrganizador().getId().equals(actual.getId());
        if (!esAdmin && !esOrganizador) {
            throw new AccessDeniedException("No puedes validar entradas de este evento");
        }

        // Snapshot de todo lo que necesitamos ANTES del UPDATE: marcarUsada tiene
        // clearAutomatically=true y detacha las entidades cargadas.
        UUID entradaId = entrada.getId();
        String nombreEvento = evento.getNombre();
        String tipoEntradaNombre = entrada.getTipoEntrada().getNombre();
        EstadoEntrada estado = entrada.getEstado();

        // (d/e/f) resolucion segun estado.
        ResultadoCheckIn resultado;
        String mensaje;
        switch (estado) {
            case VALIDA -> {
                // (d) marcado atomico: 1 = ganamos la carrera; 0 = otra validacion concurrente
                //     la marco USADA primero -> lo tratamos como YA_USADA (nunca dos VALIDO).
                int filas = entradaRepository.marcarUsada(entradaId, LocalDateTime.now());
                if (filas == 1) {
                    resultado = ResultadoCheckIn.VALIDO;
                    mensaje = "Entrada valida. Acceso permitido";
                } else {
                    resultado = ResultadoCheckIn.YA_USADA;
                    mensaje = "Entrada ya utilizada";
                }
            }
            case USADA -> {
                // (e)
                resultado = ResultadoCheckIn.YA_USADA;
                mensaje = "Entrada ya utilizada";
            }
            case PENDIENTE_PAGO -> {
                // (f)
                resultado = ResultadoCheckIn.INVALIDA;
                mensaje = "Entrada pendiente de pago, acceso denegado";
            }
            case CANCELADA -> {
                // (f)
                resultado = ResultadoCheckIn.INVALIDA;
                mensaje = "Entrada cancelada, acceso denegado";
            }
            default -> {
                resultado = ResultadoCheckIn.INVALIDA;
                mensaje = "Entrada no valida";
            }
        }

        // Auditoria: una fila por cada intento legitimo de puerta (d, e, f).
        // La entrada esta detachada tras marcarUsada; getReferenceById da un proxy valido para la FK.
        CheckIn checkIn = new CheckIn();
        checkIn.setEntrada(entradaRepository.getReferenceById(entradaId));
        checkIn.setResultado(resultado);
        checkIn.setPuerta(puerta);
        checkInRepository.save(checkIn);

        // === EMISION WEBSOCKET (post-commit) ===
        // Publicamos un evento de dominio con un snapshot inmutable. Un listener AFTER_COMMIT lo
        // empujara al feed del evento SOLO si esta transaccion commitea (nunca un check-in fantasma
        // en el dashboard si hay rollback). CheckInService no conoce el WS: solo publica el evento.
        CheckInFeedDto feed = new CheckInFeedDto(
                resultado, entradaId, nombreEvento, tipoEntradaNombre, puerta, LocalDateTime.now());
        eventPublisher.publishEvent(new CheckInRegistradoEvent(evento.getId(), feed));

        return new CheckInResponseDto(resultado, entradaId, mensaje, nombreEvento, tipoEntradaNombre);
    }
}
