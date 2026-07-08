package com.bryanstrk.pulser.evento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EventoRepository extends JpaRepository<Evento, Long>, JpaSpecificationExecutor<Evento> {

    /**
     * Ownership del feed WS: true si el evento existe y su organizador es el usuario dado.
     * Consulta de existencia (sin cargar entidad ni tocar la asociacion lazy), usada por el
     * interceptor STOMP para autorizar la suscripcion al feed de check-ins de un evento.
     */
    boolean existsByIdAndOrganizador_Id(Long id, Long organizadorId);
}
