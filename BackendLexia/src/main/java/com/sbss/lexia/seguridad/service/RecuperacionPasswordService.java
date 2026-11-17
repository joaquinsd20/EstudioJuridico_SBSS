package com.sbss.lexia.seguridad.service;

import com.sbss.lexia.notificaciones.dto.NotificacionDtos.CrearNotificacion;
import com.sbss.lexia.notificaciones.service.NotificacionService;
import com.sbss.lexia.shared.correo.CorreoService;
import com.sbss.lexia.shared.exception.PasswordInvalidaException;
import com.sbss.lexia.usuarios.repository.UsuarioGestionRepositoryJdbc;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * "¿Olvidaste tu contrasena?" sin intervencion del administrador.
 *
 * 1. La persona escribe su correo. La respuesta es SIEMPRE la misma, exista o
 *    no la cuenta, para que nadie pueda averiguar que correos estan registrados.
 * 2. Si la cuenta existe y esta activa (o bloqueada por intentos), se genera
 *    un token aleatorio de 256 bits, se guarda solo su hash y se envia un
 *    enlace que vence a los 30 minutos y sirve una sola vez.
 * 3. Con el enlace se elige una contrasena nueva (misma politica que siempre).
 *    Eso cierra todas las sesiones abiertas, desbloquea la cuenta y anula
 *    cualquier otro enlace pendiente.
 *
 * Si el correo no esta configurado (sin clave de Brevo), en vez de enviar el
 * enlace se avisa a los administradores del estudio dentro de LEXIA para que
 * restablezcan la contrasena desde Usuarios, como hasta ahora.
 */
@Slf4j
@Service
public class RecuperacionPasswordService {

    private static final int MINUTOS_VALIDEZ = 30;
    private static final int MAX_SOLICITUDES_POR_HORA = 3;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final CorreoService correo;
    private final NotificacionService notificaciones;
    private final UsuarioGestionRepositoryJdbc usuarios;
    private final String urlFrontend;

    public RecuperacionPasswordService(
            NamedParameterJdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            CorreoService correo,
            NotificacionService notificaciones,
            UsuarioGestionRepositoryJdbc usuarios,
            @Value("${lexia.frontend-url:}") String urlFrontend,
            @Value("${lexia.cors.origenes:}") String origenes) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.correo = correo;
        this.notificaciones = notificaciones;
        this.usuarios = usuarios;
        // Sin URL explicita se usa el primer origen permitido por CORS, que es
        // justamente donde vive el frontend.
        String base = (urlFrontend == null || urlFrontend.isBlank())
                ? (origenes == null ? "" : origenes.split(",")[0].trim())
                : urlFrontend.trim();
        this.urlFrontend = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private record Cuenta(Long id, Integer estudioId, String email, String nombres, String estado) {
    }

