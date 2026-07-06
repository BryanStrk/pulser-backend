package com.bryanstrk.pulser.shared.security;

import java.time.Instant;
import java.util.UUID;

/**
 * Contenido parseado de un token QR firmado. 'emitidoEn' tiene precision de segundos
 * (el token guarda epochSeconds).
 */
public record QrPayload(
        UUID entradaId,
        Long eventoId,
        Instant emitidoEn
) {
}
