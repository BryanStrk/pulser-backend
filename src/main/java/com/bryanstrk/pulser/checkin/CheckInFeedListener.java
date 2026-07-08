package com.bryanstrk.pulser.checkin;

import com.bryanstrk.pulser.checkin.event.CheckInRegistradoEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emite al dashboard en vivo cada validacion en puerta, SOLO despues de que la transaccion de
 * CheckInService.validar() haya commiteado (AFTER_COMMIT). Asi el dashboard nunca ve un check-in
 * que un rollback posterior deshizo. Trabaja unicamente con el snapshot inmutable del evento de
 * dominio: no toca la sesion de persistencia (ya cerrada en esta fase).
 */
@Component
public class CheckInFeedListener {

    private static final String DESTINO_BASE = "/topic/eventos/";
    private static final String DESTINO_SUFIJO = "/checkins";

    private final SimpMessagingTemplate messagingTemplate;

    public CheckInFeedListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCheckInRegistrado(CheckInRegistradoEvent event) {
        String destino = DESTINO_BASE + event.eventoId() + DESTINO_SUFIJO;
        messagingTemplate.convertAndSend(destino, event.feed());
    }
}
