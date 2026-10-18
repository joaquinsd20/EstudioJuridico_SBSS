package com.sbss.lexia.notificaciones.service;

import com.sbss.lexia.notificaciones.dto.NotificacionDtos.CrearNotificacion;
import com.sbss.lexia.notificaciones.repository.NotificacionRepositoryJdbc;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quien PRODUCE las notificaciones. Hasta ahora la bandeja sabia leer, marcar y
 * borrar, pero nada la llenaba: las alertas se programaban en `alertas` y se
 * quedaban ahi para siempre (RF-10).
 *
 * Dos vias:
 *  - {@link #entregarAlertasVencidas()}: lo ejecuta el planificador cada pocos
 *    minutos y convierte en notificacion cada alerta cuya hora ya llego.
 *  - {@link #resolucionNueva} y {@link #tareaAsignada}: avisos inmediatos, en la
 *    misma transaccion del hecho que los origina.
 */
@Service
@RequiredArgsConstructor
public class GeneradorNotificaciones {

    private static final Logger log = LoggerFactory.getLogger(GeneradorNotificaciones.class);

    /** Lote maximo por pasada: si hay mas, la siguiente pasada sigue donde quedo esta. */
    private static final int LOTE = 500;

    /** Un plazo en estos estados ya no necesita aviso. */
    private static final Set<String> PLAZO_TERMINADO = Set.of("CUMPLIDO", "PRESENTADO", "CANCELADO");

    private final NamedParameterJdbcTemplate jdbc;
    private final NotificacionRepositoryJdbc repo;

    /**
     * Convierte en notificacion cada alerta PROGRAMADA cuya fecha ya llego.
     *
     * Es idempotente y segura ante dos ejecuciones simultaneas (dos instancias, o
     * una pasada que tarda mas que el intervalo): cada alerta se RECLAMA con un
     * UPDATE condicionado a que siga PROGRAMADA, y solo quien logra cambiarla
     * (1 fila) crea la notificacion. La reclamacion y la notificacion van en la
     * misma transaccion: si algo falla, ambas se deshacen y se reintenta.
     *
     * - Las de canal EMAIL no se tocan: no hay servicio de correo (decision del
     *   proyecto) y marcarlas ENVIADA seria mentir.
     * - Una alerta de un plazo ya cumplido, presentado o cancelado pasa a
     *   CANCELADA sin avisar: despertar a alguien por algo resuelto resta
     *   credibilidad a las que si importan.
     * - Si el destinatario ya no esta activo, tambien se cancela.
     *
     * @return cuantas notificaciones se crearon
     */
    @Transactional
    public int entregarAlertasVencidas() {
        List<Map<String, Object>> pendientes = jdbc.queryForList("""
                SELECT a.id, a.usuario_destino_id, a.expediente_id, a.plazo_id, a.tipo,
                       a.mensaje, a.severidad, p.estado plazo_estado, u.activo usuario_activo
                  FROM alertas a
                  JOIN usuarios u ON u.id = a.usuario_destino_id
                  LEFT JOIN plazos p ON p.id = a.plazo_id
                 WHERE a.estado = 'PROGRAMADA'
                   AND a.canal IN ('SISTEMA', 'AMBOS')
                   AND a.fecha_programada <= NOW(3)
                 ORDER BY a.fecha_programada, a.id
                 LIMIT :lote
                """, Map.of("lote", LOTE));

        int creadas = 0;
        for (Map<String, Object> fila : pendientes) {
            long id = ((Number) fila.get("id")).longValue();
            try {
                // Set.of(...).contains(null) lanza NPE: no toda alerta tiene plazo.
                String estadoPlazo = (String) fila.get("plazo_estado");
                boolean sinSentido = (estadoPlazo != null && PLAZO_TERMINADO.contains(estadoPlazo))
                        || !Boolean.TRUE.equals(toBoolean(fila.get("usuario_activo")));
                int reclamada = jdbc.update("""
                        UPDATE alertas
                           SET estado = :nuevo, fecha_envio = :envio
                         WHERE id = :id AND estado = 'PROGRAMADA'
                        """, new MapSqlParameterSource()
                        .addValue("nuevo", sinSentido ? "CANCELADA" : "ENVIADA")
                        .addValue("envio", sinSentido ? null : java.time.LocalDateTime.now())
                        .addValue("id", id));
                if (reclamada == 1 && !sinSentido) {
                    repo.crear(aNotificacion(fila, id));
                    creadas++;
                }
            } catch (DataAccessException e) {
                // Una alerta rota no debe frenar a las demas.
                log.warn("No se pudo entregar la alerta {}: {}", id, e.getMessage());
            }
        }
        if (creadas > 0) {
            log.info("Alertas: {} notificacion(es) entregada(s)", creadas);
        }
        return creadas;
    }

    /**
     * Avisa de una resolucion nueva al responsable del expediente y a su equipo
     * activo, salvo a quien la registro.
     */
    public int resolucionNueva(long expedienteId, String numeroResolucion, long actorId) {
        List<Map<String, Object>> datos = jdbc.queryForList(
                "SELECT codigo_interno FROM expedientes WHERE id = :id", Map.of("id", expedienteId));
        if (datos.isEmpty()) {
            return 0;
        }
        String codigo = (String) datos.get(0).get("codigo_interno");
        List<Long> destinos = jdbc.queryForList("""
                SELECT DISTINCT u.id
                  FROM usuarios u
                 WHERE u.activo = 1 AND u.tipo_usuario = 'INTERNO' AND u.id <> :actor
                   AND (u.id = (SELECT abogado_responsable_id FROM expedientes WHERE id = :exp)
                        OR EXISTS (SELECT 1 FROM expediente_equipo q
                                    WHERE q.expediente_id = :exp AND q.usuario_id = u.id AND q.activo = 1))
                """, new MapSqlParameterSource().addValue("actor", actorId).addValue("exp", expedienteId), Long.class);
        for (Long destino : destinos) {
            repo.crear(new CrearNotificacion(destino, "RESOLUCION", "Nueva resolución",
                    recortar("Se registró " + numeroResolucion + " en el expediente " + codigo + ".", 500),
                    "ADVERTENCIA", "resoluciones", null, expedienteId, "/monitor-judicial"));
        }
        return destinos.size();
    }

    /** Responsable actual de una tarea, o null si no existe. */
    public Long responsableDe(long tareaId) {
        List<Long> r = jdbc.queryForList("SELECT usuario_responsable_id FROM tareas WHERE id = :id",
                Map.of("id", tareaId), Long.class);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Avisa a quien recibe una tarea, salvo que se la haya asignado a si mismo. */
    public boolean tareaAsignada(long tareaId, long responsableId, long actorId) {
        if (responsableId == actorId) {
            return false;
        }
        List<Map<String, Object>> datos = jdbc.queryForList("""
                SELECT t.titulo, t.expediente_id, e.codigo_interno
                  FROM tareas t JOIN expedientes e ON e.id = t.expediente_id
                 WHERE t.id = :id
                """, Map.of("id", tareaId));
        if (datos.isEmpty()) {
            return false;
        }
        Map<String, Object> t = datos.get(0);
        repo.crear(new CrearNotificacion(responsableId, "TAREA", "Tarea asignada",
                recortar("Se te asignó «" + t.get("titulo") + "» en el expediente " + t.get("codigo_interno") + ".", 500),
                "INFO", "tareas", tareaId, ((Number) t.get("expediente_id")).longValue(), "/tareas"));
        return true;
    }

    private CrearNotificacion aNotificacion(Map<String, Object> a, long alertaId) {
        String tipo = (String) a.get("tipo");
        String[] visual = switch (tipo) {
            case "PLAZO_PROXIMO" -> new String[] {"PLAZO", "Plazo por vencer", "/plazos"};
            case "PLAZO_VENCIDO" -> new String[] {"PLAZO", "Plazo vencido", "/plazos"};
            case "AUDIENCIA" -> new String[] {"AUDIENCIA", "Audiencia próxima", "/agenda"};
            case "TAREA_VENCIDA" -> new String[] {"TAREA", "Tarea vencida", "/tareas"};
            case "RESOLUCION_NUEVA" -> new String[] {"RESOLUCION", "Nueva resolución", "/monitor-judicial"};
            case "CALENDARIO_PENDIENTE" -> new String[] {"CALENDARIO", "Calendario pendiente de aprobación", "/plazos"};
            case "CUANTIA_ALTA" -> new String[] {"ALERTA", "Cuantía alta", "/expedientes"};
            case "CONFLICTO_INTERES" -> new String[] {"ALERTA", "Posible conflicto de intereses", "/clientes"};
            default -> new String[] {"ALERTA", "Alerta", null};
        };
        String severidad = switch ((String) a.get("severidad")) {
            case "CRITICA" -> "CRITICA";
            case "ALTA" -> "ADVERTENCIA";
            default -> "INFO";
        };
        Number expediente = (Number) a.get("expediente_id");
        return new CrearNotificacion(((Number) a.get("usuario_destino_id")).longValue(), visual[0], visual[1],
                recortar((String) a.get("mensaje"), 500), severidad, "alertas", alertaId,
                expediente == null ? null : expediente.longValue(), visual[2]);
    }

    private static Boolean toBoolean(Object valor) {
        if (valor instanceof Boolean b) {
            return b;
        }
        return valor instanceof Number n && n.intValue() != 0;
    }

    private static String recortar(String texto, int max) {
        return texto == null || texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
    }
}
