package com.sbss.lexia.monitor.repository;

import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.Catalogos;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.Consulta;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.ExpedienteMonitor;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.ExpedienteOpcion;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.RegistrarConsultaRequest;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.Resumen;
import com.sbss.lexia.shared.dto.Limites;
import com.sbss.lexia.shared.dto.PaginaDto;
import com.sbss.lexia.shared.security.SesionActual;
import com.sbss.lexia.shared.security.VisibilidadExpedientes;
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

    private static final int TAMANO_PAGINA_MAXIMO = 100;
    private static final int CATALOGO_MAXIMO = 50;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Endpoint compatible con clientes antiguos que esperan un array.
     * Internamente usa la misma consulta optimizada del listado paginado.
     */
    public List<ExpedienteMonitor> listar(String expediente, String cliente, String abogado,
            String organo, String estadoProcesal, LocalDate desde, LocalDate hasta,
            int limite, int desplazamiento) {
        int limiteSeguro = Limites.limite(limite);
        int desplazamientoSeguro = Limites.desplazamiento(desplazamiento);
        return ejecutarListado(expediente, cliente, abogado, organo, estadoProcesal,
                desde, hasta, limiteSeguro, desplazamientoSeguro);
    }

    /**
     * Listado real paginado. Solo agrega las consultas de los monitores de la
     * pagina visible; evita ejecutar varios subqueries correlacionados por cada
     * fila del monitor cuando la tabla historica crece.
     */
    public PaginaDto<ExpedienteMonitor> listarPagina(String expediente, String cliente, String abogado,
            String organo, String estadoProcesal, LocalDate desde, LocalDate hasta,
            int pagina, int tamano) {
        int paginaSegura = Math.max(0, pagina);
        int tamanoSeguro = tamanoPagina(tamano);

        MapSqlParameterSource conteoParams = base();
        StringBuilder conteoSql = new StringBuilder("SELECT COUNT(*) ");
        appendMonitorFromWhere(conteoSql, conteoParams, expediente, cliente, abogado,
                organo, estadoProcesal, desde, hasta);
        Long total = jdbc.queryForObject(conteoSql.toString(), conteoParams, Long.class);
        long totalSeguro = nvl(total);

        long desplazamiento = (long) paginaSegura * tamanoSeguro;
        List<ExpedienteMonitor> contenido = desplazamiento >= totalSeguro
                ? List.of()
                : ejecutarListado(expediente, cliente, abogado, organo, estadoProcesal,
                        desde, hasta, tamanoSeguro, desplazamiento);

        return pagina(contenido, paginaSegura, tamanoSeguro, totalSeguro);
    }

    private List<ExpedienteMonitor> ejecutarListado(String expediente, String cliente, String abogado,
            String organo, String estadoProcesal, LocalDate desde, LocalDate hasta,
            int limite, long desplazamiento) {

        MapSqlParameterSource p = base();
        StringBuilder fromWhere = new StringBuilder();
        appendMonitorFromWhere(fromWhere, p, expediente, cliente, abogado,
                organo, estadoProcesal, desde, hasta);
        p.addValue("limite", limite);
        p.addValue("desplazamiento", desplazamiento);

        String sql = """
                WITH pagina AS (
                    SELECT m.id monitor_id,
                           m.uuid_publico,
                           e.id expediente_id,
                           e.codigo_interno,
                           e.numero_expediente,
                           c.nombre_busqueda cliente,
                           o.nombre organo,
                           ee.nombre estado_procesal,
                           u.nombre_completo abogado_responsable,
                           m.fuente_plataforma,
                           m.estado estado_monitor,
                           m.fecha_creacion,
                           m.ultima_consulta,
                           COALESCE(m.ultima_consulta, m.fecha_creacion) orden_fecha
                    %s
                    ORDER BY COALESCE(m.ultima_consulta, m.fecha_creacion) DESC,
                             e.codigo_interno DESC,
                             m.id DESC
                    LIMIT :limite OFFSET :desplazamiento
                ),
                estadisticas AS (
                    SELECT mc.monitor_id,
                           COUNT(*) total_consultas,
                           SUM(CASE WHEN mc.hubo_movimiento = 1 THEN 1 ELSE 0 END) movimientos,
                           SUM(CASE WHEN mc.estado_verificacion = 'PENDIENTE' THEN 1 ELSE 0 END) pendientes_verificacion,
                           MAX(CASE WHEN mc.hubo_movimiento = 1 THEN mc.fecha_consulta END) fecha_ultimo_movimiento
                    FROM monitor_consultas mc
                    JOIN pagina pg ON pg.monitor_id = mc.monitor_id
                    GROUP BY mc.monitor_id
                ),
                ultima AS (
                    SELECT monitor_id, ultimo_movimiento
                    FROM (
                        SELECT mc.monitor_id,
                               COALESCE(NULLIF(mc.tipo_movimiento, ''),
                                   CASE
                                       WHEN mc.nueva_resolucion = 1
                                           THEN CONCAT('Resolución ', COALESCE(mc.numero_resolucion, ''))
                                       ELSE 'Consulta sin novedad'
                                   END
                               ) ultimo_movimiento,
                               ROW_NUMBER() OVER (
                                   PARTITION BY mc.monitor_id
                                   ORDER BY mc.fecha_consulta DESC, mc.id DESC
                               ) rn
                        FROM monitor_consultas mc
                        JOIN pagina pg ON pg.monitor_id = mc.monitor_id
                    ) ordenadas
                    WHERE rn = 1
                )
                SELECT pg.uuid_publico,
                       pg.expediente_id,
                       pg.codigo_interno,
                       pg.numero_expediente,
                       pg.cliente,
                       pg.organo,
                       pg.estado_procesal,
                       pg.abogado_responsable,
                       pg.fuente_plataforma,
                       pg.estado_monitor,
                       pg.fecha_creacion,
                       pg.ultima_consulta,
                       COALESCE(es.total_consultas, 0) total_consultas,
                       COALESCE(es.movimientos, 0) movimientos,
                       COALESCE(es.pendientes_verificacion, 0) pendientes_verificacion,
                       ul.ultimo_movimiento,
                       es.fecha_ultimo_movimiento
                FROM pagina pg
                LEFT JOIN estadisticas es ON es.monitor_id = pg.monitor_id
                LEFT JOIN ultima ul ON ul.monitor_id = pg.monitor_id
                ORDER BY pg.orden_fecha DESC, pg.codigo_interno DESC, pg.monitor_id DESC
                """.formatted(fromWhere);

        return jdbc.query(sql, p, (rs, n) -> mapMonitor(rs));
    }

    /** Cliente antiguo: array acotado. */
    public List<Consulta> listarConsultas(Long expedienteId, String tipoMovimiento, String estadoVerificacion,
            LocalDate desde, LocalDate hasta, int limite, int desplazamiento) {
        int limiteSeguro = Limites.limite(limite);
        int desplazamientoSeguro = Limites.desplazamiento(desplazamiento);
        return ejecutarConsultas(expedienteId, tipoMovimiento, estadoVerificacion,
                desde, hasta, limiteSeguro, desplazamientoSeguro);
    }

    /** Historial paginado para la pantalla del Monitor Judicial. */
    public PaginaDto<Consulta> listarConsultasPagina(Long expedienteId, String tipoMovimiento,
            String estadoVerificacion, LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        int paginaSegura = Math.max(0, pagina);
        int tamanoSeguro = tamanoPagina(tamano);

        MapSqlParameterSource conteoParams = base();
        StringBuilder conteoSql = new StringBuilder("SELECT COUNT(*) ");
        appendConsultaFromWhere(conteoSql, conteoParams, expedienteId, tipoMovimiento,
                estadoVerificacion, desde, hasta);
        Long total = jdbc.queryForObject(conteoSql.toString(), conteoParams, Long.class);
        long totalSeguro = nvl(total);

        long desplazamiento = (long) paginaSegura * tamanoSeguro;
        List<Consulta> contenido = desplazamiento >= totalSeguro
                ? List.of()
                : ejecutarConsultas(expedienteId, tipoMovimiento, estadoVerificacion,
                        desde, hasta, tamanoSeguro, desplazamiento);

        return pagina(contenido, paginaSegura, tamanoSeguro, totalSeguro);
    }

    private List<Consulta> ejecutarConsultas(Long expedienteId, String tipoMovimiento,
            String estadoVerificacion, LocalDate desde, LocalDate hasta,
            int limite, long desplazamiento) {
        MapSqlParameterSource p = base();
        StringBuilder sql = new StringBuilder("""
                SELECT mc.uuid_publico, e.id expediente_id, e.codigo_interno, e.numero_expediente,
                       c.nombre_busqueda cliente, mc.fecha_consulta, us.id usuario_id,
                       us.nombre_completo usuario, mc.fuente_plataforma, mc.hubo_movimiento,
                       mc.tipo_movimiento, mc.nueva_resolucion, mc.numero_resolucion,
                       mc.tipo_resolucion, mc.fecha_resolucion, mc.fecha_notificacion,
                       mc.documento_descargado, mc.notificacion, mc.plazo, mc.observaciones,
                       mc.origen_informacion, mc.estado_verificacion,
                       uv.nombre_completo verificado_por, mc.fecha_verificacion
                """);
        appendConsultaFromWhere(sql, p, expedienteId, tipoMovimiento,
                estadoVerificacion, desde, hasta);
        sql.append(" ORDER BY mc.fecha_consulta DESC, mc.id DESC LIMIT :limite OFFSET :desplazamiento");
        p.addValue("limite", limite);
        p.addValue("desplazamiento", desplazamiento);
        return jdbc.query(sql.toString(), p, (rs, n) -> mapConsulta(rs));
    }

    /**
     * Una sola ida a MySQL para las cuatro tarjetas. La consulta usa un CTE de
     * monitores visibles y recorre su historial una sola vez.
     */
    public Resumen resumen() {
        MapSqlParameterSource p = base();
        StringBuilder visibles = new StringBuilder("""
                SELECT m.id
                  FROM monitor_expedientes m
                  JOIN expedientes e ON e.id = m.expediente_id
                 WHERE m.estudio_id = :estudioId
                   AND m.activo = 1
                   AND e.activo = 1
                """);
        aplicarVisibilidad(visibles, p);

        String sql = """
                WITH visibles AS (
                    %s
                ),
                metricas AS (
                    SELECT
                        COALESCE(SUM(CASE
                            WHEN mc.hubo_movimiento = 1
                             AND mc.fecha_consulta >= CURRENT_DATE
                             AND mc.fecha_consulta < DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY)
                            THEN 1 ELSE 0 END), 0) movimientos_hoy,
                        COALESCE(SUM(CASE
                            WHEN mc.nueva_resolucion = 1
                             AND mc.fecha_consulta >= CURRENT_DATE
                             AND mc.fecha_consulta < DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY)
                            THEN 1 ELSE 0 END), 0) resoluciones_hoy,
                        COALESCE(SUM(CASE
                            WHEN mc.estado_verificacion = 'PENDIENTE'
                            THEN 1 ELSE 0 END), 0) pendientes
                    FROM monitor_consultas mc
                    JOIN visibles v ON v.id = mc.monitor_id
                )
                SELECT (SELECT COUNT(*) FROM visibles) expedientes_monitoreados,
                       metricas.movimientos_hoy,
                       metricas.resoluciones_hoy,
                       metricas.pendientes
                  FROM metricas
                """.formatted(visibles);

        return jdbc.queryForObject(sql, p, (rs, n) -> new Resumen(
                rs.getLong("expedientes_monitoreados"),
                rs.getLong("movimientos_hoy"),
                rs.getLong("resoluciones_hoy"),
                rs.getLong("pendientes")));
    }

    /**
     * Selector bajo demanda: ya no descarga miles de expedientes cada vez que
     * se abre el Monitor Judicial. El frontend consulta como maximo 50.
     */
    public Catalogos catalogos(String busqueda, int limite) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.id, e.codigo_interno, e.numero_expediente,
                       c.nombre_busqueda cliente, o.nombre organo,
                       u.nombre_completo abogado_responsable
                  FROM expedientes e
                  JOIN clientes c ON c.id = e.cliente_id
                  LEFT JOIN organos_jurisdiccionales o ON o.id = e.organo_id
                  LEFT JOIN usuarios u ON u.id = e.abogado_responsable_id
                 WHERE e.estudio_id = :estudioId
                   AND e.activo = 1
                """);
        MapSqlParameterSource p = base();
        aplicarVisibilidad(sql, p);

        if (busqueda != null && !busqueda.isBlank()) {
            sql.append(" AND LOWER(CONCAT_WS(' ', e.codigo_interno, e.numero_expediente, c.nombre_busqueda)) LIKE :busqueda");
            p.addValue("busqueda", "%" + busqueda.trim().toLowerCase() + "%");
        }

        int limiteSeguro = Math.max(1, Math.min(limite, CATALOGO_MAXIMO));
        sql.append(" ORDER BY e.codigo_interno DESC, e.id DESC LIMIT :limite");
        p.addValue("limite", limiteSeguro);

        List<ExpedienteOpcion> expedientes = jdbc.query(sql.toString(), p, (rs, n) -> new ExpedienteOpcion(
                rs.getLong("id"), rs.getString("codigo_interno"), rs.getString("numero_expediente"),
                rs.getString("cliente"), rs.getString("organo"), rs.getString("abogado_responsable")));
        return new Catalogos(expedientes);
    }

    /** Validacion O(1) en SQL; antes cargaba todo el catalogo y hacia stream(). */
    public boolean expedienteVisible(Long expedienteId) {
        if (expedienteId == null) {
            return false;
        }
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                  FROM expedientes e
                 WHERE e.id = :expedienteId
                   AND e.estudio_id = :estudioId
                   AND e.activo = 1
                """);
        MapSqlParameterSource p = base().addValue("expedienteId", expedienteId);
        aplicarVisibilidad(sql, p);
        Integer total = jdbc.queryForObject(sql.toString(), p, Integer.class);
        return total != null && total > 0;
    }

    private void appendMonitorFromWhere(StringBuilder sql, MapSqlParameterSource p,
            String expediente, String cliente, String abogado, String organo,
            String estadoProcesal, LocalDate desde, LocalDate hasta) {
        sql.append("""
                 FROM monitor_expedientes m
                 JOIN expedientes e ON e.id = m.expediente_id
                 JOIN clientes c ON c.id = e.cliente_id
                 LEFT JOIN organos_jurisdiccionales o ON o.id = e.organo_id
                 LEFT JOIN estados_expediente ee ON ee.id = e.estado_id
                 LEFT JOIN usuarios u ON u.id = e.abogado_responsable_id
                WHERE m.estudio_id = :estudioId
                  AND m.activo = 1
                  AND e.activo = 1
                """);
        aplicarVisibilidad(sql, p);

        if (expediente != null && !expediente.isBlank()) {
            sql.append(" AND LOWER(CONCAT_WS(' ', e.codigo_interno, e.numero_expediente)) LIKE :expediente");
            p.addValue("expediente", "%" + expediente.trim().toLowerCase() + "%");
        }
        if (cliente != null && !cliente.isBlank()) {
            sql.append(" AND LOWER(c.nombre_busqueda) LIKE :cliente");
            p.addValue("cliente", "%" + cliente.trim().toLowerCase() + "%");
        }
        if (abogado != null && !abogado.isBlank()) {
            sql.append(" AND LOWER(COALESCE(u.nombre_completo, '')) LIKE :abogado");
            p.addValue("abogado", "%" + abogado.trim().toLowerCase() + "%");
        }
        if (organo != null && !organo.isBlank()) {
            sql.append(" AND LOWER(COALESCE(o.nombre, '')) LIKE :organo");
            p.addValue("organo", "%" + organo.trim().toLowerCase() + "%");
        }
        if (estadoProcesal != null && !estadoProcesal.isBlank()) {
            sql.append(" AND LOWER(COALESCE(ee.nombre, '')) LIKE :estadoProcesal");
            p.addValue("estadoProcesal", "%" + estadoProcesal.trim().toLowerCase() + "%");
        }
        if (desde != null || hasta != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM monitor_consultas f WHERE f.monitor_id = m.id");
            if (desde != null) {
                sql.append(" AND f.fecha_consulta >= :desde");
                p.addValue("desde", desde);
            }
            if (hasta != null) {
                sql.append(" AND f.fecha_consulta < DATE_ADD(:hasta, INTERVAL 1 DAY)");
                p.addValue("hasta", hasta);
            }
            sql.append(")");
        }
    }

    private void appendConsultaFromWhere(StringBuilder sql, MapSqlParameterSource p,
            Long expedienteId, String tipoMovimiento, String estadoVerificacion,
            LocalDate desde, LocalDate hasta) {
        sql.append("""
                  FROM monitor_consultas mc
                  JOIN monitor_expedientes m ON m.id = mc.monitor_id
                  JOIN expedientes e ON e.id = mc.expediente_id
                  JOIN clientes c ON c.id = e.cliente_id
                  JOIN usuarios us ON us.id = mc.usuario_id
                  LEFT JOIN usuarios uv ON uv.id = mc.usuario_verificacion_id
                 WHERE m.estudio_id = :estudioId
                   AND m.activo = 1
                   AND e.activo = 1
                """);
        aplicarVisibilidad(sql, p);

        if (expedienteId != null) {
            sql.append(" AND e.id = :expedienteId");
            p.addValue("expedienteId", expedienteId);
        }
        if (tipoMovimiento != null && !tipoMovimiento.isBlank()) {
            sql.append(" AND mc.tipo_movimiento = :tipoMovimiento");
            p.addValue("tipoMovimiento", tipoMovimiento.trim().toUpperCase());
        }
        if (estadoVerificacion != null && !estadoVerificacion.isBlank()) {
            sql.append(" AND mc.estado_verificacion = :estadoVerificacion");
            p.addValue("estadoVerificacion", estadoVerificacion.trim().toUpperCase());
        }
        if (desde != null) {
            sql.append(" AND mc.fecha_consulta >= :desde");
            p.addValue("desde", desde);
        }
        if (hasta != null) {
            sql.append(" AND mc.fecha_consulta < DATE_ADD(:hasta, INTERVAL 1 DAY)");
            p.addValue("hasta", hasta);
        }
    }

    private static <T> PaginaDto<T> pagina(List<T> contenido, int pagina, int tamano, long total) {
        int totalPaginas = total == 0 ? 0 : (int) Math.ceil((double) total / tamano);
        boolean ultima = totalPaginas == 0 || pagina >= totalPaginas - 1;
        return new PaginaDto<>(contenido, pagina, tamano, total, totalPaginas, ultima);
    }

    private static int tamanoPagina(int tamano) {
        return Math.max(1, Math.min(tamano, TAMANO_PAGINA_MAXIMO));
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

    /**
     * La regla de RF-02, compartida con expedientes, actuaciones, agenda,
     * documental y plazos. Este modulo la tenia escrita dos veces
     * (aplicarVisibilidadExpediente y visibilidadSql) y ambas aplicaban
     * "es_confidencial=0" incluso a quien tenia el expediente asignado, asi
     * que un responsable no veia su propio caso confidencial en el monitor.
     * El helper ya decide eso leyendo el SecurityContext, de modo que sobran
     * los parametros verTodos/verConfidenciales que venian del servicio.
     */
    private void acotar(StringBuilder sql, MapSqlParameterSource p, int limite, int desplazamiento) {
        sql.append(" LIMIT :limite OFFSET :desplazamiento");
        p.addValue("limite", Limites.limite(limite));
        p.addValue("desplazamiento", Limites.desplazamiento(desplazamiento));
    }

    private void aplicarVisibilidad(StringBuilder sql, MapSqlParameterSource p) {
        sql.append(VisibilidadExpedientes.sql("e"));
        p.addValues(VisibilidadExpedientes.parametros());
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
