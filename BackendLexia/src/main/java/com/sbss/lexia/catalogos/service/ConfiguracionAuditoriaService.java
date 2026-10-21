package com.sbss.lexia.catalogos.service;

import com.sbss.lexia.shared.security.SesionActual;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Registra cambios administrativos sin guardar secretos ni el contenido completo de documentos. */
@Service
@RequiredArgsConstructor
public class ConfiguracionAuditoriaService {

    private final JdbcTemplate jdbcTemplate;

    public void registrarCambio(String entidad, long registroId, String descripcion,
            String valorAnterior, String valorNuevo, HttpServletRequest request) {
        String ip = truncar(request == null ? null : request.getRemoteAddr(), 45);
        String agente = truncar(request == null ? null : request.getHeader("User-Agent"), 250);
        jdbcTemplate.update("""
                INSERT INTO auditoria
                    (estudio_id, usuario_id, entidad, registro_id, accion,
                     valores_anteriores, valores_nuevos, descripcion, ip_origen, user_agent, exito)
                VALUES (?, ?, ?, ?, 'ACTUALIZAR', JSON_OBJECT('valor', ?), JSON_OBJECT('valor', ?), ?, ?, ?, 1)
                """,
                SesionActual.estudioId(), SesionActual.usuarioId(), entidad, registroId,
                valorAnterior, valorNuevo, truncar(descripcion, 500), ip, agente);
    }

    private String truncar(String valor, int maximo) {
        if (valor == null) return null;
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }
}
