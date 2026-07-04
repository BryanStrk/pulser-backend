package com.bryanstrk.pulser.shared.security;

import com.bryanstrk.pulser.usuario.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
    }

    /**
     * Firma un JWT HS256 para el usuario dado.
     * Claims: subject = email, "rol", "uid" (id), issuedAt, expiration.
     */
    public String generateToken(Usuario usuario) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expirationMs);
        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim("rol", usuario.getRol().name())
                .claim("uid", usuario.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifica firma + expiracion y devuelve los claims.
     * Lanza JwtException (firma invalida, expirado, malformado) si el token no es valido.
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extrae el email (subject). Propaga JwtException si el token no es valido.
     */
    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }
}
