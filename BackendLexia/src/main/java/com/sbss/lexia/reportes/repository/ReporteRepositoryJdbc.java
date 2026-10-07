package com.sbss.lexia.reportes.repository;

import com.sbss.lexia.reportes.dto.ReporteDtos.*;
import com.sbss.lexia.shared.dto.PaginaDto;
import com.sbss.lexia.shared.security.SesionActual;
import com.sbss.lexia.shared.security.VisibilidadExpedientes;
import java.math.BigDecimal;
import java.time.LocalDate;
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

        String sqlExpedientes = """
                SELECT
                    COUNT(*) total_expedientes,
                    COALESCE(SUM(CASE WHEN ee.cuenta_como_activo = 1 AND ee.es_estado_final = 0
                                      THEN 1 ELSE 0 END), 0) en_tramite,
                    COALESCE(SUM(CASE WHEN ee.es_estado_final = 1 THEN 1 ELSE 0 END), 0) concluidos,
                    COALESCE(SUM(CASE WHEN e.nivel_riesgo = 'CRITICO' THEN 1 ELSE 0 END), 0) vencidos,
                    COALESCE(AVG(TIMESTAMPDIFF(MONTH, e.fecha_inicio,
                        COALESCE(e.fecha_cierre, CURDATE()))), 0) meses_promedio
                FROM expedientes e
                JOIN estados_expediente ee ON ee.id = e.estado_id
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE 1=1
                """ + VisibilidadExpedientes.sql("e")
                + filtrosExpediente(fechaInicio, fechaFin, materia, abogadoId);

        var exp = jdbc.queryForMap(sqlExpedientes, p);

        // El periodo financiero se aplica a la fecha del honorario, no a la
        // fecha en que empezo el expediente. Conserva materia/abogado y la
        // misma regla de visibilidad del resto del sistema.
        String sqlFinanzas = """
                SELECT
                    COALESCE(SUM(h.monto_pactado), 0) ingresos_totales,
                    COALESCE(SUM(h.monto_pagado), 0) cobrado_total,
                    COALESCE(SUM(h.saldo_pendiente), 0) saldo_pendiente
                FROM honorarios h
                JOIN clientes c ON c.id = h.cliente_id AND c.estudio_id = :estudioId
                JOIN expedientes e ON e.id = h.expediente_id
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE 1=1
                """ + VisibilidadExpedientes.sql("e")
                + filtrosRelacionado("h.fecha_creacion", fechaInicio, fechaFin, materia, abogadoId);
        var fin = jdbc.queryForMap(sqlFinanzas, p);

        String sqlTareas = """
                SELECT COUNT(*) total
                FROM tareas t
                JOIN expedientes e ON e.id = t.expediente_id
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE t.activo = 1
                  AND t.estado NOT IN ('COMPLETADA', 'CANCELADA')
                """ + VisibilidadExpedientes.sql("e")
                + filtrosRelacionado("t.fecha_asignacion", fechaInicio, fechaFin, materia, abogadoId);
        Long tareasPendientes = jdbc.queryForObject(sqlTareas, p, Long.class);

        String sqlHoras = """
                SELECT COALESCE(SUM(ac.tiempo_minutos), 0) / 60.0 horas
                FROM actuaciones ac
                JOIN expedientes e ON e.id = ac.expediente_id
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE ac.activo = 1
                  AND ac.es_facturable = 1
                """ + VisibilidadExpedientes.sql("e")
                + filtrosRelacionado("ac.fecha_hora", fechaInicio, fechaFin, materia, abogadoId);
        Double horasFacturables = jdbc.queryForObject(sqlHoras, p, Double.class);

        return new KpiReporte(
                toLong(exp.get("total_expedientes")),
                toLong(exp.get("en_tramite")),
                toLong(exp.get("vencidos")),
                toLong(exp.get("concluidos")),
                toBigDecimal(fin.get("ingresos_totales")),
                toBigDecimal(fin.get("cobrado_total")),
                toBigDecimal(fin.get("saldo_pendiente")),
                tareasPendientes != null ? tareasPendientes : 0,
                toDouble(exp.get("meses_promedio")),
                horasFacturables != null ? horasFacturables : 0.0
        );
    }

    public List<Etiqueta> expedientesPorEstado(LocalDate fechaInicio, LocalDate fechaFin,
                                               String materia, Long abogadoId) {
        MapSqlParameterSource p = crearParametrosBase(fechaInicio, fechaFin, materia, abogadoId);
        String sql = """
                SELECT ee.nombre, COUNT(*) total
                FROM expedientes e
                JOIN estados_expediente ee ON ee.id = e.estado_id
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE 1=1
                """ + VisibilidadExpedientes.sql("e")
                + filtrosExpediente(fechaInicio, fechaFin, materia, abogadoId) + """
                GROUP BY ee.id, ee.nombre
                ORDER BY total DESC, ee.nombre
                """;

        return jdbc.query(sql, p, (rs, i) -> new Etiqueta(rs.getString("nombre"), rs.getLong("total")));
    }

    public List<Etiqueta> expedientesPorMateria(LocalDate fechaInicio, LocalDate fechaFin,
                                                String materia, Long abogadoId) {
        MapSqlParameterSource p = crearParametrosBase(fechaInicio, fechaFin, materia, abogadoId);
        String sql = """
                SELECT COALESCE(m.nombre, esp.nombre) materia_nombre, COUNT(*) total
                FROM expedientes e
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE 1=1
                """ + VisibilidadExpedientes.sql("e")
                + filtrosExpediente(fechaInicio, fechaFin, materia, abogadoId) + """
                GROUP BY COALESCE(m.nombre, esp.nombre)
                ORDER BY total DESC, materia_nombre
                """;

        return jdbc.query(sql, p,
                (rs, i) -> new Etiqueta(rs.getString("materia_nombre"), rs.getLong("total")));
    }

    public List<Barra> cargaPorUsuario(LocalDate fechaInicio, LocalDate fechaFin,
                                       String materia, Long abogadoId) {
        MapSqlParameterSource p = crearParametrosBase(fechaInicio, fechaFin, materia, abogadoId);
        String sql = """
                SELECT u.id,
                       u.nombre_completo,
                       COUNT(e.id) total,
                       COALESCE(SUM(CASE WHEN e.nivel_riesgo = 'CRITICO' THEN 2
                                         WHEN e.nivel_riesgo = 'ALTO' THEN 1 ELSE 0 END), 0) puntos
                FROM expedientes e
                JOIN usuarios u ON u.id = e.abogado_responsable_id
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE u.activo = 1
                  AND u.estado = 'ACTIVO'
                """ + VisibilidadExpedientes.sql("e")
                + filtrosExpediente(fechaInicio, fechaFin, materia, abogadoId) + """
                GROUP BY u.id, u.nombre_completo
                ORDER BY puntos DESC, total DESC, u.nombre_completo
                LIMIT 10
                """;

        return jdbc.query(sql, p, (rs, i) -> new Barra(
                rs.getString("nombre_completo"),
                rs.getLong("puntos"),
                color(i),
                rs.getLong("total") + (rs.getLong("total") == 1 ? " caso" : " casos")));
    }

    public List<Barra> ingresosPorMes(LocalDate fechaInicio, LocalDate fechaFin,
                                      String materia, Long abogadoId) {
        MapSqlParameterSource p = crearParametrosBase(fechaInicio, fechaFin, materia, abogadoId);
        String sql = """
                SELECT DATE_FORMAT(h.fecha_creacion, '%Y-%m') mes,
                       COALESCE(SUM(h.monto_pactado), 0) total
                FROM honorarios h
                JOIN clientes c ON c.id = h.cliente_id AND c.estudio_id = :estudioId
                JOIN expedientes e ON e.id = h.expediente_id
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                WHERE 1=1
                """ + VisibilidadExpedientes.sql("e")
                + filtrosRelacionado("h.fecha_creacion", fechaInicio, fechaFin, materia, abogadoId)
                + " GROUP BY DATE_FORMAT(h.fecha_creacion, '%Y-%m') ORDER BY mes";

        return jdbc.query(sql, p, (rs, i) -> Barra.de(
                rs.getString("mes"), rs.getLong("total"), color(i)));
    }

    public PaginaDto<ItemReporteDetalle> detallePagina(LocalDate fechaInicio, LocalDate fechaFin,
                                                        String materia, Long abogadoId, String texto,
                                                        int pagina, int tamano) {
        int page = Math.max(0, pagina);
        int size = normalizarTamano(tamano);
        int offset = page * size;

        MapSqlParameterSource p = crearParametrosBase(fechaInicio, fechaFin, materia, abogadoId);
        String filtros = filtrosExpediente(fechaInicio, fechaFin, materia, abogadoId)
                + filtroBusquedaDetalle(texto, p);

        String baseFrom = """
                FROM expedientes e
                JOIN clientes c ON c.id = e.cliente_id
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                JOIN estados_expediente ee ON ee.id = e.estado_id
                JOIN usuarios u ON u.id = e.abogado_responsable_id
                WHERE 1=1
                """ + VisibilidadExpedientes.sql("e") + filtros;

        Long total = jdbc.queryForObject("SELECT COUNT(*) " + baseFrom, p, Long.class);
        long totalElementos = total != null ? total : 0L;

        p.addValue("limit", size);
        p.addValue("offset", offset);
        String sql = selectDetalle() + baseFrom + " ORDER BY e.id DESC LIMIT :limit OFFSET :offset";
        List<ItemReporteDetalle> contenido = jdbc.query(sql, p, (rs, i) -> mapDetalle(rs));

        int totalPaginas = totalElementos == 0 ? 0 : (int) Math.ceil(totalElementos / (double) size);
        return new PaginaDto<>(contenido, page, size, totalElementos, totalPaginas,
                totalPaginas == 0 || page + 1 >= totalPaginas);
    }

    public List<ItemReporteDetalle> detalleExportacion(LocalDate fechaInicio, LocalDate fechaFin,
                                                        String materia, Long abogadoId, int limite) {
        MapSqlParameterSource p = crearParametrosBase(fechaInicio, fechaFin, materia, abogadoId);
        p.addValue("limite", Math.max(1, limite));
        String sql = selectDetalle() + """
                FROM expedientes e
                JOIN clientes c ON c.id = e.cliente_id
                JOIN especialidades esp ON esp.id = e.especialidad_id
                LEFT JOIN materias m ON m.id = e.materia_id
                JOIN estados_expediente ee ON ee.id = e.estado_id
                JOIN usuarios u ON u.id = e.abogado_responsable_id
                WHERE 1=1
                """ + VisibilidadExpedientes.sql("e")
                + filtrosExpediente(fechaInicio, fechaFin, materia, abogadoId)
                + " ORDER BY e.id DESC LIMIT :limite";
        return jdbc.query(sql, p, (rs, i) -> mapDetalle(rs));
    }

    public CatalogosReporte catalogos() {
        Integer estudioId = SesionActual.estudioId();
        MapSqlParameterSource p = new MapSqlParameterSource("estudioId", estudioId);

        List<OpcionReporte> materias = jdbc.query("""
                SELECT m.id, m.nombre
                FROM materias m
                JOIN especialidades e ON e.id = m.especialidad_id
                WHERE e.estudio_id = :estudioId
                  AND e.activo = 1
                  AND m.activo = 1
                ORDER BY m.nombre
                """, p, (rs, i) -> new OpcionReporte(rs.getLong("id"), rs.getString("nombre")));

        List<OpcionReporte> abogados = jdbc.query("""
                SELECT id, nombre_completo nombre
                FROM usuarios
                WHERE estudio_id = :estudioId
                  AND tipo_usuario = 'INTERNO'
                  AND activo = 1
                  AND estado = 'ACTIVO'
                ORDER BY nombre_completo
                """, p, (rs, i) -> new OpcionReporte(rs.getLong("id"), rs.getString("nombre")));

        return new CatalogosReporte(materias, abogados);
    }

    private String selectDetalle() {
        return """
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
                """;
    }

    private ItemReporteDetalle mapDetalle(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ItemReporteDetalle(
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
        );
    }

    private MapSqlParameterSource crearParametrosBase(LocalDate fechaInicio, LocalDate fechaFin,
                                                       String materia, Long abogadoId) {
        MapSqlParameterSource p = new MapSqlParameterSource(VisibilidadExpedientes.parametros());
        p.addValue("estudioId", SesionActual.estudioId());
        if (fechaInicio != null) p.addValue("fechaInicio", fechaInicio);
        if (fechaFin != null) p.addValue("fechaFin", fechaFin);
        if (materiaValida(materia)) p.addValue("materia", materia.trim());
        if (abogadoId != null && abogadoId > 0) p.addValue("abogadoId", abogadoId);
        return p;
    }

    private String filtrosExpediente(LocalDate fechaInicio, LocalDate fechaFin,
                                     String materia, Long abogadoId) {
        return filtrosRelacionado("e.fecha_inicio", fechaInicio, fechaFin, materia, abogadoId);
    }

    private String filtrosRelacionado(String columnaFecha, LocalDate fechaInicio, LocalDate fechaFin,
                                      String materia, Long abogadoId) {
        StringBuilder sb = new StringBuilder();
        if (fechaInicio != null) sb.append(" AND ").append(columnaFecha).append(" >= :fechaInicio");
        if (fechaFin != null) {
            // DATETIME: usar limite exclusivo del dia siguiente mantiene el
            // predicado sargable y no pierde registros del ultimo dia.
            sb.append(" AND ").append(columnaFecha).append(" < DATE_ADD(:fechaFin, INTERVAL 1 DAY)");
        }
        if (materiaValida(materia)) {
            // Los selects envian nombres exactos. Evitar LOWER + LIKE '%...%'
            // permite al optimizador aprovechar indices/joins y evita falsos
            // positivos (p.ej. "Civil" contra nombres mas largos).
            sb.append(" AND (esp.nombre = :materia OR m.nombre = :materia)");
        }
        if (abogadoId != null && abogadoId > 0) {
            sb.append(" AND e.abogado_responsable_id = :abogadoId");
        }
        return sb.append('\n').toString();
    }

    private String filtroBusquedaDetalle(String texto, MapSqlParameterSource p) {
        String q = texto == null ? "" : texto.trim();
        if (q.isEmpty()) return "";
        if (q.length() > 120) q = q.substring(0, 120);
        p.addValue("texto", "%" + q + "%");
        return """
                 AND (
                    e.codigo_interno LIKE :texto
                    OR e.numero_expediente LIKE :texto
                    OR c.nombre_busqueda LIKE :texto
                    OR COALESCE(m.nombre, esp.nombre) LIKE :texto
                    OR u.nombre_completo LIKE :texto
                    OR ee.nombre LIKE :texto
                 )
                """;
    }

    private boolean materiaValida(String materia) {
        return materia != null && !materia.isBlank() && !materia.equalsIgnoreCase("todas");
    }

    private int normalizarTamano(int solicitado) {
        return switch (solicitado) {
            case 10, 20, 40, 100 -> solicitado;
            default -> 20;
        };
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
