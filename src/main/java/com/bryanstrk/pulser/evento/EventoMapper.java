package com.bryanstrk.pulser.evento;

import com.bryanstrk.pulser.evento.dto.EventoRequestDto;
import com.bryanstrk.pulser.evento.dto.EventoResponseDto;
import com.bryanstrk.pulser.evento.dto.EventoResumenDto;
import com.bryanstrk.pulser.evento.dto.OrganizadorDto;
import com.bryanstrk.pulser.evento.dto.TipoEntradaResponseDto;
import com.bryanstrk.pulser.usuario.Usuario;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EventoMapper {

    private final TipoEntradaMapper tipoEntradaMapper;

    public EventoMapper(TipoEntradaMapper tipoEntradaMapper) {
        this.tipoEntradaMapper = tipoEntradaMapper;
    }

    public Evento toEntity(EventoRequestDto dto, Usuario organizador) {
        Evento evento = new Evento();
        evento.setNombre(dto.nombre());
        evento.setDescripcion(dto.descripcion());
        evento.setRecinto(dto.recinto());
        evento.setCiudad(dto.ciudad());
        evento.setFechaEvento(dto.fechaEvento());
        evento.setCategoria(dto.categoria());
        evento.setImagenUrl(dto.imagenUrl());
        evento.setEstado(EstadoEvento.BORRADOR);
        evento.setOrganizador(organizador);
        return evento;
    }

    public void applyUpdate(Evento evento, EventoRequestDto dto) {
        evento.setNombre(dto.nombre());
        evento.setDescripcion(dto.descripcion());
        evento.setRecinto(dto.recinto());
        evento.setCiudad(dto.ciudad());
        evento.setFechaEvento(dto.fechaEvento());
        evento.setCategoria(dto.categoria());
        evento.setImagenUrl(dto.imagenUrl());
        // estado y organizador no se modifican en la edicion de datos.
    }

    /**
     * Item de listado con la ocupacion agregada ya resuelta por el llamante (0/0 si el evento no
     * tiene tipos de entrada).
     */
    public EventoResumenDto toResumen(Evento evento, long aforoTotal, long entradasVendidas) {
        return new EventoResumenDto(
                evento.getId(),
                evento.getNombre(),
                evento.getCiudad(),
                evento.getFechaEvento(),
                evento.getCategoria(),
                evento.getEstado(),
                evento.getImagenUrl(),
                aforoTotal,
                entradasVendidas
        );
    }

    /**
     * Detalle completo. Debe invocarse dentro de una transaccion: accede a organizador (LAZY).
     * Los tipos de entrada se pasan explicitamente (Evento no mapea la coleccion).
     */
    public EventoResponseDto toResponse(Evento evento, List<TipoEntrada> tiposEntrada) {
        OrganizadorDto organizador = new OrganizadorDto(
                evento.getOrganizador().getId(),
                evento.getOrganizador().getNombre()
        );

        List<TipoEntradaResponseDto> tipos = tiposEntrada.stream()
                .map(tipoEntradaMapper::toResponse)
                .toList();

        return new EventoResponseDto(
                evento.getId(),
                evento.getNombre(),
                evento.getDescripcion(),
                evento.getRecinto(),
                evento.getCiudad(),
                evento.getFechaEvento(),
                evento.getCategoria(),
                evento.getImagenUrl(),
                evento.getEstado(),
                organizador,
                evento.getCreatedAt(),
                tipos
        );
    }
}
