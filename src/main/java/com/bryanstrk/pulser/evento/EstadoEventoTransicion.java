package com.bryanstrk.pulser.evento;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Maquina de estados del evento. Transiciones validas:
 *   BORRADOR  -> PUBLICADO | CANCELADO
 *   PUBLICADO -> FINALIZADO | CANCELADO
 *   FINALIZADO -> (terminal)
 *   CANCELADO  -> (terminal)
 */
public final class EstadoEventoTransicion {

    private static final Map<EstadoEvento, Set<EstadoEvento>> TRANSICIONES =
            new EnumMap<>(EstadoEvento.class);

    static {
        TRANSICIONES.put(EstadoEvento.BORRADOR,
                EnumSet.of(EstadoEvento.PUBLICADO, EstadoEvento.CANCELADO));
        TRANSICIONES.put(EstadoEvento.PUBLICADO,
                EnumSet.of(EstadoEvento.FINALIZADO, EstadoEvento.CANCELADO));
        TRANSICIONES.put(EstadoEvento.FINALIZADO,
                EnumSet.noneOf(EstadoEvento.class));
        TRANSICIONES.put(EstadoEvento.CANCELADO,
                EnumSet.noneOf(EstadoEvento.class));
    }

    private EstadoEventoTransicion() {
    }

    public static boolean esValida(EstadoEvento actual, EstadoEvento nuevo) {
        return TRANSICIONES.getOrDefault(actual, EnumSet.noneOf(EstadoEvento.class))
                .contains(nuevo);
    }
}
