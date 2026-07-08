package com.bryanstrk.pulser.checkin;

import com.bryanstrk.pulser.checkin.dto.CheckInRequestDto;
import com.bryanstrk.pulser.checkin.dto.CheckInResponseDto;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Validacion en puerta. Solo ORGANIZADOR o ADMIN (el @PreAuthorize corta por rol; la propiedad
// fina evento->organizador se comprueba en el service). El comprador NO valida su propia entrada.
@RestController
public class CheckInController {

    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    @PostMapping("/checkins")
    @PreAuthorize("hasAnyRole('ORGANIZADOR','ADMIN')")
    public CheckInResponseDto validar(@Valid @RequestBody CheckInRequestDto request) {
        return checkInService.validar(request.token(), request.puerta());
    }
}
