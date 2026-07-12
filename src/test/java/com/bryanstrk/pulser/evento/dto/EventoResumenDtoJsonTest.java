package com.bryanstrk.pulser.evento.dto;

import com.bryanstrk.pulser.evento.CategoriaEvento;
import com.bryanstrk.pulser.evento.EstadoEvento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que la ocupacion agregada aparece en el JSON del item de listado (slice @JsonTest:
 * usa la config Jackson real de Boot, sin arrancar contexto ni DB).
 */
@JsonTest
class EventoResumenDtoJsonTest {

    @Autowired
    private JacksonTester<EventoResumenDto> json;

    @Test
    void serializa_incluyeAforoTotalYEntradasVendidas() throws Exception {
        EventoResumenDto dto = new EventoResumenDto(
                10L, "Concierto", "Madrid",
                LocalDateTime.of(2027, 5, 1, 21, 0),
                CategoriaEvento.CONCIERTO, EstadoEvento.PUBLICADO,
                "https://img/evento.png", 100L, 40L);

        JsonContent<EventoResumenDto> content = json.write(dto);

        assertThat(content).hasJsonPathNumberValue("$.aforoTotal");
        assertThat(content).hasJsonPathNumberValue("$.entradasVendidas");
        assertThat(content).extractingJsonPathNumberValue("$.aforoTotal").isEqualTo(100);
        assertThat(content).extractingJsonPathNumberValue("$.entradasVendidas").isEqualTo(40);
    }

    @Test
    void serializa_eventoSinTipos_aforoYVendidasCero() throws Exception {
        EventoResumenDto dto = new EventoResumenDto(
                11L, "Concierto", "Madrid",
                LocalDateTime.of(2027, 5, 1, 21, 0),
                CategoriaEvento.CONCIERTO, EstadoEvento.PUBLICADO,
                null, 0L, 0L);

        JsonContent<EventoResumenDto> content = json.write(dto);

        assertThat(content).extractingJsonPathNumberValue("$.aforoTotal").isEqualTo(0);
        assertThat(content).extractingJsonPathNumberValue("$.entradasVendidas").isEqualTo(0);
    }
}
