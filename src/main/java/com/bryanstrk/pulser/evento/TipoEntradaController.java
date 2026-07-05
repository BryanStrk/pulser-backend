package com.bryanstrk.pulser.evento;

import com.bryanstrk.pulser.evento.dto.TipoEntradaRequestDto;
import com.bryanstrk.pulser.evento.dto.TipoEntradaResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Rutas absolutas (dos raices): creacion anidada bajo /eventos/{id}, edicion/borrado por id propio.
@RestController
public class TipoEntradaController {

    private final TipoEntradaService tipoEntradaService;

    public TipoEntradaController(TipoEntradaService tipoEntradaService) {
        this.tipoEntradaService = tipoEntradaService;
    }

    @PostMapping("/eventos/{eventoId}/tipos-entrada")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZADOR','ADMIN')")
    public TipoEntradaResponseDto crear(@PathVariable Long eventoId,
                                        @Valid @RequestBody TipoEntradaRequestDto request) {
        return tipoEntradaService.crear(eventoId, request);
    }

    @PutMapping("/tipos-entrada/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZADOR','ADMIN')")
    public TipoEntradaResponseDto actualizar(@PathVariable Long id,
                                             @Valid @RequestBody TipoEntradaRequestDto request) {
        return tipoEntradaService.actualizar(id, request);
    }

    @DeleteMapping("/tipos-entrada/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ORGANIZADOR','ADMIN')")
    public void eliminar(@PathVariable Long id) {
        tipoEntradaService.eliminar(id);
    }
}
