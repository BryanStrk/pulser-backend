package com.bryanstrk.pulser.shared.security;

import com.bryanstrk.pulser.usuario.RolUsuario;
import com.bryanstrk.pulser.usuario.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // Secretos base64 (>= 256 bits una vez decodificados) para HS256.
    private static final String SECRET =
            Base64.getEncoder().encodeToString("clave-super-secreta-de-32-bytes!!".getBytes());
    private static final String OTHER_SECRET =
            Base64.getEncoder().encodeToString("otra-clave-distinta-de-32-bytes!!".getBytes());
    private static final long ONE_HOUR_MS = 3_600_000L;

    private Usuario sampleUsuario() {
        Usuario usuario = new Usuario();
        usuario.setId(42L);
        usuario.setNombre("Bryan");
        usuario.setEmail("bryan@test.com");
        usuario.setPassword("hash");
        usuario.setRol(RolUsuario.ORGANIZADOR);
        return usuario;
    }

    @Test
    void generateToken_thenParse_returnsExpectedClaims() {
        JwtService jwtService = new JwtService(SECRET, ONE_HOUR_MS);

        String token = jwtService.generateToken(sampleUsuario());
        Claims claims = jwtService.parseClaims(token);

        assertThat(jwtService.extractEmail(token)).isEqualTo("bryan@test.com");
        assertThat(claims.getSubject()).isEqualTo("bryan@test.com");
        assertThat(claims.get("rol", String.class)).isEqualTo("ORGANIZADOR");
        assertThat(claims.get("uid", Long.class)).isEqualTo(42L);
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void parse_expiredToken_throwsExpiredJwtException() {
        // Expiracion negativa -> el token nace caducado.
        JwtService jwtService = new JwtService(SECRET, -1_000L);
        String token = jwtService.generateToken(sampleUsuario());

        assertThatThrownBy(() -> jwtService.parseClaims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parse_tokenSignedWithOtherKey_throwsSignatureException() {
        JwtService signer = new JwtService(SECRET, ONE_HOUR_MS);
        JwtService verifier = new JwtService(OTHER_SECRET, ONE_HOUR_MS);

        String token = signer.generateToken(sampleUsuario());

        assertThatThrownBy(() -> verifier.parseClaims(token))
                .isInstanceOf(SignatureException.class);
    }
}
