package com.bryanstrk.pulser.evento;

import com.bryanstrk.pulser.evento.dto.CambiarEstadoDto;
import com.bryanstrk.pulser.evento.dto.EventoRequestDto;
import com.bryanstrk.pulser.evento.dto.EventoResponseDto;
import com.bryanstrk.pulser.evento.dto.EventoResumenDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// Rutas RELATIVAS al context-path (/api) -> endpoints finales /api/eventos/...
@RestController
@RequestMapping("/eventos")
public class EventoController {

    private final EventoService eventoService;

    public EventoController(EventoService eventoService) {
        this.eventoService = eventoService;
    }

    // ------------------------------------------------------------ Lectura publica

    @GetMapping
    public Page<EventoResumenDto> listarPublicos(
            @RequestParam(required = false) CategoriaEvento categoria,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return eventoService.listarPublicos(categoria, ciudad, q, pageable);
    }

    @GetMapping("/{id}")
    public EventoResponseDto obtener(@PathVariable Long id) {
        return eventoService.obtener(id);
    }

    // ------------------------------------------------------------ Lectura autenticada

    @GetMapping("/mis-eventos")
    @PreAuthorize("hasAnyRole('ORGANIZADOR','ADMIN')")
    public Page<EventoResumenDto> misEventos(
            @RequestParam(required = false) CategoriaEvento categoria,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return eventoService.listarMisEventos(categoria, ciudad, q, pageable);
    }

    // ------------------------------------------------------------ Escritura (dueño/ADMIN)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZADOR','ADMIN')")
    public EventoResponseDto crear(@Valid @RequestBody EventoRequestDto request) {
        return eventoService.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZADOR','ADMIN')")
    public EventoResponseDto actualizar(@PathVariable Long id,
                                        @Valid @RequestBody EventoRequestDto request) {
        return eventoService.actualizar(id, request);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ORGANIZADOR','ADMIN')")
    public EventoResponseDto cambiarEstado(@PathVariable Long id,
                                           @Valid @RequestBody CambiarEstadoDto request) {
        return eventoService.cambiarEstado(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ORGANIZADOR','ADMIN')")
    public void eliminar(@PathVariable Long id) {
        eventoService.eliminar(id);
    }

    @PostMapping("/{id}/imagen")
    @PreAuthorize("hasAnyRole('ORGANIZADOR','ADMIN')")
    public EventoResponseDto subirImagen(@PathVariable Long id,
                                         @RequestParam("file") MultipartFile file) {
        return eventoService.subirImagen(id, file);
    }
}
