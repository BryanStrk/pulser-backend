package com.bryanstrk.pulser.entrada;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EntradaRepository extends JpaRepository<Entrada, UUID> {

    /**
     * Reserva atomica de una plaza de aforo. El predicado 'vendidas < aforo' + el lock de fila
     * del UPDATE garantizan que solo una compra concurrente gane el ultimo asiento.
     * @return 1 si reservo la plaza, 0 si estaba agotado.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE TipoEntrada t SET t.vendidas = t.vendidas + 1 " +
            "WHERE t.id = :tipoEntradaId AND t.vendidas < t.aforo")
    int reservarPlaza(@Param("tipoEntradaId") Long tipoEntradaId);

    Page<Entrada> findByUsuarioId(Long usuarioId, Pageable pageable);

    /**
     * Carga la entrada con evento, organizador y tipo de entrada ya inicializados (fetch-join)
     * para poder aplicar la autorizacion de puerta y componer la respuesta sin depender de
     * carga perezosa (open-in-view=false) ni sufrir N+1.
     */
    @Query("SELECT e FROM Entrada e " +
            "JOIN FETCH e.evento ev JOIN FETCH ev.organizador " +
            "JOIN FETCH e.tipoEntrada " +
            "WHERE e.id = :id")
    Optional<Entrada> findConEventoYOrganizador(@Param("id") UUID id);

    /**
     * Marca la entrada como USADA de forma atomica ante doble-uso concurrente. El predicado
     * 'estado = VALIDA' + el lock de fila del UPDATE garantizan que solo una validacion
     * concurrente gane: la ganadora afecta 1 fila, la perdedora 0 (la entrada ya es USADA).
     * 'fechaUso' viaja como parametro :now (calculado en el service) para no depender de NOW()
     * de SQL y poder verificarlo en test. Se incrementa version para no dejar el @Version
     * inconsistente con el estado de la fila.
     * @return 1 si esta validacion marco la entrada, 0 si otra se adelanto.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Entrada e " +
            "SET e.estado = com.bryanstrk.pulser.entrada.EstadoEntrada.USADA, " +
            "e.fechaUso = :now, e.version = e.version + 1 " +
            "WHERE e.id = :id AND e.estado = com.bryanstrk.pulser.entrada.EstadoEntrada.VALIDA")
    int marcarUsada(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
