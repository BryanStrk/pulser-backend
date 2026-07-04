package com.bryanstrk.pulser.evento;

import com.bryanstrk.pulser.evento.dto.TipoEntradaRequestDto;
import com.bryanstrk.pulser.evento.dto.TipoEntradaResponseDto;
import org.springframework.stereotype.Component;

@Component
public class TipoEntradaMapper {

    public TipoEntrada toEntity(TipoEntradaRequestDto dto, Evento evento) {
        TipoEntrada tipoEntrada = new TipoEntrada();
        tipoEntrada.setEvento(evento);
        tipoEntrada.setNombre(dto.nombre());
        tipoEntrada.setPrecio(dto.precio());
        tipoEntrada.setAforo(dto.aforo());
        tipoEntrada.setDescripcion(dto.descripcion());
        // 'vendidas' queda a 0 por el valor por defecto del campo.
        return tipoEntrada;
    }

    public void applyUpdate(TipoEntrada tipoEntrada, TipoEntradaRequestDto dto) {
        tipoEntrada.setNombre(dto.nombre());
        tipoEntrada.setPrecio(dto.precio());
        tipoEntrada.setAforo(dto.aforo());
        tipoEntrada.setDescripcion(dto.descripcion());
    }

    public TipoEntradaResponseDto toResponse(TipoEntrada tipoEntrada) {
        return new TipoEntradaResponseDto(
                tipoEntrada.getId(),
                tipoEntrada.getNombre(),
                tipoEntrada.getPrecio(),
                tipoEntrada.getAforo(),
                tipoEntrada.getVendidas(),
                tipoEntrada.getDescripcion()
        );
    }
}
