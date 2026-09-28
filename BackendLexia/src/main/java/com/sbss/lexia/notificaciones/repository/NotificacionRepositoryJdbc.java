package com.sbss.lexia.notificaciones.repository;

import com.sbss.lexia.notificaciones.dto.NotificacionDtos.Bandeja;
import com.sbss.lexia.notificaciones.dto.NotificacionDtos.CrearNotificacion;
import com.sbss.lexia.notificaciones.dto.NotificacionDtos.Notificacion;
import com.sbss.lexia.shared.security.SesionActual;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificacionRepositoryJdbc {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String SELECT = """
            SELECT n.id, n.tipo, n.titulo, n.mensaje, n.severidad, n.entidad_origen,
                   n.registro_id, n.expediente_id, e.codigo_interno, n.url_destino,
                   n.leida, n.fecha_creacion
              FROM notificaciones n
              LEFT JOIN expedientes e ON e.id = n.expediente_id
             WHERE n.usuario_id = :usuarioId
             ORDER BY n.leida ASC, n.fecha_creacion DESC
             LIMIT :limite
            """;

    public Bandeja listar(int limite, boolean soloNoLeidas) {
        String sql = soloNoLeidas
                ? SELECT.replace("WHERE n.usuario_id = :usuarioId",
                                 "WHERE n.usuario_id = :usuarioId AND n.leida = 0")
                : SELECT;

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("usuarioId", SesionActual.usuarioId())
                .addValue("limite", limite);

        List<Notificacion> items = jdbc.query(sql, p, this::mapear);
        long noLeidas = contarNoLeidas();
        return new Bandeja(noLeidas, items);
    }

    private long contarNoLeidas() {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM notificaciones
                 WHERE usuario_id = :usuarioId AND leida = 0
                """, new MapSqlParameterSource("usuarioId", SesionActual.usuarioId()), Long.class);
        return n == null ? 0L : n;
    }

    public Notificacion marcarLeida(long id) {
        // El `usuario_id` en el WHERE no es sobra: sin el, cualquiera podria
        // marcar como leida la notificacion de otro usuario que adivinara.
        jdbc.update("""
                UPDATE notificaciones SET leida = 1, fecha_lectura = NOW(3)
                 WHERE id = :id AND usuario_id = :usuarioId
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("usuarioId", SesionActual.usuarioId()));

        // MySQL no tiene UPDATE ... RETURNING, asi que se relee la fila.
        String sql = SELECT.replace("WHERE n.usuario_id = :usuarioId",
                                    "WHERE n.usuario_id = :usuarioId AND n.id = :id");
        List<Notificacion> filas = jdbc.query(sql, new MapSqlParameterSource()
                .addValue("usuarioId", SesionActual.usuarioId())
                .addValue("id", id)
                .addValue("limite", 1), this::mapear);

        return filas.isEmpty() ? null : filas.get(0);
    }

    public long marcarTodasLeidas() {
        return jdbc.update("""
                UPDATE notificaciones SET leida = 1, fecha_lectura = NOW(3)
                 WHERE usuario_id = :usuarioId AND leida = 0
                """, new MapSqlParameterSource("usuarioId", SesionActual.usuarioId()));
    }

    public long eliminarLeidas() {
        return jdbc.update("""
                DELETE FROM notificaciones
                 WHERE usuario_id = :usuarioId AND leida = 1
                """, new MapSqlParameterSource("usuarioId", SesionActual.usuarioId()));
    }

    /**
     * Inserta una notificacion. La llama el sistema, nunca un endpoint publico.
     */
    public long crear(CrearNotificacion datos) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("usuarioId", datos.usuarioId())
                .addValue("tipo", datos.tipo())
                .addValue("titulo", datos.titulo())
                .addValue("mensaje", datos.mensaje())
                .addValue("severidad", datos.severidad() == null ? "INFO" : datos.severidad())
                .addValue("entidadOrigen", datos.entidadOrigen())
                .addValue("registroId", datos.registroId())
                .addValue("expedienteId", datos.expedienteId())
                .addValue("urlDestino", datos.urlDestino());

        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO notificaciones
                       (usuario_id, tipo, titulo, mensaje, severidad, entidad_origen,
                        registro_id, expediente_id, url_destino)
                VALUES (:usuarioId, :tipo, :titulo, :mensaje, :severidad, :entidadOrigen,
                        :registroId, :expedienteId, :urlDestino)
                """, p, kh, new String[]{"id"});

        return kh.getKey() == null ? 0L : kh.getKey().longValue();
    }

    private Notificacion mapear(ResultSet rs, int i) throws SQLException {
        return new Notificacion(
                rs.getLong("id"),
                rs.getString("tipo"),
                rs.getString("titulo"),
                rs.getString("mensaje"),
                rs.getString("severidad"),
                rs.getString("entidad_origen"),
                rs.getObject("registro_id") == null ? null : rs.getLong("registro_id"),
                rs.getObject("expediente_id") == null ? null : rs.getLong("expediente_id"),
                rs.getString("codigo_interno"),
                rs.getString("url_destino"),
                rs.getBoolean("leida"),
                rs.getTimestamp("fecha_creacion").toLocalDateTime());
    }
}
