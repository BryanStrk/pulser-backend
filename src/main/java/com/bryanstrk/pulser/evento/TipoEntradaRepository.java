package com.bryanstrk.pulser.evento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TipoEntradaRepository extends JpaRepository<TipoEntrada, Long> {

    List<TipoEntrada> findByEventoId(Long eventoId);
}
