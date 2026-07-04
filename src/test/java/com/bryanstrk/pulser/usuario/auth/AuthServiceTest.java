package com.bryanstrk.pulser.usuario.auth;

import com.bryanstrk.pulser.shared.exception.BusinessException;
import com.bryanstrk.pulser.shared.security.JwtService;
import com.bryanstrk.pulser.usuario.RolUsuario;
import com.bryanstrk.pulser.usuario.Usuario;
import com.bryanstrk.pulser.usuario.UsuarioRepository;
import com.bryanstrk.pulser.usuario.auth.dto.AuthResponseDto;
import com.bryanstrk.pulser.usuario.auth.dto.LoginRequestDto;
import com.bryanstrk.pulser.usuario.auth.dto.RegisterRequestDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private Usuario persisted(RolUsuario rol) {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNombre("Bryan");
        usuario.setEmail("bryan@test.com");
        usuario.setPassword("hashed");
        usuario.setRol(rol);
        return usuario;
    }

    @Test
    void register_ok_encodesPasswordAndReturnsToken() {
        RegisterRequestDto request =
                new RegisterRequestDto("Bryan", "bryan@test.com", "secret12", RolUsuario.ORGANIZADOR);

        when(usuarioRepository.existsByEmail("bryan@test.com")).thenReturn(false);
        when(passwordEncoder.encode("secret12")).thenReturn("hashed");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtService.generateToken(any(Usuario.class))).thenReturn("jwt-token");

        AuthResponseDto response = authService.register(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.tipo()).isEqualTo("Bearer");
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("bryan@test.com");
        assertThat(response.rol()).isEqualTo(RolUsuario.ORGANIZADOR);
        verify(passwordEncoder).encode("secret12");
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void register_nullRole_defaultsToAsistente() {
        RegisterRequestDto request =
                new RegisterRequestDto("Bryan", "bryan@test.com", "secret12", null);

        when(usuarioRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtService.generateToken(any(Usuario.class))).thenReturn("jwt-token");

        AuthResponseDto response = authService.register(request);

        assertThat(response.rol()).isEqualTo(RolUsuario.ASISTENTE);
    }

    @Test
    void register_duplicateEmail_throwsBusinessException() {
        RegisterRequestDto request =
                new RegisterRequestDto("Bryan", "bryan@test.com", "secret12", RolUsuario.ASISTENTE);

        when(usuarioRepository.existsByEmail("bryan@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void register_adminRole_isRejectedByWhitelist() {
        RegisterRequestDto request =
                new RegisterRequestDto("Bryan", "bryan@test.com", "secret12", RolUsuario.ADMIN);

        when(usuarioRepository.existsByEmail("bryan@test.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Rol no permitido");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void login_ok_authenticatesAndReturnsToken() {
        LoginRequestDto request = new LoginRequestDto("bryan@test.com", "secret12");

        when(usuarioRepository.findByEmail("bryan@test.com"))
                .thenReturn(Optional.of(persisted(RolUsuario.ASISTENTE)));
        when(jwtService.generateToken(any(Usuario.class))).thenReturn("jwt-token");

        AuthResponseDto response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.email()).isEqualTo("bryan@test.com");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_badCredentials_throwsAndDoesNotIssueToken() {
        LoginRequestDto request = new LoginRequestDto("bryan@test.com", "wrong");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwtService, never()).generateToken(any());
    }
}
