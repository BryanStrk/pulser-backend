package com.bryanstrk.pulser.usuario.auth;

import com.bryanstrk.pulser.shared.exception.BusinessException;
import com.bryanstrk.pulser.shared.security.JwtService;
import com.bryanstrk.pulser.usuario.RolUsuario;
import com.bryanstrk.pulser.usuario.Usuario;
import com.bryanstrk.pulser.usuario.UsuarioRepository;
import com.bryanstrk.pulser.usuario.auth.dto.AuthResponseDto;
import com.bryanstrk.pulser.usuario.auth.dto.LoginRequestDto;
import com.bryanstrk.pulser.usuario.auth.dto.RegisterRequestDto;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
public class AuthService {

    // Whitelist anti privilege-escalation: ADMIN nunca es asignable via API.
    private static final Set<RolUsuario> ROLES_PERMITIDOS =
            EnumSet.of(RolUsuario.ASISTENTE, RolUsuario.ORGANIZADOR);
    private static final RolUsuario ROL_POR_DEFECTO = RolUsuario.ASISTENTE;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponseDto register(RegisterRequestDto request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new BusinessException("El email ya esta registrado");
        }

        RolUsuario rol = request.rol() == null ? ROL_POR_DEFECTO : request.rol();
        if (!ROLES_PERMITIDOS.contains(rol)) {
            throw new BusinessException("Rol no permitido");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre());
        usuario.setEmail(request.email());
        usuario.setPassword(passwordEncoder.encode(request.password()));
        usuario.setRol(rol);

        Usuario guardado = usuarioRepository.save(usuario);
        String token = jwtService.generateToken(guardado);
        return toAuthResponse(guardado, token);
    }

    public AuthResponseDto login(LoginRequestDto request) {
        // Lanza BadCredentialsException si email/password no cuadran -> 401 generico.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Credenciales invalidas"));

        String token = jwtService.generateToken(usuario);
        return toAuthResponse(usuario, token);
    }

    private AuthResponseDto toAuthResponse(Usuario usuario, String token) {
        return new AuthResponseDto(
                token,
                "Bearer",
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol()
        );
    }
}
