package com.sbss.lexia.reportes.repository;

import com.sbss.lexia.reportes.dto.ReporteDtos.*;
import com.sbss.lexia.shared.security.SesionActual;
import com.sbss.lexia.shared.security.VisibilidadExpedientes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReporteRepositoryJdbc {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String[] COLORES = {
            "#4F46E5", "#0EA5E9", "#10B981", "#F59E0B", "#EF4444",
            "#8B5CF6", "#EC4899", "#14B8A6", "#F97316", "#64748B"
    };

    public KpiReporte obtenerKpis(LocalDate fechaInicio, LocalDate fechaFin, String materia, Long abogadoId) {
        MapSqlParameterSource p = crearParametrosBase(fechaInicio, fechaFin, materia, abogadoId);
        String filtroSql = construirFiltrosSql(fechaInicio, fechaFin, materia, abogadoId);

        String sql = """
                SELECT
                    COUNT(DISTINCT e.id) total_expedientes,
                    SUM(CASE WHEN ee.cuenta_como_activo = 1 AND ee.es_estado_final = 0
                             THEN 1 ELSE 0 END) en_tramite,
                    SUM(CASE WHEN ee.es_estado_final = 1 THEN 1 ELSE 0 END) concluidos,
                    SUM(CASE WHEN e.nivel_riesgo = 'CRITICO' THEN 1 ELSE 0 END) vencidos,
                    AVG(TIMESTAMPDIFF(MONTH, e.fecha_inicio, COALESCE(e.fecha_cierre, CURDATE()))) meses_promedio
                FROM expedientes e
                JOIN estados_expediente ee ON ee.id = e.estado_id
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE 1=1
                """ + VisibilidadExpedientes.sql("e") + filtroSql;

        var kpisExpediente = jdbc.queryForMap(sql, p);

        // Honorarios & Cobranza. Se une a expedientes para que el dinero respete
        // los mismos filtros (materia, abogado, periodo) que el resto del
        // reporte; si no, marcar "Civil" mostraria 220 500 de todos los casos.
        // LEFT JOIN para no perder honorarios sin expediente asignado.
        MapSqlParameterSource pFin = crearParametrosBase(fechaInicio, fechaFin, materia, abogadoId);
        String sqlFin = """
                SELECT
                    COALESCE(SUM(h.monto_pactado), 0) ingresos_totales,
                    COALESCE(SUM(h.monto_pagado), 0) cobrado_total,
                    COALESCE(SUM(h.saldo_pendiente), 0) saldo_pendiente
                FROM honorarios h
                JOIN clientes c ON c.id = h.cliente_id AND c.estudio_id = :estudioId
                LEFT JOIN expedientes e ON e.id = h.expediente_id
                LEFT JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE 1=1
                """ + filtroSql;

        var mapFin = jdbc.queryForMap(sqlFin, pFin);

        // Tareas pendientes
        MapSqlParameterSource pTar = new MapSqlParameterSource("estudioId", SesionActual.estudioId());
        String sqlTar = """
                SELECT COUNT(t.id) total
                FROM tareas t
                JOIN expedientes e ON e.id = t.expediente_id
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE t.activo = 1
                  AND t.estado NOT IN ('COMPLETADA', 'CANCELADA')
                  AND e.estudio_id = :estudioId
                """ + filtroSql;
        if (fechaInicio != null) pTar.addValue("fechaInicio", fechaInicio);
        if (fechaFin != null) pTar.addValue("fechaFin", fechaFin);
        if (materia != null && !materia.equalsIgnoreCase("todas")) pTar.addValue("materia", materia);
        if (abogadoId != null && abogadoId > 0) pTar.addValue("abogadoId", abogadoId);

        Long tareasPendientes = jdbc.queryForObject(sqlTar, pTar, Long.class);

        // Horas facturables: suma de tiempo_minutos de las actuaciones marcadas
        // como facturables, dividido entre 60. Es el unico origen del dato; no
        // se estima a partir de la cantidad de expedientes.
        String sqlHoras = """
                SELECT COALESCE(SUM(ac.tiempo_minutos), 0) / 60.0 horas
                FROM actuaciones ac
                JOIN expedientes e ON e.id = ac.expediente_id
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE ac.activo = 1
                  AND ac.es_facturable = 1
                  AND e.estudio_id = :estudioId
                """ + filtroSql;
        Double horasFacturables = jdbc.queryForObject(sqlHoras, pTar, Double.class);

        long totalExp = toLong(kpisExpediente.get("total_expedientes"));
        long enTramite = toLong(kpisExpediente.get("en_tramite"));
        long vencidos = toLong(kpisExpediente.get("vencidos"));
        long concluidos = toLong(kpisExpediente.get("concluidos"));

        // Sin expedientes en el filtro no hay promedio: se devuelve 0, no un
        // numero de relleno. Cerrados usan fecha_cierre; los que siguen
        // abiertos se miden contra hoy.
        double mesesPromedio = toDouble(kpisExpediente.get("meses_promedio"));

        BigDecimal ingresos = toBigDecimal(mapFin.get("ingresos_totales"));
        BigDecimal cobrado = toBigDecimal(mapFin.get("cobrado_total"));
        BigDecimal saldo = toBigDecimal(mapFin.get("saldo_pendiente"));

        return new KpiReporte(
                totalExp,
                enTramite,
                vencidos,
                concluidos,
                ingresos,
                cobrado,
                saldo,
                tareasPendientes != null ? tareasPendientes : 0,
                mesesPromedio,
                horasFacturables != null ? horasFacturables : 0.0
        );
    }

    public List<Etiqueta> expedientesPorEstado(LocalDate fechaInicio, LocalDate fechaFin, String materia, Long abogadoId) {
        MapSqlParameterSource p = crearParametrosBase(fechaInicio, fechaFin, materia, abogadoId);
        String filtroSql = construirFiltrosSql(fechaInicio, fechaFin, materia, abogadoId);

        String sql = """
                SELECT ee.nombre, COUNT(*) total
                FROM expedientes e
                JOIN estados_expediente ee ON ee.id = e.estado_id
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE 1=1
                """ + VisibilidadExpedientes.sql("e") + filtroSql + """
                GROUP BY ee.nombre
                ORDER BY total DESC, ee.nombre
                """;

        return jdbc.query(sql, p, (rs, i) -> new Etiqueta(rs.getString("nombre"), rs.getLong("total")));
    }

    public List<Etiqueta> expedientesPorMateria(LocalDate fechaInicio, LocalDate fechaFin, String materia, Long abogadoId) {
        MapSqlParameterSource p = crearParametrosBase(fechaInicio, fechaFin, materia, abogadoId);
        String filtroSql = construirFiltrosSql(fechaInicio, fechaFin, materia, abogadoId);

        String sql = """
                SELECT COALESCE(m.nombre, esp.nombre) materia_nombre, COUNT(*) total
                FROM expedientes e
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE 1=1
                """ + VisibilidadExpedientes.sql("e") + filtroSql + """
                GROUP BY COALESCE(m.nombre, esp.nombre)
                ORDER BY total DESC
                """;

        return jdbc.query(sql, p, (rs, i) -> new Etiqueta(rs.getString("materia_nombre"), rs.getLong("total")));
    }

    public List<Barra> expedientesPorAbogado(LocalDate fechaInicio, LocalDate fechaFin, String materia, Long abogadoId) {
        MapSqlParameterSource p = crearParametrosBase(fechaInicio, fechaFin, materia, abogadoId);
        String filtroSql = construirFiltrosSql(fechaInicio, fechaFin, materia, abogadoId);

        String sql = """
                SELECT u.nombre_completo, COUNT(*) total
                FROM expedientes e
                JOIN usuarios u ON u.id = e.abogado_responsable_id
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE 1=1
                """ + VisibilidadExpedientes.sql("e") + filtroSql + """
                GROUP BY u.nombre_completo
                ORDER BY total DESC, u.nombre_completo
                """;

        return jdbc.query(sql, p, (rs, i) -> Barra.de(
                rs.getString("nombre_completo"), rs.getLong("total"), color(i)));
    }

    public List<Barra> cargaPorUsuario(LocalDate fechaInicio, LocalDate fechaFin, String materia, Long abogadoId) {
        MapSqlParameterSource p = crearParametrosBase(fechaInicio, fechaFin, materia, abogadoId);
        String filtroSql = construirFiltrosSql(fechaInicio, fechaFin, materia, abogadoId);

        String sql = """
                SELECT u.nombre_completo,
                       COUNT(e.id) total,
                       COALESCE(SUM(CASE WHEN e.nivel_riesgo = 'CRITICO' THEN 2
                                          WHEN e.nivel_riesgo = 'ALTO' THEN 1 ELSE 0 END), 0) puntos
                FROM usuarios u
                JOIN expedientes e ON e.abogado_responsable_id = u.id
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE u.estudio_id = :estudioId AND u.activo = 1
                """ + filtroSql + """
                GROUP BY u.nombre_completo
                HAVING total > 0
                ORDER BY puntos DESC, total DESC
                LIMIT 10
                """;

        return jdbc.query(sql, p, (rs, i) -> new Barra(
                rs.getString("nombre_completo"),
                rs.getLong("puntos"),
                color(i),
                rs.getLong("total") + (rs.getLong("total") == 1 ? " caso" : " casos")));
    }

    public List<Barra> tareasPorUsuario(LocalDate fechaInicio, LocalDate fechaFin, String materia, Long abogadoId) {
        MapSqlParameterSource p = crearParametrosBase(fechaInicio, fechaFin, materia, abogadoId);
        String filtroSql = construirFiltrosSql(fechaInicio, fechaFin, materia, abogadoId);

        String sql = """
                SELECT u.nombre_completo,
                       COUNT(t.id) total,
                       SUM(CASE WHEN t.estado = 'VENCIDA' THEN 1 ELSE 0 END) vencidas
                FROM tareas t
                JOIN usuarios u ON u.id = t.usuario_responsable_id
                JOIN expedientes e ON e.id = t.expediente_id
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE t.activo = 1
                  AND t.estado NOT IN ('COMPLETADA','CANCELADA')
                  AND e.estudio_id = :estudioId
                """ + filtroSql + """
                GROUP BY u.nombre_completo
                ORDER BY vencidas DESC, total DESC
                LIMIT 10
                """;

        return jdbc.query(sql, p, (rs, i) -> new Barra(
                rs.getString("nombre_completo"),
                rs.getLong("total"),
                rs.getLong("vencidas") > 0 ? "#EF4444" : color(i),
                rs.getLong("vencidas") > 0 ? rs.getLong("vencidas") + " vencida(s)" : null));
    }

    public List<Barra> ingresosPorMes(LocalDate fechaInicio, LocalDate fechaFin, String materia, Long abogadoId) {
        MapSqlParameterSource p = crearParametrosBase(fechaInicio, fechaFin, materia, abogadoId);
        String filtroSql = construirFiltrosSql(fechaInicio, fechaFin, materia, abogadoId);
        String sql = """
                SELECT DATE_FORMAT(h.fecha_creacion, '%Y-%m') mes,
                       COALESCE(SUM(h.monto_pactado), 0) total
                FROM honorarios h
                JOIN clientes c ON c.id = h.cliente_id AND c.estudio_id = :estudioId
                LEFT JOIN expedientes e ON e.id = h.expediente_id
                LEFT JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE 1=1
                """ + filtroSql;

        // Sin rango explicito se muestran los ultimos 6 meses, igual que el
        // resto de series del reporte.
        if (fechaInicio == null) {
            sql += " AND h.fecha_creacion >= DATE_SUB(CURDATE(), INTERVAL 5 MONTH)";
        }

        sql += " GROUP BY DATE_FORMAT(h.fecha_creacion, '%Y-%m') ORDER BY mes";

        return jdbc.query(sql, p, (rs, i) -> Barra.de(
                rs.getString("mes"), rs.getLong("total"), color(i)));
    }

    public List<ItemReporteDetalle> tablaDetalle(LocalDate fechaInicio, LocalDate fechaFin, String materia, Long abogadoId) {
        MapSqlParameterSource p = crearParametrosBase(fechaInicio, fechaFin, materia, abogadoId);
        String filtroSql = construirFiltrosSql(fechaInicio, fechaFin, materia, abogadoId);

        String sql = """
                SELECT e.id,
                       COALESCE(e.codigo_interno, e.numero_expediente) codigo,
                       COALESCE(e.resumen, e.codigo_interno, 'Expediente sin resumen') titulo,
                       c.nombre_busqueda cliente,
                       COALESCE(
                         (SELECT GROUP_CONCAT(cp.nombre_completo ORDER BY cp.nombre_completo SEPARATOR ', ')
                            FROM expediente_contrapartes ecp
                            JOIN contrapartes cp ON cp.id = ecp.contraparte_id
                           WHERE ecp.expediente_id = e.id),
                         'No especificada'
                       ) contraparte,
                       COALESCE(m.nombre, esp.nombre) materia,
                       u.nombre_completo abogado,
                       ee.nombre estado,
                       e.nivel_riesgo,
                       DATE_FORMAT(e.fecha_inicio, '%d/%m/%Y') fecha_inicio,
                       COALESCE(e.cuantia, 0) monto
                FROM expedientes e
                JOIN clientes c ON c.id = e.cliente_id
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                JOIN estados_expediente ee ON ee.id = e.estado_id
                JOIN usuarios u ON u.id = e.abogado_responsable_id
                WHERE 1=1
                """ + VisibilidadExpedientes.sql("e") + filtroSql + """
                ORDER BY e.id DESC
                LIMIT 50
                """;

        return jdbc.query(sql, p, (rs, i) -> new ItemReporteDetalle(
                rs.getLong("id"),
                rs.getString("codigo"),
                rs.getString("titulo"),
                rs.getString("cliente"),
                rs.getString("contraparte"),
                rs.getString("materia"),
                rs.getString("abogado"),
                rs.getString("estado"),
                rs.getString("nivel_riesgo"),
                rs.getString("fecha_inicio"),
                rs.getBigDecimal("monto")
        ));
    }

    private MapSqlParameterSource crearParametrosBase(LocalDate fechaInicio, LocalDate fechaFin, String materia, Long abogadoId) {
        MapSqlParameterSource p = new MapSqlParameterSource(VisibilidadExpedientes.parametros());
        p.addValue("estudioId", SesionActual.estudioId());
        if (fechaInicio != null) p.addValue("fechaInicio", fechaInicio);
        if (fechaFin != null) p.addValue("fechaFin", fechaFin);
        if (materia != null && !materia.equalsIgnoreCase("todas")) p.addValue("materia", materia);
        if (abogadoId != null && abogadoId > 0) p.addValue("abogadoId", abogadoId);
        return p;
    }

    private String construirFiltrosSql(LocalDate fechaInicio, LocalDate fechaFin, String materia, Long abogadoId) {
        StringBuilder sb = new StringBuilder();
        if (fechaInicio != null) {
            sb.append(" AND e.fecha_inicio >= :fechaInicio");
        }
        if (fechaFin != null) {
            sb.append(" AND e.fecha_inicio <= :fechaFin");
        }
        if (materia != null && !materia.equalsIgnoreCase("todas")) {
            sb.append(" AND (LOWER(esp.nombre) LIKE LOWER(CONCAT('%', :materia, '%')) OR LOWER(COALESCE(m.nombre, '')) LIKE LOWER(CONCAT('%', :materia, '%')))");
        }
        if (abogadoId != null && abogadoId > 0) {
            sb.append(" AND e.abogado_responsable_id = :abogadoId");
        }
        // El salto de cierre del text block de Java se descarta, asi que un
        // filtro terminado en ":fechaFin" quedaba pegado al "GROUP BY" que le
        // sigue y Spring lo leia como un parametro nuevo llamado "fechaFinGROUP".
        return sb.toString() + "\n";
    }

    private String color(int i) {
        return COLORES[Math.floorMod(i, COLORES.length)];
    }

    private long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        return 0L;
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        return 0.0;
    }
}
