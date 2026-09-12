package com.sbss.lexia.shared.security;

import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Emite y valida los JWT de LEXIA.
 *
 * El `subject` del token es el `uuid_publico`, nunca el id BIGINT: ese no sale
 * del backend, y un identificador secuencial en un token permitiria adivinar
 * cuantos usuarios tiene el estudio.
 */
@Service
public class JwtService {

    private final SecretKey clave;

    /** Vida del token en segundos. Se expone para informarla en la respuesta. */
    @Getter
    private final long expiracionSegundos;

    public JwtService(
            @Value("${lexia.jwt.secreto}") String secreto,
            @Value("${lexia.jwt.expiracion-segundos}") long expiracionSegundos) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "lexia.jwt.secreto debe tener al menos 32 bytes para HS256; tiene " + bytes.length);
        }
        this.clave = Keys.hmacShaKeyFor(bytes);
        this.expiracionSegundos = expiracionSegundos;
    }

    public String generar(UsuarioPrincipal usuario) {
        Instant ahora = Instant.now();
        List<String> authorities = usuario.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .subject(usuario.getUuid())
                .claim("email", usuario.getEmail())
                .claim("authorities", authorities)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(expiracionSegundos)))
                .signWith(clave)
                .compact();
    }

    /** Devuelve el correo del token, o null si la firma o la vigencia no son validas. */
    public String emailDelToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("email", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
