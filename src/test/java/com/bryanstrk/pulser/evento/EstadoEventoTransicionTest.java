package com.bryanstrk.pulser.evento;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoEventoTransicionTest {

    @Test
    void transicionesValidas() {
        assertThat(EstadoEventoTransicion.esValida(EstadoEvento.BORRADOR, EstadoEvento.PUBLICADO)).isTrue();
        assertThat(EstadoEventoTransicion.esValida(EstadoEvento.BORRADOR, EstadoEvento.CANCELADO)).isTrue();
        assertThat(EstadoEventoTransicion.esValida(EstadoEvento.PUBLICADO, EstadoEvento.FINALIZADO)).isTrue();
        assertThat(EstadoEventoTransicion.esValida(EstadoEvento.PUBLICADO, EstadoEvento.CANCELADO)).isTrue();
    }

    @Test
    void estadosTerminalesNoPermitenNingunaSalida() {
        for (EstadoEvento destino : EstadoEvento.values()) {
            assertThat(EstadoEventoTransicion.esValida(EstadoEvento.FINALIZADO, destino))
                    .as("FINALIZADO -> %s", destino).isFalse();
            assertThat(EstadoEventoTransicion.esValida(EstadoEvento.CANCELADO, destino))
                    .as("CANCELADO -> %s", destino).isFalse();
        }
    }

    @Test
    void transicionesInvalidasRepresentativas() {
        assertThat(EstadoEventoTransicion.esValida(EstadoEvento.PUBLICADO, EstadoEvento.BORRADOR)).isFalse();
        assertThat(EstadoEventoTransicion.esValida(EstadoEvento.BORRADOR, EstadoEvento.FINALIZADO)).isFalse();
    }
}
