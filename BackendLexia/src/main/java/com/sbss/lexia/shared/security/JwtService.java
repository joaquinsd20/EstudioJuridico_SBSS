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
                .claim(CLAIM_VERSION_PASSWORD, versionPassword(usuario))
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(expiracionSegundos)))
                .signWith(clave)
                .compact();
    }

    /**
     * Version de la contrasena con la que se emitio el token: el instante del
     * ultimo cambio en segundos, o 0 si nunca se cambio. Al cambiarla, la
     * version de la base avanza y todo token con la anterior deja de valer.
     * Comparar por igualdad es exacto; comparar la fecha de emision contra la
     * del cambio dejaria vivo cualquier token emitido en ese mismo segundo.
     */
    static final String CLAIM_VERSION_PASSWORD = "pv";

    public static long versionPassword(UsuarioPrincipal usuario) {
        Instant cambio = usuario.getFechaCambioPassword();
        return cambio == null ? 0L : cambio.getEpochSecond();
    }

    /** Correo y version de contrasena de un token valido. */
    public record TokenLeido(String email, long versionPassword) {
    }

    /** Lee el token, o devuelve null si la firma o la vigencia no son validas. */
    public TokenLeido leer(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String email = claims.get("email", String.class);
            if (email == null) {
                return null;
            }
            // Un token emitido antes de V13 no trae la version: cuenta como 0,
            // que solo es valida mientras el usuario no haya cambiado la clave.
            Number version = claims.get(CLAIM_VERSION_PASSWORD, Number.class);
            return new TokenLeido(email, version == null ? 0L : version.longValue());
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
