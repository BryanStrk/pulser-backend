package com.bryanstrk.pulser.entrada;

import com.bryanstrk.pulser.entrada.dto.CrearEntradaRequestDto;
import com.bryanstrk.pulser.entrada.dto.EntradaResponseDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Todas las rutas son autenticadas (cualquier usuario puede comprar; anonimo -> 401).
@RestController
public class EntradaController {

    private final EntradaService entradaService;

    public EntradaController(EntradaService entradaService) {
        this.entradaService = entradaService;
    }

    @PostMapping("/eventos/{eventoId}/entradas")
    @ResponseStatus(HttpStatus.CREATED)
    public EntradaResponseDto comprar(@PathVariable Long eventoId,
                                      @Valid @RequestBody CrearEntradaRequestDto request) {
        return entradaService.comprar(eventoId, request);
    }

    @GetMapping("/entradas/mis-entradas")
    public Page<EntradaResponseDto> misEntradas(Pageable pageable) {
        return entradaService.misEntradas(pageable);
    }

    @GetMapping("/entradas/{id}")
    public EntradaResponseDto obtener(@PathVariable UUID id) {
        return entradaService.obtener(id);
    }
}
