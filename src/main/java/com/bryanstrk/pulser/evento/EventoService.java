package com.bryanstrk.pulser.evento;

import com.bryanstrk.pulser.evento.dto.CambiarEstadoDto;
import com.bryanstrk.pulser.evento.dto.EventoRequestDto;
import com.bryanstrk.pulser.evento.dto.EventoResponseDto;
import com.bryanstrk.pulser.evento.dto.EventoResumenDto;
import com.bryanstrk.pulser.shared.exception.BusinessException;
import com.bryanstrk.pulser.shared.exception.ResourceNotFoundException;
import com.bryanstrk.pulser.shared.security.CurrentUserService;
import com.bryanstrk.pulser.usuario.RolUsuario;
import com.bryanstrk.pulser.usuario.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class EventoService {

    private final EventoRepository eventoRepository;
    private final TipoEntradaRepository tipoEntradaRepository;
    private final EventoMapper eventoMapper;
    private final CurrentUserService currentUserService;
    private final ImageStorageService imageStorageService;

    public EventoService(EventoRepository eventoRepository,
                         TipoEntradaRepository tipoEntradaRepository,
                         EventoMapper eventoMapper,
                         CurrentUserService currentUserService,
                         ImageStorageService imageStorageService) {
        this.eventoRepository = eventoRepository;
        this.tipoEntradaRepository = tipoEntradaRepository;
        this.eventoMapper = eventoMapper;
        this.currentUserService = currentUserService;
        this.imageStorageService = imageStorageService;
    }

    // ---------------------------------------------------------------- Lecturas

    /**
     * Listado publico: solo eventos PUBLICADO, con filtros opcionales y paginacion.
     */
    @Transactional(readOnly = true)
    public Page<EventoResumenDto> listarPublicos(CategoriaEvento categoria,
                                                 String ciudad,
                                                 String texto,
                                                 Pageable pageable) {
        Specification<Evento> spec = EventoSpecifications.conEstado(EstadoEvento.PUBLICADO)
                .and(EventoSpecifications.conCategoria(categoria))
                .and(EventoSpecifications.conCiudad(ciudad))
                .and(EventoSpecifications.nombreContiene(texto));

        return eventoRepository.findAll(spec, pageable).map(eventoMapper::toResumen);
    }

    /**
     * Eventos del usuario actual en cualquier estado (mismos filtros que el listado publico).
     * Para ADMIN devuelve los suyos, no todos los del sistema.
     */
    @Transactional(readOnly = true)
    public Page<EventoResumenDto> listarMisEventos(CategoriaEvento categoria,
                                                   String ciudad,
                                                   String texto,
                                                   Pageable pageable) {
        Usuario actual = currentUserService.getCurrentUsuario();

        Specification<Evento> spec = EventoSpecifications.conOrganizador(actual.getId())
                .and(EventoSpecifications.conCategoria(categoria))
                .and(EventoSpecifications.conCiudad(ciudad))
                .and(EventoSpecifications.nombreContiene(texto));

        return eventoRepository.findAll(spec, pageable).map(eventoMapper::toResumen);
    }

    /**
     * Detalle. Un evento no PUBLICADO solo es visible para su dueño o un ADMIN;
     * en caso contrario se responde 404 (no se revela su existencia).
     */
    @Transactional(readOnly = true)
    public EventoResponseDto obtener(Long id) {
        Evento evento = buscarEvento(id);

        if (evento.getEstado() != EstadoEvento.PUBLICADO && !puedeVerNoPublicado(evento)) {
            throw new ResourceNotFoundException("Evento no encontrado");
        }

        List<TipoEntrada> tipos = tipoEntradaRepository.findByEventoId(id);
        return eventoMapper.toResponse(evento, tipos);
    }

    // ---------------------------------------------------------------- Escrituras

    @Transactional
    public EventoResponseDto crear(EventoRequestDto request) {
        Usuario organizador = currentUserService.getCurrentUsuario();
        Evento evento = eventoMapper.toEntity(request, organizador);
        Evento guardado = eventoRepository.save(evento);
        return eventoMapper.toResponse(guardado, List.of());
    }

    @Transactional
    public EventoResponseDto actualizar(Long id, EventoRequestDto request) {
        Evento evento = buscarEvento(id);
        verificarGestion(evento);
        verificarNoTerminal(evento);
        eventoMapper.applyUpdate(evento, request);
        List<TipoEntrada> tipos = tipoEntradaRepository.findByEventoId(id);
        return eventoMapper.toResponse(evento, tipos);
    }

    @Transactional
    public EventoResponseDto cambiarEstado(Long id, CambiarEstadoDto request) {
        Evento evento = buscarEvento(id);
        verificarGestion(evento);

        EstadoEvento nuevo = request.nuevoEstado();
        if (!EstadoEventoTransicion.esValida(evento.getEstado(), nuevo)) {
            throw new BusinessException(
                    "Transicion de estado no permitida: " + evento.getEstado() + " -> " + nuevo);
        }
        evento.setEstado(nuevo);

        List<TipoEntrada> tipos = tipoEntradaRepository.findByEventoId(id);
        return eventoMapper.toResponse(evento, tipos);
    }

    @Transactional
    public EventoResponseDto subirImagen(Long id, MultipartFile imagen) {
        Evento evento = buscarEvento(id);
        verificarGestion(evento);
        verificarNoTerminal(evento);

        // public_id determinista por evento -> overwrite en el mismo asset, sin acumular basura.
        String publicId = "pulser/eventos/" + evento.getId();
        // TODO: con public_id fijo + overwrite=true el asset se reemplaza en su sitio; si en el
        //       futuro cambia la estrategia de nombres, habria que borrar el asset anterior en Cloudinary.
        String url = imageStorageService.subir(imagen, publicId);
        evento.setImagenUrl(url);

        List<TipoEntrada> tipos = tipoEntradaRepository.findByEventoId(id);
        return eventoMapper.toResponse(evento, tipos);
    }

    @Transactional
    public void eliminar(Long id) {
        Evento evento = buscarEvento(id);
        verificarGestion(evento);

        if (evento.getEstado() != EstadoEvento.BORRADOR) {
            throw new BusinessException(
                    "Solo se puede eliminar un evento en BORRADOR; usa la cancelacion en su lugar");
        }

        // No hay cascade en la entidad: eliminamos primero sus tipos de entrada.
        List<TipoEntrada> tipos = tipoEntradaRepository.findByEventoId(id);
        tipoEntradaRepository.deleteAll(tipos);
        eventoRepository.delete(evento);
    }

    // ---------------------------------------------------------------- Helpers

    private Evento buscarEvento(Long id) {
        return eventoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado"));
    }

    private boolean puedeVerNoPublicado(Evento evento) {
        return currentUserService.getCurrentUsuarioOptional()
                .map(usuario -> esAdmin(usuario) || esDueno(evento, usuario))
                .orElse(false);
    }

    private void verificarGestion(Evento evento) {
        Usuario actual = currentUserService.getCurrentUsuario();
        if (!esAdmin(actual) && !esDueno(evento, actual)) {
            throw new AccessDeniedException("No puedes gestionar este evento");
        }
    }

    private void verificarNoTerminal(Evento evento) {
        // Mismo criterio que TipoEntradaService.verificarEventoGestionable: no editar en estado terminal.
        if (evento.getEstado() == EstadoEvento.FINALIZADO || evento.getEstado() == EstadoEvento.CANCELADO) {
            throw new BusinessException("No se puede editar un evento " + evento.getEstado());
        }
    }

    private boolean esAdmin(Usuario usuario) {
        return usuario.getRol() == RolUsuario.ADMIN;
    }

    private boolean esDueno(Evento evento, Usuario usuario) {
        return evento.getOrganizador().getId().equals(usuario.getId());
    }
}
