package com.bryanstrk.pulser.checkin.event;

import com.bryanstrk.pulser.checkin.dto.CheckInFeedDto;

/**
 * Evento de dominio publicado por CheckInService DENTRO de su transaccion, tras registrar el
 * CheckIn. Lleva un snapshot inmutable (solo primitivos, sin entidades) para que el listener
 * AFTER_COMMIT pueda emitir al WS sin tocar la sesion de persistencia (ya cerrada tras el commit).
 * 'eventoId' es la clave de enrutado al destino /topic/eventos/{eventoId}/checkins.
 */
public record CheckInRegistradoEvent(Long eventoId, CheckInFeedDto feed) {
}
