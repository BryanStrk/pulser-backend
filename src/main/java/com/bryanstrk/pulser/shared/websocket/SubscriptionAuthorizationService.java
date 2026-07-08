package com.bryanstrk.pulser.shared.websocket;

import com.bryanstrk.pulser.evento.EventoRepository;
import com.bryanstrk.pulser.usuario.RolUsuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regla de ownership para suscribirse al feed de check-ins de un evento: ADMIN siempre; en otro
 * caso, el usuario debe ser el organizador del evento.
 *
 * Vive en su propio servicio @Transactional(readOnly = true) a proposito: el interceptor STOMP la
 * invoca desde el hilo del canal, que NO trae una transaccion abierta (a diferencia de REST, donde
 * la abre el metodo @Transactional del servicio). Sin esta frontera transaccional, la query de
 * existencia revienta con TransactionRequiredException. La comprobacion que toca BD se aisla aqui;
 * el parseo del destino se queda en el interceptor.
 */
@Service
public class SubscriptionAuthorizationService {

    private final EventoRepository eventoRepository;

    public SubscriptionAuthorizationService(EventoRepository eventoRepository) {
        this.eventoRepository = eventoRepository;
    }

    @Transactional(readOnly = true)
    public boolean puedeSuscribirse(Long eventoId, WsUser user) {
        if (user.rol() == RolUsuario.ADMIN) {
            return true;
        }
        return eventoRepository.existsByIdAndOrganizador_Id(eventoId, user.id());
    }
}