    /** Paso 1. No revela si la cuenta existe: el controlador responde igual siempre. */
    @Transactional
    public void solicitar(String emailIngresado, String ip) {
        String email = emailIngresado == null ? "" : emailIngresado.trim().toLowerCase(Locale.ROOT);
        List<Cuenta> encontradas = jdbc.query("""
                SELECT id, estudio_id, email, nombres, estado
                  FROM usuarios
                 WHERE LOWER(email) = :email
                """, Map.of("email", email), (rs, i) -> new Cuenta(
                rs.getLong("id"), rs.getInt("estudio_id"), rs.getString("email"),
                rs.getString("nombres"), rs.getString("estado")));

        if (encontradas.isEmpty()) return;
        Cuenta cuenta = encontradas.get(0);
        // Una cuenta desactivada por el estudio no se reactiva por esta via.
        if (!"ACTIVO".equals(cuenta.estado()) && !"BLOQUEADO".equals(cuenta.estado())) return;

        Integer recientes = jdbc.queryForObject("""
                SELECT COUNT(*) FROM recuperaciones_password
                 WHERE usuario_id = :id AND creado_en > NOW(3) - INTERVAL 1 HOUR
                """, Map.of("id", cuenta.id()), Integer.class);
        if (recientes != null && recientes >= MAX_SOLICITUDES_POR_HORA) {
            log.info("Recuperacion de contrasena: limite por hora alcanzado (usuario {})", cuenta.id());
            return;
        }

        if (!correo.activo()) {
            avisarAdministradores(cuenta);
            return;
        }

        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        // Un enlace nuevo anula los anteriores que no se usaron.
        jdbc.update("""
                UPDATE recuperaciones_password SET usado_en = NOW(3)
                 WHERE usuario_id = :id AND usado_en IS NULL
                """, Map.of("id", cuenta.id()));
        jdbc.update("""
                INSERT INTO recuperaciones_password (usuario_id, token_hash, expira_en, ip_solicitud)
                VALUES (:id, :hash, NOW(3) + INTERVAL %d MINUTE, :ip)
                """.formatted(MINUTOS_VALIDEZ), new MapSqlParameterSource()
                .addValue("id", cuenta.id())
                .addValue("hash", sha256(token))
                .addValue("ip", ip == null ? null : ip.substring(0, Math.min(ip.length(), 45))));

        String enlace = urlFrontend + "/restablecer-password?token=" + token;
        boolean enviado = correo.enviar(cuenta.email(), cuenta.nombres(),
                "Restablece tu contraseña de LEXIA", html(cuenta.nombres(), enlace), texto(cuenta.nombres(), enlace));
        if (!enviado) {
            // El proveedor fallo: que al menos el administrador se entere.
            avisarAdministradores(cuenta);
        }
        usuarios.auditar(cuenta.estudioId(), cuenta.id(), "usuarios", cuenta.id(), null, "ACTUALIZAR",
                "Solicito recuperar su contrasena" + (enviado ? "; se envio el enlace por correo." : "; no se pudo enviar el correo."));
    }

    /** Para que la pantalla avise antes de escribir si el enlace ya no sirve. */
    @Transactional(readOnly = true)
    public boolean tokenValido(String token) {
        if (token == null || token.isBlank()) return false;
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM recuperaciones_password
                 WHERE token_hash = :hash AND usado_en IS NULL AND expira_en > NOW(3)
                """, Map.of("hash", sha256(token)), Integer.class);
        return n != null && n > 0;
    }

    /** Paso 3. Cambia la contrasena con un enlace valido. */
    @Transactional
    public void restablecer(String token, String passwordNueva) {
        if (token == null || token.isBlank()) throw enlaceInvalido();
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT r.id AS recuperacion_id, u.id AS usuario_id, u.estudio_id, u.email
                  FROM recuperaciones_password r
                  JOIN usuarios u ON u.id = r.usuario_id
                 WHERE r.token_hash = :hash AND r.usado_en IS NULL AND r.expira_en > NOW(3)
                   FOR UPDATE
                """, Map.of("hash", sha256(token)));
        if (filas.isEmpty()) throw enlaceInvalido();

        Map<String, Object> f = filas.get(0);
        Long usuarioId = ((Number) f.get("usuario_id")).longValue();
        Integer estudioId = ((Number) f.get("estudio_id")).intValue();
        String email = (String) f.get("email");

        PoliticaPassword.validar(passwordNueva, email);

