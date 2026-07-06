package com.bryanstrk.pulser.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Firma y verifica el token QR de una entrada con HMAC-SHA256.
 * Formato: base64url(entradaId.eventoId.epochSeconds) + "." + base64url(hmac(parte1)).
 * La clave se construye una vez en el constructor desde qr.hmac-secret (base64).
 * Sin logging de secreto ni de firma.
 */
@Service
public class QrSigningService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DEC = Base64.getUrlDecoder();

    private final SecretKeySpec key;

    public QrSigningService(@Value("${qr.hmac-secret}") String secret) {
        this.key = new SecretKeySpec(Base64.getDecoder().decode(secret), HMAC_ALGORITHM);
    }

    public String firmar(UUID entradaId, Long eventoId, Instant emitidoEn) {
        String datos = entradaId + "." + eventoId + "." + emitidoEn.getEpochSecond();
        String parte1 = B64URL.encodeToString(datos.getBytes(StandardCharsets.UTF_8));
        String firma = B64URL.encodeToString(hmac(parte1));
        return parte1 + "." + firma;
    }

    /**
     * @return el payload si el token es autentico y bien formado; Optional.empty() si la firma
     * no cuadra (firma o payload manipulados, otra clave) o el formato es invalido.
     */
    public Optional<QrPayload> verificar(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String[] partes = token.split("\\.", -1);
        if (partes.length != 2) {
            return Optional.empty();
        }
        String parte1 = partes[0];
        String firmaRecibida = partes[1];
        try {
            byte[] firmaEsperada = hmac(parte1);
            byte[] firmaBytes = B64URL_DEC.decode(firmaRecibida);
            // Comparacion constant-time; nunca equals() sobre las firmas.
            if (!MessageDigest.isEqual(firmaEsperada, firmaBytes)) {
                return Optional.empty();
            }

            String datos = new String(B64URL_DEC.decode(parte1), StandardCharsets.UTF_8);
            String[] campos = datos.split("\\.", -1);
            if (campos.length != 3) {
                return Optional.empty();
            }
            UUID entradaId = UUID.fromString(campos[0]);
            Long eventoId = Long.valueOf(campos[1]);
            Instant emitidoEn = Instant.ofEpochSecond(Long.parseLong(campos[2]));
            return Optional.of(new QrPayload(entradaId, eventoId, emitidoEn));
        } catch (IllegalArgumentException e) {
            // base64 invalido, UUID/Long mal formado: token no valido.
            return Optional.empty();
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el HMAC del QR", e);
        }
    }
}
