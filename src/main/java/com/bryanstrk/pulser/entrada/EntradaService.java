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
import com.bryanstrk.pulser.usuario.RolUsuario;
import com.bryanstrk.pulser.usuario.Usuario;
import com.bryanstrk.pulser.usuario.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    public EntradaService(EntradaRepository entradaRepository,
                          EventoRepository eventoRepository,
                          TipoEntradaRepository tipoEntradaRepository,
                          UsuarioRepository usuarioRepository,
                          EntradaMapper entradaMapper,
                          CurrentUserService currentUserService) {
        this.entradaRepository = entradaRepository;
        this.eventoRepository = eventoRepository;
        this.tipoEntradaRepository = tipoEntradaRepository;
        this.usuarioRepository = usuarioRepository;
        this.entradaMapper = entradaMapper;
        this.currentUserService = currentUserService;
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
        // codigoQr queda null: se rellenara en el bloque de QR.

        // saveAndFlush para materializar la INSERT y poblar fechaCompra (@CreationTimestamp) en la respuesta.
        Entrada guardada = entradaRepository.saveAndFlush(entrada);
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
        Entrada entrada = entradaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entrada no encontrada"));

        Usuario actual = currentUserService.getCurrentUsuario();
        boolean esAdmin = actual.getRol() == RolUsuario.ADMIN;
        boolean esComprador = entrada.getUsuario().getId().equals(actual.getId());
        if (!esAdmin && !esComprador) {
            // 404 enmascarado: no revelar la existencia de entradas ajenas.
            throw new ResourceNotFoundException("Entrada no encontrada");
        }

        return entradaMapper.toResponse(entrada);
    }
}
