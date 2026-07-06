package com.bryanstrk.pulser.shared.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QrSigningServiceTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("clave-qr-secreta-para-tests-32b!".getBytes());
    private static final String OTHER_SECRET =
            Base64.getEncoder().encodeToString("otra-clave-qr-distinta-de-tests!".getBytes());

    private final QrSigningService service = new QrSigningService(SECRET);

    @Test
    void firmarYVerificar_roundtrip_devuelveElMismoPayload() {
        UUID entradaId = UUID.randomUUID();
        Long eventoId = 42L;
        Instant emitidoEn = Instant.now();

        String token = service.firmar(entradaId, eventoId, emitidoEn);
        Optional<QrPayload> resultado = service.verificar(token);

        assertThat(token).contains(".");
        assertThat(resultado).isPresent();
        QrPayload payload = resultado.get();
        assertThat(payload.entradaId()).isEqualTo(entradaId);
        assertThat(payload.eventoId()).isEqualTo(eventoId);
        // El token guarda epochSeconds -> se pierden los nanos.
        assertThat(payload.emitidoEn()).isEqualTo(emitidoEn.truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    void verificar_firmaManipulada_devuelveEmpty() {
        String token = service.firmar(UUID.randomUUID(), 1L, Instant.now());
        // Alteramos el ultimo caracter (parte de la firma).
        String manipulado = flipUltimoChar(token);

        assertThat(service.verificar(manipulado)).isEmpty();
    }

    @Test
    void verificar_payloadManipulado_devuelveEmpty() {
        String token = service.firmar(UUID.randomUUID(), 1L, Instant.now());
        String[] partes = token.split("\\.", -1);
        // Alteramos el primer caracter de la parte de datos: la firma deja de cuadrar.
        String parte1Manipulada = flipPrimerChar(partes[0]);
        String manipulado = parte1Manipulada + "." + partes[1];

        assertThat(service.verificar(manipulado)).isEmpty();
    }

    @Test
    void verificar_formatoInvalido_devuelveEmpty() {
        assertThat(service.verificar(null)).isEmpty();
        assertThat(service.verificar("")).isEmpty();
        assertThat(service.verificar("sin-punto-separador")).isEmpty();
        assertThat(service.verificar("una.dos.tres")).isEmpty();
    }

    @Test
    void verificar_tokenFirmadoConOtraClave_devuelveEmpty() {
        QrSigningService firmante = new QrSigningService(SECRET);
        QrSigningService verificador = new QrSigningService(OTHER_SECRET);

        String token = firmante.firmar(UUID.randomUUID(), 1L, Instant.now());

        assertThat(verificador.verificar(token)).isEmpty();
    }

    // ---------------------------------------------------------------- helpers

    private String flipUltimoChar(String s) {
        char ultimo = s.charAt(s.length() - 1);
        char reemplazo = ultimo == 'A' ? 'B' : 'A';
        return s.substring(0, s.length() - 1) + reemplazo;
    }

    private String flipPrimerChar(String s) {
        char primero = s.charAt(0);
        char reemplazo = primero == 'A' ? 'B' : 'A';
        return reemplazo + s.substring(1);
    }
}
