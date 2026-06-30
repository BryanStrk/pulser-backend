package com.bryanstrk.pulser.entrada;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EntradaRepository extends JpaRepository<Entrada, UUID> {
}
