package com.sbss.lexia.dashboard.repository;

import com.sbss.lexia.dashboard.dto.DashboardDtos.Barra;
import com.sbss.lexia.dashboard.dto.DashboardDtos.EstadoCobranza;
import com.sbss.lexia.dashboard.dto.DashboardDtos.Etiqueta;
import com.sbss.lexia.dashboard.dto.DashboardDtos.ExpedienteAtencion;
import com.sbss.lexia.shared.security.SesionActual;
import com.sbss.lexia.shared.security.VisibilidadExpedientes;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DashboardRepositoryJdbc {

    private final NamedParameterJdbcTemplate jdbc;

    /** Paleta unica para los graficos. Se comparte con el front por esta razon. */
    private static final String[] COLORES = {
            "#4F46E5", "#0EA5E9", "#10B981", "#F59E0B", "#EF4444",
            "#8B5CF6", "#EC4899", "#14B8A6", "#F97316", "#64748B"
    };

    public List<Etiqueta> expedientesPorEstado() {
        return jdbc.query("""
                SELECT ee.nombre, COUNT(*) total
                  FROM expedientes e
                  JOIN estados_expediente ee ON ee.id = e.estado_id
                 WHERE 1=1
                """ + VisibilidadExpedientes.sql("e") + """
                 GROUP BY ee.nombre
                 ORDER BY total DESC, ee.nombre
                """, vis(), (rs, i) -> new Etiqueta(rs.getString("nombre"), rs.getLong("total")));
    }

    public List<Etiqueta> expedientesPorEspecialidad() {
        MapSqlParameterSource p = vis();
        return jdbc.query("""
                SELECT esp.nombre, COUNT(*) total
                  FROM expedientes e
                  JOIN especialidades esp ON esp.id = e.especialidad_id
                 WHERE 1=1
                """ + VisibilidadExpedientes.sql("e") + """
                 GROUP BY esp.nombre
                 ORDER BY total DESC, esp.nombre
                """, p, (rs, i) -> new Etiqueta(rs.getString("nombre"), rs.getLong("total")));
    }

    /**
     * Expedientes por abogado.
     *
     * Se cuentan sobre lo que el usuario puede VER, no sobre todo el estudio:
     * si no, el grafico prometeria a un abogado los expedientes de un
     * companero que despues no podria abrir desde la lista.
     */
    public List<Barra> expedientesPorAbogado() {
        return jdbc.query("""
                SELECT u.nombre_completo, COUNT(*) total
                  FROM expedientes e
                  JOIN usuarios u ON u.id = e.abogado_responsable_id
                 WHERE 1=1
                """ + VisibilidadExpedientes.sql("e") + """
                 GROUP BY u.nombre_completo
                 ORDER BY total DESC, u.nombre_completo
                """, vis(), (rs, i) -> Barra.de(
                rs.getString("nombre_completo"), rs.getLong("total"), color(i)));
    }

    /**
     * Carga de trabajo por usuario, PONDERADA por riesgo.
     *
     * A un abogado con dos casos criticos le conviene mas desatenderlos que a
     * otro con diez en tramite normal, asi que la barra mide puntos de carga
     * (critico=2, alto=1) y NO el numero de expedientes. El conteo crudo
     * vuelve en `detalle`: sin el, "3" al lado de una barra no se sabe si son
     * tres casos criticos o tres de tramite.
     *
     * Cuenta lo que el usuario puede VER, igual que expedientesPorAbogado: la
     * regla va en el ON del LEFT JOIN, asi que quien no tiene ningun expediente
     * visible desaparece por el HAVING en vez de salir con los ajenos.
     */
    public List<Barra> cargaPorUsuario() {
        MapSqlParameterSource p = vis().addValue("estudioId", SesionActual.estudioId());
        return jdbc.query("""
                SELECT u.nombre_completo,
                       COUNT(e.id) total,
                       COALESCE(SUM(CASE WHEN e.nivel_riesgo = 'CRITICO' THEN 2
                                          WHEN e.nivel_riesgo = 'ALTO' THEN 1 ELSE 0 END), 0) puntos
                  FROM usuarios u
                  LEFT JOIN expedientes e ON e.abogado_responsable_id = u.id
                """ + VisibilidadExpedientes.sql("e") + """
                 WHERE u.estudio_id = :estudioId AND u.activo = 1
                 GROUP BY u.nombre_completo
                HAVING total > 0
                 ORDER BY puntos DESC, total DESC, u.nombre_completo
                 LIMIT 10
                """, p, (rs, i) -> new Barra(
                rs.getString("nombre_completo"),
                rs.getLong("puntos"),
                color(i),
                rs.getLong("total") + (rs.getLong("total") == 1 ? " expediente" : " expedientes")));
    }

    /**
     * Tareas abiertas por responsable.
     *
     * El filtro de estados NO es `NOT IN ('COMPLETADA','CANCELADA')` a
     * proposito: `tareas.estado` tiene su propio flujo (PENDIENTE, EN_REVISION,
     * APROBADA, EN_PROCESO...) y una tarea APROBADA sigue siendo trabajo sin
     * cerrar. Solo se restan las que de verdad terminaron. Quien tenga
     * vencidas pinta en rojo el mismo: es el dato que hace mirar el grafico.
     *
     * Misma regla de visibilidad que el modulo de tareas, que acota por el
     * expediente de la tarea.
     */
    public List<Barra> tareasPorUsuario() {
        return jdbc.query("""
                SELECT u.nombre_completo,
                       COUNT(t.id) total,
                       SUM(CASE WHEN t.estado = 'VENCIDA' THEN 1 ELSE 0 END) vencidas
                  FROM tareas t
                  JOIN usuarios u ON u.id = t.usuario_responsable_id
                  JOIN expedientes e ON e.id = t.expediente_id
                 WHERE t.activo = 1
                   AND t.estado NOT IN ('COMPLETADA','CANCELADA')
                """ + VisibilidadExpedientes.sql("e") + """
                 GROUP BY u.nombre_completo
                 ORDER BY vencidas DESC, total DESC, u.nombre_completo
                 LIMIT 10
                """, vis(), (rs, i) -> new Barra(
                rs.getString("nombre_completo"),
                rs.getLong("total"),
                rs.getLong("vencidas") > 0 ? "#EF4444" : color(i),
                rs.getLong("vencidas") > 0 ? rs.getLong("vencidas") + " vencida(s)" : null));
    }

    /**
     * Honorarios facturados por mes, ultimos 6 meses.
     *
     * Se grafica lo facturado (`monto_pactado`) y no lo cobrado: `honorarios`
     * no tiene columna `fecha_pago` — el cobro se registra en
     * `movimientos_financieros`. Facturar por mes es lo que responde "como va
     * el ano"; cuanto hay por cobrar, con su vencido, lo dice `cobranza()`.
     *
     * Los meses sin facturacion no se rellenan con ceros aqui: se devuelven
     * solo los que tienen datos y es el front el que completa la serie, para
     * que un mes vacio se vea como un hueco y no como un ingreso de cero.
     */
    public List<Barra> ingresosPorMes() {
        MapSqlParameterSource p = new MapSqlParameterSource("estudioId", SesionActual.estudioId());
        return jdbc.query("""
                SELECT DATE_FORMAT(h.fecha_creacion, '%Y-%m') mes,
                       COALESCE(SUM(h.monto_pactado), 0) total
                  FROM honorarios h
                  JOIN clientes c ON c.id = h.cliente_id
                 WHERE c.estudio_id = :estudioId
                   AND h.fecha_creacion >= DATE_SUB(CURDATE(), INTERVAL 5 MONTH)
                 GROUP BY DATE_FORMAT(h.fecha_creacion, '%Y-%m')
                 ORDER BY mes
                """, p, (rs, i) -> Barra.de(
                rs.getString("mes"), rs.getLong("total"), color(i)));
    }

    public EstadoCobranza cobranza() {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(h.monto_pactado), 0) facturado,
                       COALESCE(SUM(h.monto_pagado), 0) cobrado,
                       COALESCE(SUM(h.saldo_pendiente), 0) pendiente,
                       COALESCE(SUM(CASE WHEN h.saldo_pendiente > 0
                                          AND h.fecha_vencimiento < CURDATE()
                                         THEN h.saldo_pendiente ELSE 0 END), 0) vencido,
                       SUM(CASE WHEN h.saldo_pendiente > 0 THEN 1 ELSE 0 END) cuentas_pendientes,
                       SUM(CASE WHEN h.saldo_pendiente > 0
                                 AND h.fecha_vencimiento < CURDATE() THEN 1 ELSE 0 END) cuentas_vencidas
                  FROM honorarios h
                  JOIN clientes c ON c.id = h.cliente_id
                 WHERE c.estudio_id = :estudioId AND c.activo = 1
                """, new MapSqlParameterSource("estudioId", SesionActual.estudioId()),
                (rs, i) -> new EstadoCobranza(
                        rs.getBigDecimal("facturado"),
                        rs.getBigDecimal("cobrado"),
                        rs.getBigDecimal("pendiente"),
                        rs.getBigDecimal("vencido"),
                        rs.getLong("cuentas_pendientes"),
                        rs.getLong("cuentas_vencidas")));
    }

    /**
     * Los seis expedientes abiertos que mas piden atencion: primero los de
     * riesgo critico y despues los mas tiempo sin movimiento (mas de 30 dias).
     * "Abierto" es `es_estado_final = 0`, el mismo dato que usa Configuracion,
     * no una lista de codigos escrita en el front.
     */
    public List<ExpedienteAtencion> expedientesAtencion() {
        return jdbc.query("""
                SELECT e.uuid_publico, e.codigo_interno, e.numero_expediente,
                       c.nombre_busqueda cliente, COALESCE(m.nombre, esp.nombre) materia,
                       ee.nombre estado, e.nivel_riesgo, u.nombre_completo responsable,
                       e.es_confidencial, e.fecha_ultima_actuacion
                  FROM expedientes e
                  JOIN clientes c ON c.id = e.cliente_id
                  JOIN estados_expediente ee ON ee.id = e.estado_id
                  JOIN especialidades esp ON esp.id = e.especialidad_id
                  LEFT JOIN materias m ON m.id = e.materia_id
                  LEFT JOIN usuarios u ON u.id = e.abogado_responsable_id
                 WHERE e.activo = 1
                   AND ee.es_estado_final = 0
                   AND (e.nivel_riesgo = 'CRITICO'
                        OR e.fecha_ultima_actuacion < NOW() - INTERVAL 30 DAY)
                """ + VisibilidadExpedientes.sql("e") + """
                 ORDER BY e.nivel_riesgo = 'CRITICO' DESC, e.fecha_ultima_actuacion, e.codigo_interno
                 LIMIT 6
                """, vis(), (rs, i) -> new ExpedienteAtencion(
                rs.getString("uuid_publico"),
                rs.getString("codigo_interno"),
                rs.getString("numero_expediente"),
                rs.getString("cliente"),
                rs.getString("materia"),
                rs.getString("estado"),
                rs.getString("nivel_riesgo"),
                rs.getString("responsable"),
                rs.getBoolean("es_confidencial"),
                rs.getTimestamp("fecha_ultima_actuacion") == null
                        ? null : rs.getTimestamp("fecha_ultima_actuacion").toLocalDateTime()));
    }

    private MapSqlParameterSource vis() {
        // No se puede pasar p.getValues(): VisibilidadExpedientes.parametros()
        // hace put() sobre el mapa que recibe, y la vista que devuelve
        // MapSqlParameterSource es inmutable (UnsupportedOperationException).
        // Se le da el mapa nuevo que genera el propio helper.
        return new MapSqlParameterSource(VisibilidadExpedientes.parametros());
    }

    private String color(int i) {
        return COLORES[Math.floorMod(i, COLORES.length)];
    }
}
