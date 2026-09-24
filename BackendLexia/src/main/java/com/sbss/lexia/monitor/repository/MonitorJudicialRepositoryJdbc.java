package com.sbss.lexia.monitor.repository;

import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.Catalogos;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.Consulta;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.ExpedienteMonitor;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.ExpedienteOpcion;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.RegistrarConsultaRequest;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.Resumen;
import com.sbss.lexia.shared.security.SesionActual;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MonitorJudicialRepositoryJdbc {

    private final NamedParameterJdbcTemplate jdbc;

    public List<ExpedienteMonitor> listar(String expediente, String cliente, String abogado,
            String organo, String estadoProcesal, LocalDate desde, LocalDate hasta,
            boolean verTodos, boolean verConfidenciales) {

        StringBuilder sql = new StringBuilder("""
                SELECT m.uuid_publico, e.id expediente_id, e.codigo_interno, e.numero_expediente,
                       c.nombre_busqueda cliente, o.nombre organo, ee.nombre estado_procesal,
                       u.nombre_completo abogado_responsable, m.fuente_plataforma,
                       m.estado estado_monitor, m.fecha_creacion, m.ultima_consulta,
                       (SELECT COUNT(*) FROM monitor_consultas mc
                         WHERE mc.monitor_id=m.id) total_consultas,
                       (SELECT COUNT(*) FROM monitor_consultas mc
                         WHERE mc.monitor_id=m.id AND mc.hubo_movimiento=1) movimientos,
                       (SELECT COUNT(*) FROM monitor_consultas mc
                         WHERE mc.monitor_id=m.id AND mc.estado_verificacion='PENDIENTE') pendientes_verificacion,
                       (SELECT COALESCE(mc.tipo_movimiento,
                               CASE WHEN mc.nueva_resolucion=1 THEN CONCAT('Resolución ', COALESCE(mc.numero_resolucion,'')) ELSE 'Consulta sin novedad' END)
                          FROM monitor_consultas mc
                         WHERE mc.monitor_id=m.id
                         ORDER BY mc.fecha_consulta DESC, mc.id DESC LIMIT 1) ultimo_movimiento,
                       (SELECT mc.fecha_consulta FROM monitor_consultas mc
                         WHERE mc.monitor_id=m.id AND mc.hubo_movimiento=1
                         ORDER BY mc.fecha_consulta DESC, mc.id DESC LIMIT 1) fecha_ultimo_movimiento
                  FROM monitor_expedientes m
                  JOIN expedientes e ON e.id=m.expediente_id
                  JOIN clientes c ON c.id=e.cliente_id
                  LEFT JOIN organos_jurisdiccionales o ON o.id=e.organo_id
                  LEFT JOIN estados_expediente ee ON ee.id=e.estado_id
                  LEFT JOIN usuarios u ON u.id=e.abogado_responsable_id
                 WHERE m.estudio_id=:estudioId
                   AND m.activo=1
                   AND e.activo=1
                """);
        MapSqlParameterSource p = base();
        aplicarVisibilidadExpediente(sql, p, verTodos, verConfidenciales);

        if (expediente != null && !expediente.isBlank()) {
            sql.append(" AND LOWER(CONCAT_WS(' ',e.codigo_interno,e.numero_expediente)) LIKE :expediente");
            p.addValue("expediente", "%" + expediente.trim().toLowerCase() + "%");
        }
        if (cliente != null && !cliente.isBlank()) {
            sql.append(" AND LOWER(c.nombre_busqueda) LIKE :cliente");
            p.addValue("cliente", "%" + cliente.trim().toLowerCase() + "%");
        }
        if (abogado != null && !abogado.isBlank()) {
            sql.append(" AND LOWER(COALESCE(u.nombre_completo,'')) LIKE :abogado");
            p.addValue("abogado", "%" + abogado.trim().toLowerCase() + "%");
        }
        if (organo != null && !organo.isBlank()) {
            sql.append(" AND LOWER(COALESCE(o.nombre,'')) LIKE :organo");
            p.addValue("organo", "%" + organo.trim().toLowerCase() + "%");
        }
        if (estadoProcesal != null && !estadoProcesal.isBlank()) {
            sql.append(" AND LOWER(COALESCE(ee.nombre,'')) LIKE :estadoProcesal");
            p.addValue("estadoProcesal", "%" + estadoProcesal.trim().toLowerCase() + "%");
        }
        if (desde != null || hasta != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM monitor_consultas f WHERE f.monitor_id=m.id");
            if (desde != null) {
                sql.append(" AND DATE(f.fecha_consulta)>=:desde");
                p.addValue("desde", desde);
            }
            if (hasta != null) {
                sql.append(" AND DATE(f.fecha_consulta)<=:hasta");
                p.addValue("hasta", hasta);
            }
            sql.append(")");
        }
        sql.append(" ORDER BY COALESCE(m.ultima_consulta,m.fecha_creacion) DESC, e.codigo_interno DESC");
        return jdbc.query(sql.toString(), p, (rs, n) -> mapMonitor(rs));
    }

    public List<Consulta> listarConsultas(Long expedienteId, String tipoMovimiento, String estadoVerificacion,
            LocalDate desde, LocalDate hasta, boolean verTodos, boolean verConfidenciales) {
        StringBuilder sql = new StringBuilder("""
                SELECT mc.uuid_publico, e.id expediente_id, e.codigo_interno, e.numero_expediente,
                       c.nombre_busqueda cliente, mc.fecha_consulta, us.id usuario_id,
                       us.nombre_completo usuario, mc.fuente_plataforma, mc.hubo_movimiento,
                       mc.tipo_movimiento, mc.nueva_resolucion, mc.numero_resolucion,
                       mc.tipo_resolucion, mc.fecha_resolucion, mc.fecha_notificacion,
                       mc.documento_descargado, mc.notificacion, mc.plazo, mc.observaciones,
                       mc.origen_informacion, mc.estado_verificacion,
                       uv.nombre_completo verificado_por, mc.fecha_verificacion
                  FROM monitor_consultas mc
                  JOIN monitor_expedientes m ON m.id=mc.monitor_id
                  JOIN expedientes e ON e.id=mc.expediente_id
                  JOIN clientes c ON c.id=e.cliente_id
                  JOIN usuarios us ON us.id=mc.usuario_id
                  LEFT JOIN usuarios uv ON uv.id=mc.usuario_verificacion_id
                 WHERE m.estudio_id=:estudioId
                   AND e.activo=1
                """);
        MapSqlParameterSource p = base();
        aplicarVisibilidadExpediente(sql, p, verTodos, verConfidenciales);
        if (expedienteId != null) {
            sql.append(" AND e.id=:expedienteId");
            p.addValue("expedienteId", expedienteId);
        }
        if (tipoMovimiento != null && !tipoMovimiento.isBlank()) {
            sql.append(" AND mc.tipo_movimiento=:tipoMovimiento");
            p.addValue("tipoMovimiento", tipoMovimiento.trim().toUpperCase());
        }
        if (estadoVerificacion != null && !estadoVerificacion.isBlank()) {
            sql.append(" AND mc.estado_verificacion=:estadoVerificacion");
            p.addValue("estadoVerificacion", estadoVerificacion.trim().toUpperCase());
        }
        if (desde != null) {
            sql.append(" AND DATE(mc.fecha_consulta)>=:desde");
            p.addValue("desde", desde);
        }
        if (hasta != null) {
            sql.append(" AND DATE(mc.fecha_consulta)<=:hasta");
            p.addValue("hasta", hasta);
        }
        sql.append(" ORDER BY mc.fecha_consulta DESC, mc.id DESC LIMIT 200");
        return jdbc.query(sql.toString(), p, (rs, n) -> mapConsulta(rs));
    }

    public Resumen resumen(boolean verTodos, boolean verConfidenciales) {
        StringBuilder filtro = new StringBuilder("""
                FROM monitor_expedientes m
                JOIN expedientes e ON e.id=m.expediente_id
               WHERE m.estudio_id=:estudioId AND m.activo=1 AND e.activo=1
                """);
        MapSqlParameterSource p = base();
        aplicarVisibilidadExpediente(filtro, p, verTodos, verConfidenciales);
        String condicion = filtro.toString();

        Long monitoreados = jdbc.queryForObject("SELECT COUNT(*) " + condicion, p, Long.class);
        Long movimientos = jdbc.queryForObject("""
                SELECT COUNT(*) FROM monitor_consultas mc
                JOIN monitor_expedientes m ON m.id=mc.monitor_id
                JOIN expedientes e ON e.id=m.expediente_id
                WHERE m.estudio_id=:estudioId AND m.activo=1 AND e.activo=1
                  AND mc.hubo_movimiento=1 AND DATE(mc.fecha_consulta)=CURRENT_DATE
                """ + visibilidadSql(verTodos, verConfidenciales), parametrosVisibilidad(p, verTodos), Long.class);
        Long resoluciones = jdbc.queryForObject("""
                SELECT COUNT(*) FROM monitor_consultas mc
                JOIN monitor_expedientes m ON m.id=mc.monitor_id
                JOIN expedientes e ON e.id=m.expediente_id
                WHERE m.estudio_id=:estudioId AND m.activo=1 AND e.activo=1
                  AND mc.nueva_resolucion=1 AND DATE(mc.fecha_consulta)=CURRENT_DATE
                """ + visibilidadSql(verTodos, verConfidenciales), parametrosVisibilidad(p, verTodos), Long.class);
        Long pendientes = jdbc.queryForObject("""
                SELECT COUNT(*) FROM monitor_consultas mc
                JOIN monitor_expedientes m ON m.id=mc.monitor_id
                JOIN expedientes e ON e.id=m.expediente_id
                WHERE m.estudio_id=:estudioId AND m.activo=1 AND e.activo=1
                  AND mc.estado_verificacion='PENDIENTE'
                """ + visibilidadSql(verTodos, verConfidenciales), parametrosVisibilidad(p, verTodos), Long.class);
        return new Resumen(nvl(monitoreados), nvl(movimientos), nvl(resoluciones), nvl(pendientes));
    }

    public Catalogos catalogos(boolean verTodos, boolean verConfidenciales) {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT e.id, e.codigo_interno, e.numero_expediente,
                       c.nombre_busqueda cliente, o.nombre organo,
                       u.nombre_completo abogado_responsable
                  FROM expedientes e
                  JOIN clientes c ON c.id=e.cliente_id
                  LEFT JOIN organos_jurisdiccionales o ON o.id=e.organo_id
                  LEFT JOIN usuarios u ON u.id=e.abogado_responsable_id
                 WHERE e.estudio_id=:estudioId AND e.activo=1
                """);
        MapSqlParameterSource p = base();
        aplicarVisibilidadExpedienteSinMonitor(sql, p, verTodos, verConfidenciales);
        sql.append(" ORDER BY e.codigo_interno DESC");
        List<ExpedienteOpcion> expedientes = jdbc.query(sql.toString(), p, (rs, n) -> new ExpedienteOpcion(
                rs.getLong("id"), rs.getString("codigo_interno"), rs.getString("numero_expediente"),
                rs.getString("cliente"), rs.getString("organo"), rs.getString("abogado_responsable")));
        return new Catalogos(expedientes);
    }

    public boolean expedienteVisible(Long expedienteId, boolean verTodos, boolean verConfidenciales) {
        if (expedienteId == null) return false;
        return catalogos(verTodos, verConfidenciales).expedientes().stream().anyMatch(e -> e.id().equals(expedienteId));
    }

    public String agregarExpediente(Long expedienteId, String fuentePlataforma) {
        MapSqlParameterSource p = base()
                .addValue("expedienteId", expedienteId)
                .addValue("fuente", fuentePlataforma.trim())
                .addValue("usuarioId", SesionActual.usuarioId());
        jdbc.update("""
                INSERT INTO monitor_expedientes(estudio_id, expediente_id, fuente_plataforma,
                    estado, usuario_creacion_id, activo)
                VALUES(:estudioId,:expedienteId,:fuente,'ACTIVO',:usuarioId,1)
                ON DUPLICATE KEY UPDATE
                    fuente_plataforma=VALUES(fuente_plataforma), estado='ACTIVO', activo=1,
                    usuario_modificacion_id=VALUES(usuario_creacion_id)
                """, p);
        return jdbc.queryForObject("""
                SELECT uuid_publico FROM monitor_expedientes
                 WHERE estudio_id=:estudioId AND expediente_id=:expedienteId
                """, p, String.class);
    }

    public String registrarConsulta(RegistrarConsultaRequest r, String origen, String estadoVerificacion) {
        Long monitorId = obtenerOCrearMonitor(r.expedienteId(), r.fuentePlataforma());
        Long resolucionId = null;
        if (r.nuevaResolucion()) {
            resolucionId = crearOEncontrarResolucion(r, origen, estadoVerificacion);
        }

        MapSqlParameterSource p = base()
                .addValue("monitorId", monitorId)
                .addValue("expedienteId", r.expedienteId())
                .addValue("usuarioId", SesionActual.usuarioId())
                .addValue("fuente", r.fuentePlataforma().trim())
                .addValue("huboMovimiento", r.huboMovimiento() || r.nuevaResolucion())
                .addValue("tipoMovimiento", textoMayus(r.tipoMovimiento()), Types.VARCHAR)
                .addValue("nuevaResolucion", r.nuevaResolucion())
                .addValue("numeroResolucion", texto(r.numeroResolucion()), Types.VARCHAR)
                .addValue("tipoResolucion", textoMayus(r.tipoResolucion()), Types.VARCHAR)
                .addValue("fechaResolucion", r.fechaResolucion(), Types.DATE)
                .addValue("fechaNotificacion", r.fechaNotificacion(), Types.DATE)
                .addValue("resolucionId", resolucionId, Types.BIGINT)
                .addValue("documento", texto(r.documentoDescargado()), Types.VARCHAR)
                .addValue("notificacion", texto(r.notificacion()), Types.VARCHAR)
                .addValue("plazo", texto(r.plazo()), Types.VARCHAR)
                .addValue("observaciones", texto(r.observaciones()), Types.LONGVARCHAR)
                .addValue("origen", origen)
                .addValue("verificacion", estadoVerificacion)
                .addValue("verificadorId", "VERIFICADA".equals(estadoVerificacion) ? SesionActual.usuarioId() : null, Types.BIGINT);

        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO monitor_consultas(
                    monitor_id, expediente_id, usuario_id, fuente_plataforma,
                    hubo_movimiento, tipo_movimiento, nueva_resolucion, numero_resolucion,
                    tipo_resolucion, fecha_resolucion, fecha_notificacion, resolucion_id,
                    documento_descargado, notificacion, plazo, observaciones,
                    origen_informacion, estado_verificacion, usuario_verificacion_id, fecha_verificacion)
                VALUES(
                    :monitorId,:expedienteId,:usuarioId,:fuente,
                    :huboMovimiento,:tipoMovimiento,:nuevaResolucion,:numeroResolucion,
                    :tipoResolucion,:fechaResolucion,:fechaNotificacion,:resolucionId,
                    :documento,:notificacion,:plazo,:observaciones,
                    :origen,:verificacion,:verificadorId,
                    CASE WHEN :verificacion='VERIFICADA' THEN CURRENT_TIMESTAMP(3) ELSE NULL END)
                """, p, key, new String[]{"id"});
        Number id = key.getKey();
        if (id == null) throw new IllegalStateException("No se obtuvo el id de la consulta judicial");

        jdbc.update("""
                UPDATE monitor_expedientes
                   SET ultima_consulta=CURRENT_TIMESTAMP(3), fuente_plataforma=:fuente,
                       usuario_modificacion_id=:usuarioId
                 WHERE id=:monitorId AND estudio_id=:estudioId
                """, p);
        return jdbc.queryForObject("SELECT uuid_publico FROM monitor_consultas WHERE id=:id",
                Map.of("id", id.longValue()), String.class);
    }

    public boolean verificarConsulta(String uuid, String estado) {
        MapSqlParameterSource p = base().addValue("uuid", uuid).addValue("estado", estado)
                .addValue("usuarioId", SesionActual.usuarioId());
        int filas = jdbc.update("""
                UPDATE monitor_consultas mc
                JOIN monitor_expedientes m ON m.id=mc.monitor_id
                   SET mc.estado_verificacion=:estado,
                       mc.usuario_verificacion_id=:usuarioId,
                       mc.fecha_verificacion=CURRENT_TIMESTAMP(3)
                 WHERE mc.uuid_publico=:uuid
                   AND m.estudio_id=:estudioId
                """, p);
        if (filas > 0) {
            jdbc.update("""
                    UPDATE resoluciones r
                    JOIN monitor_consultas mc ON mc.resolucion_id=r.id
                    JOIN monitor_expedientes m ON m.id=mc.monitor_id
                       SET r.estado_verificacion=:estado,
                           r.usuario_verificacion_id=:usuarioId,
                           r.fecha_verificacion=CURRENT_TIMESTAMP(3)
                     WHERE mc.uuid_publico=:uuid
                       AND m.estudio_id=:estudioId
                    """, p);
        }
        return filas > 0;
    }

    public boolean desactivar(String uuid) {
        return jdbc.update("""
                UPDATE monitor_expedientes
                   SET activo=0, estado='CERRADO', usuario_modificacion_id=:usuarioId
                 WHERE uuid_publico=:uuid AND estudio_id=:estudioId AND activo=1
                """, base().addValue("uuid", uuid).addValue("usuarioId", SesionActual.usuarioId())) > 0;
    }

    private Long obtenerOCrearMonitor(Long expedienteId, String fuente) {
        MapSqlParameterSource p = base().addValue("expedienteId", expedienteId)
                .addValue("fuente", fuente.trim()).addValue("usuarioId", SesionActual.usuarioId());
        jdbc.update("""
                INSERT INTO monitor_expedientes(estudio_id, expediente_id, fuente_plataforma,
                    estado, usuario_creacion_id, activo)
                VALUES(:estudioId,:expedienteId,:fuente,'ACTIVO',:usuarioId,1)
                ON DUPLICATE KEY UPDATE
                    fuente_plataforma=VALUES(fuente_plataforma), estado='ACTIVO', activo=1,
                    usuario_modificacion_id=VALUES(usuario_creacion_id)
                """, p);
        return jdbc.queryForObject("""
                SELECT id FROM monitor_expedientes
                 WHERE estudio_id=:estudioId AND expediente_id=:expedienteId
                """, p, Long.class);
    }

    private Long crearOEncontrarResolucion(RegistrarConsultaRequest r, String origen, String estadoVerificacion) {
        MapSqlParameterSource p = base()
                .addValue("expedienteId", r.expedienteId())
                .addValue("numero", r.numeroResolucion().trim())
                .addValue("tipo", textoMayus(r.tipoResolucion()), Types.VARCHAR)
                .addValue("fechaResolucion", r.fechaResolucion(), Types.DATE)
                .addValue("fechaNotificacion", r.fechaNotificacion())
                .addValue("fuente", r.fuentePlataforma().trim())
                .addValue("estadoVerificacion", estadoVerificacion)
                .addValue("verificador", "VERIFICADA".equals(estadoVerificacion) ? SesionActual.usuarioId() : null, Types.BIGINT)
                .addValue("creador", SesionActual.usuarioId());
        jdbc.update("""
                INSERT INTO resoluciones(expediente_id, numero_resolucion, tipo_resolucion,
                    fecha_resolucion, fecha_notificacion, sumilla, fuente, origen,
                    estado_verificacion, usuario_verificacion_id, fecha_verificacion,
                    usuario_creacion_id)
                SELECT :expedienteId,:numero,:tipo,:fechaResolucion,:fechaNotificacion,NULL,
                       :fuente,'MONITOR_JUDICIAL',:estadoVerificacion,:verificador,
                       CASE WHEN :estadoVerificacion='VERIFICADA' THEN CURRENT_TIMESTAMP(3) ELSE NULL END,
                       :creador
                 WHERE NOT EXISTS (
                    SELECT 1 FROM resoluciones r
                     WHERE r.expediente_id=:expedienteId AND r.numero_resolucion=:numero)
                """, p);
        return jdbc.queryForObject("""
                SELECT id FROM resoluciones
                 WHERE expediente_id=:expedienteId AND numero_resolucion=:numero
                 ORDER BY id DESC LIMIT 1
                """, p, Long.class);
    }

    private void aplicarVisibilidadExpediente(StringBuilder sql, MapSqlParameterSource p,
            boolean verTodos, boolean verConfidenciales) {
        if (SesionActual.esPortalCliente()) {
            sql.append(" AND e.cliente_id=:clienteId");
            p.addValue("clienteId", SesionActual.clienteId(), Types.BIGINT);
        } else if (!verTodos) {
            sql.append(" AND (e.abogado_responsable_id=:usuarioId OR EXISTS (SELECT 1 FROM expediente_equipo eq WHERE eq.expediente_id=e.id AND eq.usuario_id=:usuarioId AND eq.activo=1))");
            p.addValue("usuarioId", SesionActual.usuarioId());
        }
        if (!verConfidenciales) sql.append(" AND e.es_confidencial=0");
    }

    private void aplicarVisibilidadExpedienteSinMonitor(StringBuilder sql, MapSqlParameterSource p,
            boolean verTodos, boolean verConfidenciales) {
        aplicarVisibilidadExpediente(sql, p, verTodos, verConfidenciales);
    }

    private String visibilidadSql(boolean verTodos, boolean verConfidenciales) {
        StringBuilder s = new StringBuilder();
        if (SesionActual.esPortalCliente()) {
            s.append(" AND e.cliente_id=:clienteId");
        } else if (!verTodos) {
            s.append(" AND (e.abogado_responsable_id=:usuarioId OR EXISTS (SELECT 1 FROM expediente_equipo eq WHERE eq.expediente_id=e.id AND eq.usuario_id=:usuarioId AND eq.activo=1))");
        }
        if (!verConfidenciales) s.append(" AND e.es_confidencial=0");
        return s.toString();
    }

    private MapSqlParameterSource parametrosVisibilidad(MapSqlParameterSource original, boolean verTodos) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        for (String name : original.getParameterNames()) p.addValue(name, original.getValue(name));
        if (SesionActual.esPortalCliente()) p.addValue("clienteId", SesionActual.clienteId(), Types.BIGINT);
        else if (!verTodos) p.addValue("usuarioId", SesionActual.usuarioId());
        return p;
    }

    private ExpedienteMonitor mapMonitor(ResultSet rs) throws SQLException {
        return new ExpedienteMonitor(
                rs.getString("uuid_publico"), rs.getLong("expediente_id"), rs.getString("codigo_interno"),
                rs.getString("numero_expediente"), rs.getString("cliente"), rs.getString("organo"),
                rs.getString("estado_procesal"), rs.getString("abogado_responsable"), rs.getString("fuente_plataforma"),
                rs.getString("estado_monitor"), ts(rs, "fecha_creacion"), ts(rs, "ultima_consulta"),
                rs.getLong("total_consultas"), rs.getLong("movimientos"), rs.getLong("pendientes_verificacion"),
                rs.getString("ultimo_movimiento"), ts(rs, "fecha_ultimo_movimiento"));
    }

    private Consulta mapConsulta(ResultSet rs) throws SQLException {
        return new Consulta(
                rs.getString("uuid_publico"), rs.getLong("expediente_id"), rs.getString("codigo_interno"),
                rs.getString("numero_expediente"), rs.getString("cliente"), ts(rs, "fecha_consulta"),
                rs.getLong("usuario_id"), rs.getString("usuario"), rs.getString("fuente_plataforma"),
                rs.getBoolean("hubo_movimiento"), rs.getString("tipo_movimiento"), rs.getBoolean("nueva_resolucion"),
                rs.getString("numero_resolucion"), rs.getString("tipo_resolucion"), date(rs, "fecha_resolucion"),
                date(rs, "fecha_notificacion"), rs.getString("documento_descargado"), rs.getString("notificacion"),
                rs.getString("plazo"), rs.getString("observaciones"), rs.getString("origen_informacion"),
                rs.getString("estado_verificacion"), rs.getString("verificado_por"), ts(rs, "fecha_verificacion"));
    }

    private MapSqlParameterSource base() {
        return new MapSqlParameterSource().addValue("estudioId", SesionActual.estudioId());
    }

    private static long nvl(Long v) {
        return v == null ? 0 : v;
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String textoMayus(String s) {
        String t = texto(s);
        return t == null ? null : t.toUpperCase();
    }

    private static java.time.LocalDateTime ts(ResultSet rs, String c) throws SQLException {
        var t = rs.getTimestamp(c);
        return t == null ? null : t.toLocalDateTime();
    }

    private static LocalDate date(ResultSet rs, String c) throws SQLException {
        var d = rs.getDate(c);
        return d == null ? null : d.toLocalDate();
    }
}
