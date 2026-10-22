package com.sbss.lexia.seguridad.preferencias.service;

import com.sbss.lexia.catalogos.service.ConfiguracionAuditoriaService;
import com.sbss.lexia.shared.security.SesionActual;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Preferencias personales: cada lectura/escritura se acota al usuario y estudio de la sesión. */
@Service
@RequiredArgsConstructor
public class PreferenciasUsuarioService {

    private final JdbcTemplate jdbcTemplate;
    private final ConfiguracionAuditoriaService auditoria;

    @Transactional(readOnly = true)
    public PreferenciasDto obtener() {
        Integer estudioId = SesionActual.estudioId();
        Long usuarioId = SesionActual.usuarioId();
        String tema = leer(estudioId, usuarioId, "tema", "CLARO");
        boolean colapsado = Boolean.parseBoolean(leer(estudioId, usuarioId, "sidebar_colapsado", "false"));
        if (!"CLARO".equals(tema) && !"OSCURO".equals(tema)) tema = "CLARO";
        return new PreferenciasDto(tema, colapsado);
    }

    @Transactional
    public PreferenciasDto guardar(GuardarPreferenciasRequest request, HttpServletRequest contexto) {
        Integer estudioId = SesionActual.estudioId();
        Long usuarioId = SesionActual.usuarioId();
        PreferenciasDto antes = obtener();
        String tema = request.tema().trim().toUpperCase(Locale.ROOT);
        guardarValor(estudioId, usuarioId, "tema", tema);
        guardarValor(estudioId, usuarioId, "sidebar_colapsado", Boolean.toString(request.sidebarColapsado()));
        PreferenciasDto despues = obtener();
        if (!antes.equals(despues)) {
            // La auditoría registra el identificador de una preferencia concreta; no guarda más datos personales.
            Long registroId = jdbcTemplate.queryForObject(
                    "SELECT id FROM preferencias_usuario WHERE estudio_id = ? AND usuario_id = ? AND clave = 'tema'",
                    Long.class, estudioId, usuarioId);
            auditoria.registrarCambio("preferencias_usuario", registroId,
                    "Actualización de preferencias personales", antes.toString(), despues.toString(), contexto);
        }
        return despues;
    }

    private String leer(Integer estudioId, Long usuarioId, String clave, String fallback) {
        return jdbcTemplate.query(
                "SELECT valor FROM preferencias_usuario WHERE estudio_id = ? AND usuario_id = ? AND clave = ?",
                rs -> rs.next() ? rs.getString(1) : fallback, estudioId, usuarioId, clave);
    }

    private void guardarValor(Integer estudioId, Long usuarioId, String clave, String valor) {
        String anterior = leer(estudioId, usuarioId, clave, null);
        if (valor.equals(anterior)) return;
        jdbcTemplate.update("""
                INSERT INTO preferencias_usuario (estudio_id, usuario_id, clave, valor)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE valor = VALUES(valor), fecha_modificacion = CURRENT_TIMESTAMP(3)
                """, estudioId, usuarioId, clave, valor);
    }

    public record PreferenciasDto(String tema, boolean sidebarColapsado) {
    }

    public record GuardarPreferenciasRequest(String tema, boolean sidebarColapsado) {
    }
}
