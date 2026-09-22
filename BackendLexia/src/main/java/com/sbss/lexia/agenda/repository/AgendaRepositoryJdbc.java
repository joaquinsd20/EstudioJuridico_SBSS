package com.sbss.lexia.agenda.repository;

import com.sbss.lexia.agenda.dto.AgendaDtos.Catalogos;
import com.sbss.lexia.agenda.dto.AgendaDtos.EventoLista;
import com.sbss.lexia.agenda.dto.AgendaDtos.ExpedienteOpcion;
import com.sbss.lexia.agenda.dto.AgendaDtos.GuardarRequest;
import com.sbss.lexia.agenda.dto.AgendaDtos.ActualizarRequest;
import com.sbss.lexia.agenda.dto.AgendaDtos.Participante;
import com.sbss.lexia.agenda.dto.AgendaDtos.Reprogramacion;
import com.sbss.lexia.agenda.dto.AgendaDtos.UsuarioOpcion;
import com.sbss.lexia.shared.security.SesionActual;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
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
public class AgendaRepositoryJdbc {

    private final NamedParameterJdbcTemplate jdbc;

    public List<EventoLista> listar(
            LocalDateTime desde,
            LocalDateTime hasta,
            String texto,
            String tipo,
            String estado,
            Long expedienteId,
            Long participanteId,
            boolean verTodos,
            boolean verConfidenciales) {

        StringBuilder sql = new StringBuilder("""
                SELECT ev.id, ev.uuid_publico, ev.tipo, ev.titulo, ev.descripcion,
                       ev.fecha_inicio, ev.fecha_fin, ev.todo_el_dia, ev.modalidad,
                       ev.ubicacion, ev.enlace_virtual, ev.estado, ev.resultado,
                       ev.recordatorio_minutos, ev.color_hex, ev.origen_informacion,
                       ev.requiere_verificacion,
                       e.id expediente_id, e.codigo_interno expediente_codigo,
                       e.numero_expediente, c.nombre_busqueda cliente_nombre,
                       uc.id creador_id, uc.nombre_completo creador,
                       (SELECT COUNT(*) FROM evento_participantes epc WHERE epc.evento_id = ev.id) participantes
                  FROM eventos_agenda ev
                  LEFT JOIN expedientes e ON e.id = ev.expediente_id
                  LEFT JOIN clientes c ON c.id = e.cliente_id
                  LEFT JOIN usuarios uc ON uc.id = ev.usuario_creacion_id
                 WHERE ev.estudio_id = :estudioId
                   AND ev.activo = 1
                   AND ev.fecha_inicio <= :hasta
                   AND ev.fecha_fin >= :desde
                """);

        MapSqlParameterSource p = base()
                .addValue("desde", desde)
                .addValue("hasta", hasta);

        aplicarVisibilidad(sql, p, verTodos, verConfidenciales);

        if (texto != null && !texto.isBlank()) {
            sql.append(" AND LOWER(CONCAT_WS(' ', ev.titulo, ev.descripcion, ev.ubicacion, e.codigo_interno, e.numero_expediente, c.nombre_busqueda)) LIKE :texto");
            p.addValue("texto", "%" + texto.trim().toLowerCase() + "%");
        }
        if (tipo != null && !tipo.isBlank()) {
            sql.append(" AND ev.tipo = :tipo");
            p.addValue("tipo", tipo.trim().toUpperCase());
        }
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND ev.estado = :estado");
            p.addValue("estado", estado.trim().toUpperCase());
        }
        if (expedienteId != null) {
            sql.append(" AND ev.expediente_id = :expedienteId");
            p.addValue("expedienteId", expedienteId);
        }
        if (participanteId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM evento_participantes ep WHERE ep.evento_id=ev.id AND ep.usuario_id=:participanteId)");
            p.addValue("participanteId", participanteId);
        }

        sql.append(" ORDER BY ev.fecha_inicio, ev.id");
        return jdbc.query(sql.toString(), p, (rs, n) -> mapEvento(rs));
    }

    public EventoLista obtener(String uuid, boolean verTodos, boolean verConfidenciales) {
        StringBuilder sql = new StringBuilder("""
                SELECT ev.id, ev.uuid_publico, ev.tipo, ev.titulo, ev.descripcion,
                       ev.fecha_inicio, ev.fecha_fin, ev.todo_el_dia, ev.modalidad,
                       ev.ubicacion, ev.enlace_virtual, ev.estado, ev.resultado,
                       ev.recordatorio_minutos, ev.color_hex, ev.origen_informacion,
                       ev.requiere_verificacion,
                       e.id expediente_id, e.codigo_interno expediente_codigo,
                       e.numero_expediente, c.nombre_busqueda cliente_nombre,
                       uc.id creador_id, uc.nombre_completo creador,
                       (SELECT COUNT(*) FROM evento_participantes epc WHERE epc.evento_id = ev.id) participantes
                  FROM eventos_agenda ev
                  LEFT JOIN expedientes e ON e.id = ev.expediente_id
                  LEFT JOIN clientes c ON c.id = e.cliente_id
                  LEFT JOIN usuarios uc ON uc.id = ev.usuario_creacion_id
                 WHERE ev.estudio_id = :estudioId
                   AND ev.activo = 1
                   AND ev.uuid_publico = :uuid
                """);
        MapSqlParameterSource p = base().addValue("uuid", uuid);
        aplicarVisibilidad(sql, p, verTodos, verConfidenciales);
        List<EventoLista> r = jdbc.query(sql.toString(), p, (rs, n) -> mapEvento(rs));
        return r.isEmpty() ? null : r.getFirst();
    }

    public List<Participante> participantes(String uuid) {
        return jdbc.query("""
                SELECT u.id, u.nombre_completo, u.cargo, ep.tipo_participacion, ep.confirmado
                  FROM evento_participantes ep
                  JOIN eventos_agenda ev ON ev.id = ep.evento_id
                  JOIN usuarios u ON u.id = ep.usuario_id
                 WHERE ev.uuid_publico = :uuid
                   AND ev.estudio_id = :estudioId
                 ORDER BY FIELD(ep.tipo_participacion,'ORGANIZADOR','ASISTENTE','OPCIONAL'), u.nombre_completo
                """, base().addValue("uuid", uuid),
                (rs, n) -> new Participante(
                        rs.getLong("id"), rs.getString("nombre_completo"), rs.getString("cargo"),
                        rs.getString("tipo_participacion"), rs.getBoolean("confirmado")));
    }

    public List<Reprogramacion> reprogramaciones(String uuid) {
        return jdbc.query("""
                SELECT er.id, er.fecha_inicio_anterior, er.fecha_fin_anterior,
                       er.fecha_inicio_nueva, er.fecha_fin_nueva, er.motivo,
                       u.nombre_completo usuario, er.fecha_reprogramacion
                  FROM evento_reprogramaciones er
                  JOIN eventos_agenda ev ON ev.id = er.evento_id
                  LEFT JOIN usuarios u ON u.id = er.usuario_id
                 WHERE ev.uuid_publico = :uuid
                   AND ev.estudio_id = :estudioId
                 ORDER BY er.fecha_reprogramacion DESC
                """, base().addValue("uuid", uuid),
                (rs, n) -> new Reprogramacion(
                        rs.getLong("id"),
                        rs.getTimestamp("fecha_inicio_anterior").toLocalDateTime(),
                        rs.getTimestamp("fecha_fin_anterior").toLocalDateTime(),
                        rs.getTimestamp("fecha_inicio_nueva").toLocalDateTime(),
                        rs.getTimestamp("fecha_fin_nueva").toLocalDateTime(),
                        rs.getString("motivo"), rs.getString("usuario"),
                        rs.getTimestamp("fecha_reprogramacion").toLocalDateTime()));
    }

    public Catalogos catalogos(boolean verTodos, boolean verConfidenciales) {
        return new Catalogos(expedientesVisibles(verTodos, verConfidenciales), usuarios());
    }

    public boolean expedienteVisible(Long expedienteId, boolean verTodos, boolean verConfidenciales) {
        if (expedienteId == null) return true;
        return expedientesVisibles(verTodos, verConfidenciales).stream().anyMatch(e -> e.id().equals(expedienteId));
    }

    public boolean usuariosValidos(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return true;
        Integer cantidad = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM usuarios
                 WHERE estudio_id=:estudioId
                   AND activo=1
                   AND estado='ACTIVO'
                   AND tipo_usuario='INTERNO'
                   AND id IN (:ids)
                """, base().addValue("ids", ids.stream().distinct().toList()), Integer.class);
        return cantidad != null && cantidad == ids.stream().distinct().count();
    }

    public String crear(GuardarRequest r, String color) {
        MapSqlParameterSource p = parametrosComunes(r.expedienteId(), r.tipo(), r.titulo(), r.descripcion(),
                r.fechaInicio(), r.fechaFin(), r.todoElDia(), r.modalidad(), r.ubicacion(), r.enlaceVirtual(),
                r.recordatorioMinutos(), color, r.origenInformacion(), r.requiereVerificacion());
        p.addValue("creadorId", SesionActual.usuarioId());

        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO eventos_agenda(
                    estudio_id, expediente_id, tipo, titulo, descripcion, fecha_inicio, fecha_fin,
                    todo_el_dia, modalidad, ubicacion, enlace_virtual, estado, recordatorio_minutos,
                    color_hex, origen_informacion, requiere_verificacion, usuario_creacion_id)
                VALUES(
                    :estudioId, :expedienteId, :tipo, :titulo, :descripcion, :fechaInicio, :fechaFin,
                    :todoElDia, :modalidad, :ubicacion, :enlaceVirtual, 'PROGRAMADO', :recordatorio,
                    :color, :origen, :verificacion, :creadorId)
                """, p, key, new String[]{"id"});
        Number n = key.getKey();
        if (n == null) throw new IllegalStateException("No se obtuvo el id del evento");
        Long id = n.longValue();
        guardarParticipantes(id, r.participanteIds());
        return uuidPorId(id);
    }

    public boolean actualizar(String uuid, ActualizarRequest r, String color) {
        Long id = idPorUuid(uuid);
        if (id == null) return false;
        MapSqlParameterSource p = parametrosComunes(r.expedienteId(), r.tipo(), r.titulo(), r.descripcion(),
                r.fechaInicio(), r.fechaFin(), r.todoElDia(), r.modalidad(), r.ubicacion(), r.enlaceVirtual(),
                r.recordatorioMinutos(), color, r.origenInformacion(), r.requiereVerificacion())
                .addValue("estado", r.estado().trim().toUpperCase())
                .addValue("resultado", texto(r.resultado()), Types.LONGVARCHAR)
                .addValue("modificadorId", SesionActual.usuarioId())
                .addValue("id", id);

        int filas = jdbc.update("""
                UPDATE eventos_agenda
                   SET expediente_id=:expedienteId, tipo=:tipo, titulo=:titulo, descripcion=:descripcion,
                       fecha_inicio=:fechaInicio, fecha_fin=:fechaFin, todo_el_dia=:todoElDia,
                       modalidad=:modalidad, ubicacion=:ubicacion, enlace_virtual=:enlaceVirtual,
                       estado=:estado, resultado=:resultado, recordatorio_minutos=:recordatorio,
                       color_hex=:color, origen_informacion=:origen, requiere_verificacion=:verificacion,
                       usuario_modificacion_id=:modificadorId
                 WHERE id=:id AND estudio_id=:estudioId AND activo=1
                """, p);
        if (filas > 0) guardarParticipantes(id, r.participanteIds());
        return filas > 0;
    }

    public boolean reprogramar(String uuid, LocalDateTime nuevoInicio, LocalDateTime nuevoFin,
            String motivo, Integer recordatorio) {
        EventoFechas actual = fechas(uuid);
        if (actual == null) return false;
        MapSqlParameterSource p = base()
                .addValue("eventoId", actual.id())
                .addValue("inicioAnterior", actual.inicio())
                .addValue("finAnterior", actual.fin())
                .addValue("inicioNuevo", nuevoInicio)
                .addValue("finNuevo", nuevoFin)
                .addValue("motivo", motivo.trim())
                .addValue("usuarioId", SesionActual.usuarioId())
                .addValue("recordatorio", recordatorio, Types.INTEGER);
        jdbc.update("""
                INSERT INTO evento_reprogramaciones(evento_id, fecha_inicio_anterior, fecha_fin_anterior,
                    fecha_inicio_nueva, fecha_fin_nueva, motivo, usuario_id)
                VALUES(:eventoId,:inicioAnterior,:finAnterior,:inicioNuevo,:finNuevo,:motivo,:usuarioId)
                """, p);
        return jdbc.update("""
                UPDATE eventos_agenda
                   SET fecha_inicio=:inicioNuevo, fecha_fin=:finNuevo, estado='REPROGRAMADO',
                       recordatorio_minutos=COALESCE(:recordatorio, recordatorio_minutos),
                       usuario_modificacion_id=:usuarioId
                 WHERE id=:eventoId AND estudio_id=:estudioId AND activo=1
                """, p) > 0;
    }

    public boolean registrarResultado(String uuid, String resultado, boolean verificado) {
        return jdbc.update("""
                UPDATE eventos_agenda
                   SET resultado=:resultado, estado='REALIZADO',
                       requiere_verificacion=:requiere,
                       usuario_modificacion_id=:usuarioId
                 WHERE uuid_publico=:uuid AND estudio_id=:estudioId AND activo=1
                """, base().addValue("uuid", uuid)
                .addValue("resultado", resultado.trim())
                .addValue("requiere", !verificado)
                .addValue("usuarioId", SesionActual.usuarioId())) > 0;
    }

    public boolean cancelar(String uuid) {
        return jdbc.update("""
                UPDATE eventos_agenda
                   SET estado='CANCELADO', usuario_modificacion_id=:usuarioId
                 WHERE uuid_publico=:uuid AND estudio_id=:estudioId AND activo=1
                """, base().addValue("uuid", uuid).addValue("usuarioId", SesionActual.usuarioId())) > 0;
    }

    private List<ExpedienteOpcion> expedientesVisibles(boolean verTodos, boolean verConfidenciales) {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT e.id, e.codigo_interno, e.numero_expediente, c.nombre_busqueda
                  FROM expedientes e
                  JOIN clientes c ON c.id=e.cliente_id
                """);
        MapSqlParameterSource p = base();
        if (!verTodos && !SesionActual.esPortalCliente()) {
            sql.append(" LEFT JOIN expediente_equipo eq ON eq.expediente_id=e.id AND eq.usuario_id=:usuarioId AND eq.activo=1");
            p.addValue("usuarioId", SesionActual.usuarioId());
        }
        sql.append(" WHERE e.estudio_id=:estudioId AND e.activo=1");
        if (SesionActual.esPortalCliente()) {
            sql.append(" AND e.cliente_id=:clienteId");
            p.addValue("clienteId", SesionActual.clienteId(), Types.BIGINT);
        } else if (!verTodos) {
            sql.append(" AND (e.abogado_responsable_id=:usuarioId OR eq.usuario_id IS NOT NULL)");
        }
        if (!verConfidenciales) sql.append(" AND e.es_confidencial=0");
        sql.append(" ORDER BY e.codigo_interno DESC");
        return jdbc.query(sql.toString(), p, (rs, n) -> new ExpedienteOpcion(
                rs.getLong("id"), rs.getString("codigo_interno"), rs.getString("numero_expediente"),
                rs.getString("nombre_busqueda")));
    }

    private List<UsuarioOpcion> usuarios() {
        if (SesionActual.esPortalCliente()) return List.of();
        return jdbc.query("""
                SELECT id, nombre_completo, cargo
                  FROM usuarios
                 WHERE estudio_id=:estudioId AND activo=1 AND estado='ACTIVO' AND tipo_usuario='INTERNO'
                 ORDER BY nombre_completo
                """, base(), (rs, n) -> new UsuarioOpcion(rs.getLong("id"), rs.getString("nombre_completo"), rs.getString("cargo")));
    }

    private void guardarParticipantes(Long eventoId, List<Long> seleccionados) {
        Long organizadorId = jdbc.queryForObject(
                "SELECT COALESCE(usuario_creacion_id, :actual) FROM eventos_agenda WHERE id=:eventoId",
                Map.of("eventoId", eventoId, "actual", SesionActual.usuarioId()), Long.class);
        if (organizadorId == null) organizadorId = SesionActual.usuarioId();

        jdbc.update("DELETE FROM evento_participantes WHERE evento_id=:eventoId", Map.of("eventoId", eventoId));
        jdbc.update("""
                INSERT INTO evento_participantes(evento_id, usuario_id, tipo_participacion, confirmado, fecha_confirmacion)
                VALUES(:eventoId,:usuarioId,'ORGANIZADOR',1,CURRENT_TIMESTAMP(3))
                """, Map.of("eventoId", eventoId, "usuarioId", organizadorId));
        if (seleccionados == null) return;
        final Long organizador = organizadorId;
        for (Long id : seleccionados.stream().filter(i -> i != null && !i.equals(organizador)).distinct().toList()) {
            jdbc.update("""
                    INSERT INTO evento_participantes(evento_id, usuario_id, tipo_participacion, confirmado)
                    VALUES(:eventoId,:usuarioId,'ASISTENTE',0)
                    """, Map.of("eventoId", eventoId, "usuarioId", id));
        }
    }

    private Long idPorUuid(String uuid) {
        List<Long> ids = jdbc.query("""
                SELECT id FROM eventos_agenda
                 WHERE uuid_publico=:uuid AND estudio_id=:estudioId AND activo=1
                """, base().addValue("uuid", uuid), (rs, n) -> rs.getLong(1));
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private EventoFechas fechas(String uuid) {
        List<EventoFechas> r = jdbc.query("""
                SELECT id, fecha_inicio, fecha_fin FROM eventos_agenda
                 WHERE uuid_publico=:uuid AND estudio_id=:estudioId AND activo=1
                """, base().addValue("uuid", uuid), (rs, n) -> new EventoFechas(
                rs.getLong("id"), rs.getTimestamp("fecha_inicio").toLocalDateTime(),
                rs.getTimestamp("fecha_fin").toLocalDateTime()));
        return r.isEmpty() ? null : r.getFirst();
    }

    private String uuidPorId(Long id) {
        return jdbc.queryForObject("SELECT uuid_publico FROM eventos_agenda WHERE id=:id", Map.of("id", id), String.class);
    }

    private MapSqlParameterSource parametrosComunes(Long expedienteId, String tipo, String titulo, String descripcion,
            LocalDateTime inicio, LocalDateTime fin, boolean todoDia, String modalidad, String ubicacion,
            String enlace, Integer recordatorio, String color, String origen, boolean verificacion) {
        return base()
                .addValue("expedienteId", expedienteId, Types.BIGINT)
                .addValue("tipo", tipo.trim().toUpperCase())
                .addValue("titulo", titulo.trim())
                .addValue("descripcion", texto(descripcion), Types.LONGVARCHAR)
                .addValue("fechaInicio", inicio)
                .addValue("fechaFin", fin)
                .addValue("todoElDia", todoDia)
                .addValue("modalidad", modalidad.trim().toUpperCase())
                .addValue("ubicacion", texto(ubicacion), Types.VARCHAR)
                .addValue("enlaceVirtual", texto(enlace), Types.VARCHAR)
                .addValue("recordatorio", recordatorio, Types.INTEGER)
                .addValue("color", color, Types.CHAR)
                .addValue("origen", origen.trim().toUpperCase())
                .addValue("verificacion", verificacion);
    }

    private void aplicarVisibilidad(StringBuilder sql, MapSqlParameterSource p,
            boolean verTodos, boolean verConfidenciales) {
        if (SesionActual.esPortalCliente()) {
            sql.append(" AND ev.expediente_id IS NOT NULL AND e.cliente_id=:clienteId");
            p.addValue("clienteId", SesionActual.clienteId(), Types.BIGINT);
        } else if (!verTodos) {
            sql.append("""
                     AND (
                       (ev.expediente_id IS NULL AND (
                           ev.usuario_creacion_id=:usuarioId
                           OR EXISTS (SELECT 1 FROM evento_participantes eps WHERE eps.evento_id=ev.id AND eps.usuario_id=:usuarioId)
                       ))
                       OR
                       (ev.expediente_id IS NOT NULL AND (
                           e.abogado_responsable_id=:usuarioId
                           OR EXISTS (SELECT 1 FROM expediente_equipo eqs
                                      WHERE eqs.expediente_id=e.id AND eqs.usuario_id=:usuarioId AND eqs.activo=1)
                       ))
                     )
                    """);
            p.addValue("usuarioId", SesionActual.usuarioId());
        }
        if (!verConfidenciales) sql.append(" AND (ev.expediente_id IS NULL OR e.es_confidencial=0)");
    }

    private EventoLista mapEvento(ResultSet rs) throws SQLException {
        return new EventoLista(
                rs.getString("uuid_publico"), rs.getString("tipo"), rs.getString("titulo"), rs.getString("descripcion"),
                rs.getTimestamp("fecha_inicio").toLocalDateTime(), rs.getTimestamp("fecha_fin").toLocalDateTime(),
                rs.getBoolean("todo_el_dia"), rs.getString("modalidad"), rs.getString("ubicacion"),
                rs.getString("enlace_virtual"), rs.getString("estado"), rs.getString("resultado"),
                getInteger(rs, "recordatorio_minutos"), rs.getString("color_hex"), rs.getString("origen_informacion"),
                rs.getBoolean("requiere_verificacion"), getLong(rs, "expediente_id"), rs.getString("expediente_codigo"),
                rs.getString("numero_expediente"), rs.getString("cliente_nombre"), getLong(rs, "creador_id"),
                rs.getString("creador"), rs.getInt("participantes"));
    }

    private MapSqlParameterSource base() {
        return new MapSqlParameterSource().addValue("estudioId", SesionActual.estudioId());
    }

    private static Long getLong(ResultSet rs, String c) throws SQLException {
        long v = rs.getLong(c);
        return rs.wasNull() ? null : v;
    }

    private static Integer getInteger(ResultSet rs, String c) throws SQLException {
        int v = rs.getInt(c);
        return rs.wasNull() ? null : v;
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private record EventoFechas(Long id, LocalDateTime inicio, LocalDateTime fin) {
    }
}
