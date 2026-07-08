package com.bryanstrk.pulser.entrada;

import com.bryanstrk.pulser.entrada.dto.CrearEntradaRequestDto;
import com.bryanstrk.pulser.entrada.dto.EntradaResponseDto;
import com.bryanstrk.pulser.evento.Evento;
import com.bryanstrk.pulser.evento.EstadoEvento;
import com.bryanstrk.pulser.evento.EventoRepository;
import com.bryanstrk.pulser.evento.TipoEntrada;
import com.bryanstrk.pulser.evento.TipoEntradaRepository;
import com.bryanstrk.pulser.shared.exception.BusinessException;
import com.bryanstrk.pulser.shared.exception.ResourceNotFoundException;
import com.bryanstrk.pulser.shared.security.CurrentUserService;
import com.bryanstrk.pulser.shared.security.QrSigningService;
import com.bryanstrk.pulser.usuario.RolUsuario;
import com.bryanstrk.pulser.usuario.Usuario;
import com.bryanstrk.pulser.usuario.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class EntradaService {

    private final EntradaRepository entradaRepository;
    private final EventoRepository eventoRepository;
    private final TipoEntradaRepository tipoEntradaRepository;
    private final UsuarioRepository usuarioRepository;
    private final EntradaMapper entradaMapper;
    private final CurrentUserService currentUserService;
    private final QrSigningService qrSigningService;
    private final QrImageService qrImageService;

    public EntradaService(EntradaRepository entradaRepository,
                          EventoRepository eventoRepository,
                          TipoEntradaRepository tipoEntradaRepository,
                          UsuarioRepository usuarioRepository,
                          EntradaMapper entradaMapper,
                          CurrentUserService currentUserService,
                          QrSigningService qrSigningService,
                          QrImageService qrImageService) {
        this.entradaRepository = entradaRepository;
        this.eventoRepository = eventoRepository;
        this.tipoEntradaRepository = tipoEntradaRepository;
        this.usuarioRepository = usuarioRepository;
        this.entradaMapper = entradaMapper;
        this.currentUserService = currentUserService;
        this.qrSigningService = qrSigningService;
        this.qrImageService = qrImageService;
    }

    /**
     * Compra de una entrada con pago SIMULADO.
     * Pago simulado: la Entrada nace directamente VALIDA. PENDIENTE_PAGO queda reservado para
     * cuando exista Stripe (entonces naceria PENDIENTE_PAGO y pasaria a VALIDA al confirmar el pago).
     */
    @Transactional
    public EntradaResponseDto comprar(Long eventoId, CrearEntradaRequestDto request) {
        Usuario usuario = currentUserService.getCurrentUsuario();

        // (1) cargar evento y tipoEntrada
        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado"));
        TipoEntrada tipoEntrada = tipoEntradaRepository.findById(request.tipoEntradaId())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de entrada no encontrado"));

        // (2) pertenencia (404 enmascarado) + evento PUBLICADO + fecha futura
        if (!tipoEntrada.getEvento().getId().equals(eventoId)) {
            throw new ResourceNotFoundException("Tipo de entrada no encontrado");
        }
        if (evento.getEstado() != EstadoEvento.PUBLICADO) {
            throw new BusinessException("El evento no esta disponible para la compra");
        }
        if (!evento.getFechaEvento().isAfter(LocalDateTime.now())) {
            throw new BusinessException("El evento ya ha comenzado o finalizado");
        }

        // (3) snapshot de precio ANTES del UPDATE (clearAutomatically detacha las entidades)
        BigDecimal precioSnapshot = tipoEntrada.getPrecio();

        // (4) reserva atomica de plaza
        int reservadas = entradaRepository.reservarPlaza(tipoEntrada.getId());
        if (reservadas == 0) {
            throw new BusinessException("Entradas agotadas");
        }

        // (5) alta VALIDA con precio snapshotado y referencias por id
        Entrada entrada = new Entrada();
        entrada.setEvento(eventoRepository.getReferenceById(eventoId));
        entrada.setTipoEntrada(tipoEntradaRepository.getReferenceById(request.tipoEntradaId()));
        entrada.setUsuario(usuarioRepository.getReferenceById(usuario.getId()));
        entrada.setPrecio(precioSnapshot);
        entrada.setEstado(EstadoEntrada.VALIDA);

        // persist asigna el UUID (generador pre-insert) sin flush; con el id ya podemos firmar el QR.
        Entrada guardada = entradaRepository.save(entrada);
        guardada.setCodigoQr(qrSigningService.firmar(guardada.getId(), eventoId, Instant.now()));
        // flush: INSERT unico con el codigoQr incluido y fechaCompra (@CreationTimestamp) poblada.
        entradaRepository.flush();

        return entradaMapper.toResponse(guardada, evento, tipoEntrada);
    }

    @Transactional(readOnly = true)
    public Page<EntradaResponseDto> misEntradas(Pageable pageable) {
        Usuario usuario = currentUserService.getCurrentUsuario();
        return entradaRepository.findByUsuarioId(usuario.getId(), pageable)
                .map(entradaMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public EntradaResponseDto obtener(UUID id) {
        return entradaMapper.toResponse(cargarEntradaAccesible(id));
    }

    @Transactional(readOnly = true)
    public byte[] generarQrPng(UUID id) {
        Entrada entrada = cargarEntradaAccesible(id);
        if (entrada.getCodigoQr() == null) {
            // Entradas anteriores al bloque QR: no tienen token que renderizar.
            throw new ResourceNotFoundException("Esta entrada no tiene QR");
        }
        return qrImageService.renderPng(entrada.getCodigoQr());
    }

    /**
     * Carga la entrada aplicando el control de acceso: solo comprador o ADMIN;
     * en otro caso 404 enmascarado (no revelar la existencia de entradas ajenas).
     */
    private Entrada cargarEntradaAccesible(UUID id) {
        Entrada entrada = entradaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entrada no encontrada"));

        Usuario actual = currentUserService.getCurrentUsuario();
        boolean esAdmin = actual.getRol() == RolUsuario.ADMIN;
        boolean esComprador = entrada.getUsuario().getId().equals(actual.getId());
        if (!esAdmin && !esComprador) {
            throw new ResourceNotFoundException("Entrada no encontrada");
        }
        return entrada;
    }
}
