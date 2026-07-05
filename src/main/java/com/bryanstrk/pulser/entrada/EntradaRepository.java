package com.bryanstrk.pulser.entrada;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