        // fecha_cambio_password truncada al segundo: es la "version" del token
        // JWT, asi que cambiarla cierra todas las sesiones abiertas (ver V13).
        jdbc.update("""
                UPDATE usuarios
                   SET password_hash = :hash, debe_cambiar_password = 0,
                       fecha_cambio_password = DATE_SUB(NOW(3), INTERVAL MICROSECOND(NOW(3)) MICROSECOND),
                       intentos_fallidos = 0, estado = IF(estado = 'BLOQUEADO', 'ACTIVO', estado),
                       fecha_bloqueo = NULL
                 WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("hash", passwordEncoder.encode(passwordNueva))
                .addValue("id", usuarioId));
        jdbc.update("""
                UPDATE recuperaciones_password SET usado_en = NOW(3)
                 WHERE usuario_id = :id AND usado_en IS NULL
                """, Map.of("id", usuarioId));
        usuarios.auditar(estudioId, usuarioId, "usuarios", usuarioId, null, "ACTUALIZAR",
                "Restablecio su contrasena con un enlace de recuperacion; se cerraron sus sesiones abiertas.");
    }

    private void avisarAdministradores(Cuenta cuenta) {
        List<Long> admins = jdbc.queryForList("""
                SELECT DISTINCT u.id
                  FROM usuarios u
                  JOIN usuarios_roles ur ON ur.usuario_id = u.id
                  JOIN roles r ON r.id = ur.rol_id
                 WHERE r.codigo = 'ADMINISTRADOR' AND u.estudio_id = :estudio AND u.estado = 'ACTIVO'
                """, Map.of("estudio", cuenta.estudioId()), Long.class);
        for (Long admin : admins) {
            try {
                notificaciones.crear(new CrearNotificacion(admin, "SEGURIDAD",
                        "Solicitud de nueva contraseña",
                        cuenta.email() + " olvidó su contraseña. Restablécela desde Usuarios.",
                        "ADVERTENCIA", "usuarios", cuenta.id(), null, "/usuarios"));
            } catch (RuntimeException e) {
                // Un fallo aqui no puede cambiar la respuesta: delataria que la cuenta existe.
                log.warn("No se pudo avisar al administrador {}: {}", admin, e.toString());
            }
        }
    }

    private static PasswordInvalidaException enlaceInvalido() {
        return new PasswordInvalidaException("ENLACE_INVALIDO",
                "El enlace no es valido o ya vencio. Solicita uno nuevo.");
    }

    private static String sha256(String valor) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String texto(String nombre, String enlace) {
        return "Hola " + (nombre == null ? "" : nombre) + ",\n\n"
                + "Recibimos una solicitud para restablecer tu contraseña de LEXIA.\n"
                + "Abre este enlace para elegir una nueva (vence en " + MINUTOS_VALIDEZ + " minutos):\n\n"
                + enlace + "\n\n"
                + "Si no fuiste tú, ignora este correo: tu contraseña no cambiará.\n\n"
                + "LEXIA · Estudio Jurídico SBSS";
    }

    private static String html(String nombre, String enlace) {
        String n = HtmlUtils.htmlEscape(nombre == null ? "" : nombre);
        String e = HtmlUtils.htmlEscape(enlace);
        return """
                <div style="margin:0;padding:32px 16px;background:#0f1330;font-family:Segoe UI,Arial,sans-serif">
                  <div style="max-width:480px;margin:0 auto;background:#ffffff;border-radius:16px;padding:32px">
                    <p style="margin:0 0 24px;font-size:14px;font-weight:700;letter-spacing:4px;color:#4f46e5">LEXIA</p>
                    <h1 style="margin:0 0 12px;font-size:22px;color:#0f172a">Restablece tu contraseña</h1>
                    <p style="margin:0 0 24px;font-size:15px;line-height:1.6;color:#475569">
                      Hola %s, recibimos una solicitud para restablecer tu contraseña.
                      El enlace vence en %d minutos y sirve una sola vez.</p>
                    <a href="%s" style="display:inline-block;padding:13px 22px;border-radius:10px;
                       background:#4f46e5;color:#ffffff;font-weight:600;text-decoration:none">Elegir nueva contraseña</a>
                    <p style="margin:24px 0 0;font-size:13px;line-height:1.6;color:#64748b">
                      Si no fuiste tú, ignora este correo: tu contraseña no cambiará.</p>
                  </div>
                  <p style="max-width:480px;margin:16px auto 0;font-size:12px;color:#94a3b8;text-align:center">
                    Estudio Jurídico SBSS</p>
                </div>
                """.formatted(n, MINUTOS_VALIDEZ, e);
    }
}
