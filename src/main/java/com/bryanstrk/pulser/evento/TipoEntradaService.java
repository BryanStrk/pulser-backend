package com.bryanstrk.pulser.evento;

import com.bryanstrk.pulser.evento.dto.TipoEntradaRequestDto;
import com.bryanstrk.pulser.evento.dto.TipoEntradaResponseDto;
import com.bryanstrk.pulser.shared.exception.BusinessException;
import com.bryanstrk.pulser.shared.exception.ResourceNotFoundException;
import com.bryanstrk.pulser.shared.security.CurrentUserService;
import com.bryanstrk.pulser.usuario.RolUsuario;
import com.bryanstrk.pulser.usuario.Usuario;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TipoEntradaService {

    private final TipoEntradaRepository tipoEntradaRepository;
    private final EventoRepository eventoRepository;
    private final TipoEntradaMapper tipoEntradaMapper;
    private final CurrentUserService currentUserService;

    public TipoEntradaService(TipoEntradaRepository tipoEntradaRepository,
                              EventoRepository eventoRepository,
                              TipoEntradaMapper tipoEntradaMapper,
                              CurrentUserService currentUserService) {
        this.tipoEntradaRepository = tipoEntradaRepository;
        this.eventoRepository = eventoRepository;
        this.tipoEntradaMapper = tipoEntradaMapper;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public TipoEntradaResponseDto crear(Long eventoId, TipoEntradaRequestDto request) {
        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado"));
        verificarGestion(evento);
        verificarEventoGestionable(evento);

        TipoEntrada tipoEntrada = tipoEntradaMapper.toEntity(request, evento);
        TipoEntrada guardado = tipoEntradaRepository.save(tipoEntrada);
        return tipoEntradaMapper.toResponse(guardado);
    }

    @Transactional
    public TipoEntradaResponseDto actualizar(Long tipoEntradaId, TipoEntradaRequestDto request) {
        TipoEntrada tipoEntrada = buscarTipoEntrada(tipoEntradaId);
        Evento evento = tipoEntrada.getEvento();
        verificarGestion(evento);
        verificarEventoGestionable(evento);

        // El nuevo aforo no puede quedar por debajo de las entradas ya vendidas.
        if (request.aforo() < tipoEntrada.getVendidas()) {
            throw new BusinessException(
                    "El aforo no puede ser menor que las entradas ya vendidas (" + tipoEntrada.getVendidas() + ")");
        }

        tipoEntradaMapper.applyUpdate(tipoEntrada, request);
        return tipoEntradaMapper.toResponse(tipoEntrada);
    }

    @Transactional
    public void eliminar(Long tipoEntradaId) {
        TipoEntrada tipoEntrada = buscarTipoEntrada(tipoEntradaId);
        Evento evento = tipoEntrada.getEvento();
        verificarGestion(evento);
        verificarEventoGestionable(evento);

        if (tipoEntrada.getVendidas() > 0) {
            throw new BusinessException("No se puede eliminar un tipo de entrada con ventas");
        }

        tipoEntradaRepository.delete(tipoEntrada);
    }

    // ---------------------------------------------------------------- Helpers

    private TipoEntrada buscarTipoEntrada(Long id) {
        return tipoEntradaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de entrada no encontrado"));
    }

    private void verificarGestion(Evento evento) {
        Usuario actual = currentUserService.getCurrentUsuario();
        boolean esAdmin = actual.getRol() == RolUsuario.ADMIN;
        boolean esDueno = evento.getOrganizador().getId().equals(actual.getId());
        if (!esAdmin && !esDueno) {
            throw new AccessDeniedException("No puedes gestionar los tipos de entrada de este evento");
        }
    }

    private void verificarEventoGestionable(Evento evento) {
        if (evento.getEstado() == EstadoEvento.FINALIZADO || evento.getEstado() == EstadoEvento.CANCELADO) {
            throw new BusinessException(
                    "No se pueden gestionar tipos de entrada de un evento " + evento.getEstado());
        }
    }
}
