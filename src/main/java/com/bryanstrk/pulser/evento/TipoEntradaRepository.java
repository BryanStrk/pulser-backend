package com.bryanstrk.pulser.evento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TipoEntradaRepository extends JpaRepository<TipoEntrada, Long> {

    List<TipoEntrada> findByEventoId(Long eventoId);

    /**
     * Ocupacion agregada de una pagina de eventos en UNA sola query (sin N+1, sin cargar
     * colecciones): una fila por evento con al menos un tipo de entrada. Los eventos sin tipos
     * no aparecen -> el llamante los resuelve a 0/0.
     */
    @Query("SELECT t.evento.id AS eventoId, " +
            "SUM(t.aforo) AS aforoTotal, " +
            "SUM(t.vendidas) AS entradasVendidas " +
            "FROM TipoEntrada t " +
            "WHERE t.evento.id IN :ids " +
            "GROUP BY t.evento.id")
    List<OcupacionEventoProjection> sumarOcupacionPorEvento(@Param("ids") Collection<Long> ids);
}
