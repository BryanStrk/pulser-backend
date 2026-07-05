package com.bryanstrk.pulser.entrada;

import com.bryanstrk.pulser.entrada.dto.EntradaResponseDto;
import com.bryanstrk.pulser.entrada.dto.EventoEntradaDto;
import com.bryanstrk.pulser.evento.Evento;
import com.bryanstrk.pulser.evento.TipoEntrada;
import org.springframework.stereotype.Component;

@Component
public class EntradaMapper {

    /**
     * Para lecturas (detalle / mis-entradas): lee evento y tipoEntrada LAZY.
     * DEBE invocarse dentro de una transaccion.
     */
    public EntradaResponseDto toResponse(Entrada entrada) {
        return build(entrada, entrada.getEvento(), entrada.getTipoEntrada());
    }

    /**
     * Para la compra: el UPDATE atomico limpia el contexto, asi que el evento y el tipoEntrada
     * ya cargados se pasan explicitamente (sus campos escalares siguen disponibles aunque esten detached).
     */
    public EntradaResponseDto toResponse(Entrada entrada, Evento evento, TipoEntrada tipoEntrada) {
        return build(entrada, evento, tipoEntrada);
    }

    private EntradaResponseDto build(Entrada entrada, Evento evento, TipoEntrada tipoEntrada) {
        EventoEntradaDto eventoDto = new EventoEntradaDto(
                evento.getId(),
                evento.getNombre(),
                evento.getFechaEvento(),
                evento.getRecinto(),
                evento.getCiudad()
        );

        return new EntradaResponseDto(
                entrada.getId(),
                entrada.getEstado(),
                entrada.getPrecio(),
                tipoEntrada.getNombre(),
                entrada.getCodigoQr(),
                entrada.getFechaCompra(),
                entrada.getFechaUso(),
                eventoDto
        );
    }
}
