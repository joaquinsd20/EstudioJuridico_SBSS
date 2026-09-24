package com.sbss.lexia.plazos.repository;

import com.sbss.lexia.plazos.dto.PlazoDtos.*;
import com.sbss.lexia.shared.security.SesionActual;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
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
public class PlazoRepositoryJdbc {

    private final NamedParameterJdbcTemplate jdbc;

    public List<PlazoLista> listar(String texto, Long expedienteId, Long responsableId, Long tipoPlazoId,
            String estado, String riesgo, String computo, Boolean requiereRevision, LocalDate desde, LocalDate hasta) {
        MapSqlParameterSource p = base();
        StringBuilder sql = new StringBuilder(selectBase()).append(" WHERE p.activo=1 AND e.activo=1 AND e.estudio_id=:estudioId");
        aplicarPortal(sql, p);
        if (texto != null && !texto.isBlank()) {
            sql.append(" AND LOWER(CONCAT_WS(' ',p.descripcion,p.actuacion_requerida,p.observaciones,e.codigo_interno,e.numero_expediente,c.nombre_busqueda,m.nombre,o.nombre,o.distrito_judicial,tp.nombre,ur.nombre_completo,uv.nombre_completo,r.numero_resolucion,a.asunto,d.nombre)) LIKE :texto");
            p.addValue("texto", "%" + texto.trim().toLowerCase() + "%");
        }
        if (expedienteId != null) { sql.append(" AND p.expediente_id=:expedienteId"); p.addValue("expedienteId", expedienteId); }
        if (responsableId != null) { sql.append(" AND p.usuario_responsable_id=:responsableId"); p.addValue("responsableId", responsableId); }
        if (tipoPlazoId != null) { sql.append(" AND p.tipo_plazo_id=:tipoPlazoId"); p.addValue("tipoPlazoId", tipoPlazoId); }
        if (estado != null && !estado.isBlank()) { sql.append(" AND ").append(estadoSql()).append("=:estado"); p.addValue("estado", estado); }
        if (riesgo != null && !riesgo.isBlank()) { sql.append(" AND p.nivel_riesgo=:riesgo"); p.addValue("riesgo", riesgo); }
        if (computo != null && !computo.isBlank()) { sql.append(" AND p.tipo_computo=:computo"); p.addValue("computo", computo); }
        if (requiereRevision != null) { sql.append(" AND p.requiere_revision_profesional=:revision"); p.addValue("revision", requiereRevision); }
        if (desde != null) { sql.append(" AND p.fecha_vencimiento>=:desde"); p.addValue("desde", desde); }
        if (hasta != null) { sql.append(" AND p.fecha_vencimiento<=:hasta"); p.addValue("hasta", hasta); }
        sql.append(" ORDER BY CASE WHEN ").append(estadoSql()).append("='VENCIDO' THEN 0 ELSE 1 END, p.fecha_vencimiento, p.id DESC");
        return jdbc.query(sql.toString(), p, this::mapLista);
    }

    public Resumen resumen() {
        MapSqlParameterSource p = base();
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) total_activos,
                       SUM(CASE WHEN %s='VENCIDO' THEN 1 ELSE 0 END) vencidos,
                       SUM(CASE WHEN p.fecha_vencimiento=CURRENT_DATE AND %s NOT IN ('CUMPLIDO','CANCELADO') THEN 1 ELSE 0 END) vence_hoy,
                       SUM(CASE WHEN p.fecha_vencimiento BETWEEN CURRENT_DATE AND DATE_ADD(CURRENT_DATE, INTERVAL 7 DAY)
                                AND %s NOT IN ('CUMPLIDO','CANCELADO') THEN 1 ELSE 0 END) proximos_siete,
                       SUM(CASE WHEN p.nivel_riesgo='CRITICO' AND %s NOT IN ('CUMPLIDO','CANCELADO') THEN 1 ELSE 0 END) criticos,
                       SUM(CASE WHEN p.requiere_revision_profesional=1 AND %s NOT IN ('CUMPLIDO','CANCELADO') THEN 1 ELSE 0 END) revision
                  FROM plazos p JOIN expedientes e ON e.id=p.expediente_id
                 WHERE p.activo=1 AND e.activo=1 AND e.estudio_id=:estudioId
                """.formatted(estadoSql(), estadoSql(), estadoSql(), estadoSql(), estadoSql()));
        aplicarPortal(sql, p);
        return jdbc.queryForObject(sql.toString(), p, (rs, n) -> new Resumen(
                rs.getLong("total_activos"), rs.getLong("vencidos"), rs.getLong("vence_hoy"),
                rs.getLong("proximos_siete"), rs.getLong("criticos"), rs.getLong("revision")));
    }

    public PlazoDetalle detalle(String uuid) {
        MapSqlParameterSource p = base().addValue("uuid", uuid);
        StringBuilder sql = new StringBuilder(selectBase()).append(" WHERE p.uuid_publico=:uuid AND p.activo=1 AND e.activo=1 AND e.estudio_id=:estudioId");
        aplicarPortal(sql, p);
        List<PlazoLista> filas = jdbc.query(sql.toString(), p, this::mapLista);
        if (filas.isEmpty()) return null;
        Long id = idPorUuid(uuid);
        return new PlazoDetalle(filas.getFirst(), alertas(id));
    }

    public Catalogos catalogos() {
        return new Catalogos(expedientes(), usuarios(), tiposPlazo());
    }

    public Relaciones relaciones(Long expedienteId) {
        return new Relaciones(resoluciones(expedienteId), actuaciones(expedienteId), documentos(expedienteId));
    }

    public TipoPlazoOpcion tipoPlazo(Long id) {
        if (id == null) return null;
        List<TipoPlazoOpcion> filas = jdbc.query("""
                SELECT id,codigo,nombre,dias_por_defecto,tipo_computo_defecto,nivel_riesgo_defecto
                  FROM tipos_plazo WHERE id=:id AND estudio_id=:estudioId AND activo=1
                """, Map.of("id", id, "estudioId", SesionActual.estudioId()), (rs, n) -> mapTipo(rs));
        return filas.isEmpty() ? null : filas.getFirst();
    }

    public boolean expedienteExiste(Long id) {
        MapSqlParameterSource p = base().addValue("id", id);
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM expedientes e WHERE e.id=:id AND e.activo=1 AND e.estudio_id=:estudioId");
        aplicarPortal(sql, p);
        Integer n = jdbc.queryForObject(sql.toString(), p, Integer.class);
        return n != null && n > 0;
    }

    public boolean usuarioValido(Long id) {
        if (id == null) return true;
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM usuarios
                 WHERE id=:id AND estudio_id=:estudioId AND activo=1 AND estado='ACTIVO' AND tipo_usuario='INTERNO'
                """, Map.of("id", id, "estudioId", SesionActual.estudioId()), Integer.class);
        return n != null && n > 0;
    }

    public boolean resolucionValida(Long id, Long expedienteId) {
        if (id == null) return true;
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM resoluciones r JOIN expedientes e ON e.id=r.expediente_id
                 WHERE r.id=:id AND r.expediente_id=:expedienteId AND r.activo=1 AND e.estudio_id=:estudioId
                """, Map.of("id", id, "expedienteId", expedienteId, "estudioId", SesionActual.estudioId()), Integer.class);
        return n != null && n > 0;
    }

    public boolean actuacionValida(Long id, Long expedienteId) {
        if (id == null) return true;
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM actuaciones a JOIN expedientes e ON e.id=a.expediente_id
                 WHERE a.id=:id AND a.expediente_id=:expedienteId AND a.activo=1 AND e.estudio_id=:estudioId
                """, Map.of("id", id, "expedienteId", expedienteId, "estudioId", SesionActual.estudioId()), Integer.class);
        return n != null && n > 0;
    }

    public boolean documentoValido(Long id, Long expedienteId) {
        if (id == null) return true;
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM documentos d JOIN expedientes e ON e.id=d.expediente_id
                 WHERE d.id=:id AND d.expediente_id=:expedienteId AND d.activo=1 AND d.en_papelera=0 AND e.estudio_id=:estudioId
                """, Map.of("id", id, "expedienteId", expedienteId, "estudioId", SesionActual.estudioId()), Integer.class);
        return n != null && n > 0;
    }

    public String distritoJudicial(Long expedienteId) {
        List<String> filas = jdbc.query("""
                SELECT o.distrito_judicial FROM expedientes e
                LEFT JOIN organos_jurisdiccionales o ON o.id=e.organo_id
                WHERE e.id=:id AND e.estudio_id=:estudioId
                """, Map.of("id", expedienteId, "estudioId", SesionActual.estudioId()), (rs, n) -> rs.getString(1));
        return filas.isEmpty() ? null : filas.getFirst();
    }

    public List<String> incidenciasCalendario(LocalDate desde, LocalDate hasta, String ambito) {
        MapSqlParameterSource p = base().addValue("desde", desde).addValue("hasta", hasta).addValue("ambito", ambito);
        return jdbc.query("""
                SELECT CONCAT(c.descripcion,
                       CASE WHEN c.tipo='SUSPENSION_PLAZOS' THEN ' (suspension de plazos)'
                            WHEN c.tipo='HUELGA_JUDICIAL' THEN ' (huelga judicial)'
                            WHEN c.tipo='DIA_NO_LABORABLE' THEN ' (dia no laborable)'
                            ELSE ' (dia especial)' END)
                  FROM calendario_judicial c
                 WHERE c.estado='APROBADO'
                   AND c.fecha_inicio<=:hasta AND c.fecha_fin>=:desde
                   AND (c.ambito IS NULL OR c.ambito=:ambito)
                 ORDER BY c.fecha_inicio
                """, p, (rs, n) -> rs.getString(1));
    }

    public Integer valorConfigNumero(String clave, Integer defecto) {
        try {
            List<String> filas = jdbc.query("SELECT valor FROM configuracion WHERE estudio_id=:estudioId AND clave=:clave",
                    Map.of("estudioId", SesionActual.estudioId(), "clave", clave), (rs, n) -> rs.getString(1));
            return filas.isEmpty() ? defecto : Integer.valueOf(filas.getFirst().trim());
        } catch (Exception e) {
            return defecto;
        }
    }

    public Long insertar(GuardarRequest r, LocalDate inicio, LocalDate vencimiento, LocalDate interna,
            boolean requiereRevision, String motivoRevision, String estado) {
        MapSqlParameterSource p = parametrosGuardado(r, inicio, vencimiento, interna, requiereRevision, motivoRevision, estado)
                .addValue("creadorId", SesionActual.usuarioId());
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO plazos(expediente_id,actuacion_origen_id,resolucion_id,tipo_plazo_id,descripcion,origen,
                  fecha_notificacion,fecha_inicio_computo,dias_plazo,tipo_computo,fecha_vencimiento,fecha_vencimiento_interna,
                  usuario_responsable_id,usuario_revisor_id,estado,nivel_riesgo,requiere_revision_profesional,motivo_revision,
                  actuacion_requerida,documento_resultante_id,observaciones,fecha_cumplimiento,usuario_creacion_id)
                VALUES(:expedienteId,:actuacionId,:resolucionId,:tipoPlazoId,:descripcion,:origen,:fechaNotif,:inicio,:dias,:computo,
                  :vencimiento,:interna,:responsableId,:revisorId,:estado,:riesgo,:requiere,:motivo,:actuacionRequerida,:documentoId,
                  :observaciones,CASE WHEN :estado='CUMPLIDO' THEN NOW(3) ELSE NULL END,:creadorId)
                """, p, key, new String[]{"id"});
        Number id = key.getKey();
        if (id == null) throw new IllegalStateException("No se obtuvo id generado para el plazo");
        return id.longValue();
    }

    public void actualizar(Long id, GuardarRequest r, LocalDate inicio, LocalDate vencimiento, LocalDate interna,
            boolean requiereRevision, String motivoRevision, String estado) {
        MapSqlParameterSource p = parametrosGuardado(r, inicio, vencimiento, interna, requiereRevision, motivoRevision, estado)
                .addValue("id", id).addValue("usuarioId", SesionActual.usuarioId());
        jdbc.update("""
                UPDATE plazos SET expediente_id=:expedienteId,actuacion_origen_id=:actuacionId,resolucion_id=:resolucionId,
                  tipo_plazo_id=:tipoPlazoId,descripcion=:descripcion,origen=:origen,fecha_notificacion=:fechaNotif,
                  fecha_inicio_computo=:inicio,dias_plazo=:dias,tipo_computo=:computo,fecha_vencimiento=:vencimiento,
                  fecha_vencimiento_interna=:interna,usuario_responsable_id=:responsableId,usuario_revisor_id=:revisorId,
                  estado=:estado,nivel_riesgo=:riesgo,requiere_revision_profesional=:requiere,motivo_revision=:motivo,
                  actuacion_requerida=:actuacionRequerida,documento_resultante_id=:documentoId,observaciones=:observaciones,
                  fecha_cumplimiento=CASE WHEN :estado='CUMPLIDO' THEN COALESCE(fecha_cumplimiento,NOW(3)) ELSE NULL END,
                  usuario_modificacion_id=:usuarioId
                WHERE id=:id
                """, p);
    }

    public void cambiarEstado(Long id, String estado) {
        Map<String,Object> p = Map.of("estado", estado, "usuarioId", SesionActual.usuarioId(), "id", id);
        jdbc.update("""
                UPDATE plazos SET estado=:estado,
                  fecha_cumplimiento=CASE WHEN :estado='CUMPLIDO' THEN COALESCE(fecha_cumplimiento,NOW(3)) ELSE fecha_cumplimiento END,
                  usuario_modificacion_id=:usuarioId WHERE id=:id
                """, p);
        jdbc.update("""
                UPDATE eventos_agenda
                   SET estado=CASE WHEN :estado='CANCELADO' THEN 'CANCELADO'
                                   WHEN :estado IN ('PRESENTADO','CUMPLIDO') THEN 'REALIZADO'
                                   ELSE estado END,
                       usuario_modificacion_id=:usuarioId
                 WHERE plazo_id=:id AND activo=1
                """, p);
        if ("CUMPLIDO".equals(estado) || "CANCELADO".equals(estado)) {
            jdbc.update("UPDATE alertas SET estado='CANCELADA' WHERE plazo_id=:id AND estado='PROGRAMADA'", Map.of("id", id));
        }
    }

    public String uuidPorId(Long id) {
        List<String> filas = jdbc.query("""
                SELECT p.uuid_publico FROM plazos p JOIN expedientes e ON e.id=p.expediente_id
                 WHERE p.id=:id AND p.activo=1 AND e.estudio_id=:estudioId
                """, Map.of("id", id, "estudioId", SesionActual.estudioId()), (rs, n) -> rs.getString(1));
        return filas.isEmpty() ? null : filas.getFirst();
    }

    public Long idPorUuid(String uuid) {
        MapSqlParameterSource p = base().addValue("uuid", uuid);
        StringBuilder sql = new StringBuilder("""
                SELECT p.id FROM plazos p JOIN expedientes e ON e.id=p.expediente_id
                 WHERE p.uuid_publico=:uuid AND p.activo=1 AND e.estudio_id=:estudioId
                """);
        aplicarPortal(sql, p);
        List<Long> filas = jdbc.query(sql.toString(), p, (rs, n) -> rs.getLong(1));
        return filas.isEmpty() ? null : filas.getFirst();
    }

    public void eliminarAlertasProgramadas(Long plazoId) {
        jdbc.update("DELETE FROM alertas WHERE plazo_id=:plazoId AND estado='PROGRAMADA'", Map.of("plazoId", plazoId));
    }

    public void insertarAlerta(Long plazoId, Long expedienteId, Long responsableId, Integer diasAntes,
            String mensaje, String severidad, LocalDateTime fechaProgramada) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("estudioId", SesionActual.estudioId()).addValue("plazoId", plazoId)
                .addValue("expedienteId", expedienteId).addValue("usuarioId", responsableId)
                .addValue("tipo", diasAntes < 0 ? "PLAZO_VENCIDO" : "PLAZO_PROXIMO")
                .addValue("dias", diasAntes).addValue("mensaje", mensaje).addValue("severidad", severidad)
                .addValue("fecha", fechaProgramada);
        jdbc.update("""
                INSERT INTO alertas(estudio_id,plazo_id,expediente_id,usuario_destino_id,tipo,dias_antes,mensaje,severidad,fecha_programada,canal,estado)
                VALUES(:estudioId,:plazoId,:expedienteId,:usuarioId,:tipo,:dias,:mensaje,:severidad,:fecha,'SISTEMA','PROGRAMADA')
                """, p);
    }

    public void sincronizarEventoAgenda(Long plazoId, Long expedienteId, Long actuacionId, String descripcion,
            LocalDate vencimiento, boolean requiereRevision) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("estudioId", SesionActual.estudioId()).addValue("plazoId", plazoId)
                .addValue("expedienteId", expedienteId).addValue("actuacionId", actuacionId)
                .addValue("titulo", "Vence: " + descripcion).addValue("descripcion", "Vencimiento oficial del plazo juridico")
                .addValue("inicio", vencimiento.atStartOfDay()).addValue("fin", vencimiento.atTime(23, 59))
                .addValue("requiere", requiereRevision).addValue("usuarioId", SesionActual.usuarioId());
        Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM eventos_agenda WHERE plazo_id=:plazoId AND activo=1",
                Map.of("plazoId", plazoId), Integer.class);
        if (existe != null && existe > 0) {
            jdbc.update("""
                    UPDATE eventos_agenda SET expediente_id=:expedienteId,actuacion_id=:actuacionId,titulo=:titulo,descripcion=:descripcion,
                      fecha_inicio=:inicio,fecha_fin=:fin,todo_el_dia=1,estado=CASE WHEN estado='CANCELADO' THEN 'PROGRAMADO' ELSE estado END,
                      origen_informacion='MANUAL',requiere_verificacion=:requiere,usuario_modificacion_id=:usuarioId
                    WHERE plazo_id=:plazoId AND activo=1
                    """, p);
        } else {
            jdbc.update("""
                    INSERT INTO eventos_agenda(estudio_id,expediente_id,actuacion_id,plazo_id,tipo,titulo,descripcion,fecha_inicio,fecha_fin,
                      todo_el_dia,modalidad,estado,recordatorio_minutos,color_hex,origen_informacion,requiere_verificacion,usuario_creacion_id)
                    VALUES(:estudioId,:expedienteId,:actuacionId,:plazoId,'VENCIMIENTO_PLAZO',:titulo,:descripcion,:inicio,:fin,1,
                      'PRESENCIAL','PROGRAMADO',1440,'#DC2626','MANUAL',:requiere,:usuarioId)
                    """, p);
        }
    }

    private List<Alerta> alertas(Long plazoId) {
        if (plazoId == null) return List.of();
        return jdbc.query("""
                SELECT id,tipo,dias_antes,mensaje,severidad,fecha_programada,estado
                  FROM alertas WHERE plazo_id=:plazoId ORDER BY fecha_programada
                """, Map.of("plazoId", plazoId), (rs, n) -> new Alerta(
                rs.getLong("id"), rs.getString("tipo"), getInt(rs, "dias_antes"), rs.getString("mensaje"),
                rs.getString("severidad"), rs.getTimestamp("fecha_programada").toLocalDateTime(), rs.getString("estado")));
    }

    private List<ExpedienteOpcion> expedientes() {
        MapSqlParameterSource p = base();
        StringBuilder sql = new StringBuilder("""
                SELECT e.id,e.codigo_interno,e.numero_expediente,c.nombre_busqueda,u.id responsable_id,u.nombre_completo responsable,
                       o.distrito_judicial
                  FROM expedientes e JOIN clientes c ON c.id=e.cliente_id JOIN usuarios u ON u.id=e.abogado_responsable_id
                  LEFT JOIN organos_jurisdiccionales o ON o.id=e.organo_id
                 WHERE e.estudio_id=:estudioId AND e.activo=1
                """);
        aplicarPortal(sql, p);
        sql.append(" ORDER BY e.codigo_interno DESC");
        return jdbc.query(sql.toString(), p, (rs, n) -> new ExpedienteOpcion(
                rs.getLong("id"), rs.getString("codigo_interno"), rs.getString("numero_expediente"),
                rs.getString("nombre_busqueda"), rs.getLong("responsable_id"), rs.getString("responsable"), rs.getString("distrito_judicial")));
    }

    private List<UsuarioOpcion> usuarios() {
        return jdbc.query("""
                SELECT id,nombre_completo,cargo FROM usuarios
                 WHERE estudio_id=:estudioId AND activo=1 AND estado='ACTIVO' AND tipo_usuario='INTERNO'
                 ORDER BY nombre_completo
                """, Map.of("estudioId", SesionActual.estudioId()), (rs, n) -> new UsuarioOpcion(rs.getLong(1), rs.getString(2), rs.getString(3)));
    }

    private List<TipoPlazoOpcion> tiposPlazo() {
        return jdbc.query("""
                SELECT id,codigo,nombre,dias_por_defecto,tipo_computo_defecto,nivel_riesgo_defecto
                  FROM tipos_plazo WHERE estudio_id=:estudioId AND activo=1 ORDER BY nombre
                """, Map.of("estudioId", SesionActual.estudioId()), (rs, n) -> mapTipo(rs));
    }

    private List<ResolucionOpcion> resoluciones(Long expedienteId) {
        return jdbc.query("""
                SELECT r.id,r.numero_resolucion,r.tipo_resolucion,r.fecha_resolucion,r.fecha_notificacion,r.sumilla,r.origen,r.estado_verificacion
                  FROM resoluciones r JOIN expedientes e ON e.id=r.expediente_id
                 WHERE r.expediente_id=:expedienteId AND r.activo=1 AND e.estudio_id=:estudioId
                 ORDER BY r.fecha_notificacion DESC,r.id DESC
                """, Map.of("expedienteId", expedienteId, "estudioId", SesionActual.estudioId()), (rs, n) -> new ResolucionOpcion(
                rs.getLong("id"), rs.getString("numero_resolucion"), rs.getString("tipo_resolucion"),
                getDate(rs, "fecha_resolucion"), rs.getDate("fecha_notificacion").toLocalDate(), rs.getString("sumilla"),
                rs.getString("origen"), rs.getString("estado_verificacion")));
    }

    private List<ActuacionOpcion> actuaciones(Long expedienteId) {
        return jdbc.query("""
                SELECT a.id,a.fecha_hora,ta.nombre tipo,a.asunto FROM actuaciones a
                JOIN tipos_actuacion ta ON ta.id=a.tipo_actuacion_id JOIN expedientes e ON e.id=a.expediente_id
                 WHERE a.expediente_id=:expedienteId AND a.activo=1 AND e.estudio_id=:estudioId
                 ORDER BY a.fecha_hora DESC,a.id DESC
                """, Map.of("expedienteId", expedienteId, "estudioId", SesionActual.estudioId()), (rs, n) -> new ActuacionOpcion(
                rs.getLong("id"), rs.getTimestamp("fecha_hora").toLocalDateTime(), rs.getString("tipo"), rs.getString("asunto")));
    }

    private List<DocumentoOpcion> documentos(Long expedienteId) {
        return jdbc.query("""
                SELECT d.id,d.uuid_publico,d.nombre,d.categoria,d.estado FROM documentos d
                JOIN expedientes e ON e.id=d.expediente_id
                 WHERE d.expediente_id=:expedienteId AND d.activo=1 AND d.en_papelera=0 AND e.estudio_id=:estudioId
                 ORDER BY d.fecha_creacion DESC,d.id DESC
                """, Map.of("expedienteId", expedienteId, "estudioId", SesionActual.estudioId()), (rs, n) -> new DocumentoOpcion(
                rs.getLong("id"), rs.getString("uuid_publico"), rs.getString("nombre"), rs.getString("categoria"), rs.getString("estado")));
    }

    private String selectBase() {
        return """
                SELECT p.uuid_publico,p.origen,e.id expediente_id,e.codigo_interno expediente_codigo,e.numero_expediente,
                       c.nombre_busqueda cliente_nombre,m.nombre materia,o.nombre organo,o.distrito_judicial,tp.id tipo_plazo_id,tp.codigo tipo_codigo,tp.nombre tipo_nombre,p.descripcion,
                       p.fecha_notificacion,p.fecha_inicio_computo,p.dias_plazo,p.tipo_computo,p.fecha_vencimiento,
                       p.fecha_vencimiento_interna,DATEDIFF(p.fecha_vencimiento,CURRENT_DATE) dias_restantes,
                       ur.id responsable_id,ur.nombre_completo responsable,uv.id revisor_id,uv.nombre_completo revisor,
                       %s estado,p.nivel_riesgo,p.requiere_revision_profesional,p.motivo_revision,p.actuacion_requerida,
                       r.id resolucion_id,r.numero_resolucion,a.id actuacion_origen_id,a.asunto actuacion_asunto,
                       d.id documento_id,d.nombre documento_nombre,p.observaciones,
                       (SELECT COUNT(*) FROM alertas al WHERE al.plazo_id=p.id AND al.estado<>'CANCELADA') alertas
                  FROM plazos p
                  JOIN expedientes e ON e.id=p.expediente_id
                  JOIN clientes c ON c.id=e.cliente_id
                  JOIN tipos_plazo tp ON tp.id=p.tipo_plazo_id
                  JOIN usuarios ur ON ur.id=p.usuario_responsable_id
                  LEFT JOIN materias m ON m.id=e.materia_id
                  LEFT JOIN organos_jurisdiccionales o ON o.id=e.organo_id
                  LEFT JOIN usuarios uv ON uv.id=p.usuario_revisor_id
                  LEFT JOIN resoluciones r ON r.id=p.resolucion_id
                  LEFT JOIN actuaciones a ON a.id=p.actuacion_origen_id
                  LEFT JOIN documentos d ON d.id=p.documento_resultante_id
                """.formatted(estadoSql());
    }

    private String estadoSql() {
        return "CASE WHEN p.fecha_vencimiento<CURRENT_DATE AND p.estado IN ('PENDIENTE_CALCULO','CALCULADO','EN_PREPARACION','EN_REVISION','LISTO_PRESENTAR') THEN 'VENCIDO' ELSE p.estado END";
    }

    private MapSqlParameterSource parametrosGuardado(GuardarRequest r, LocalDate inicio, LocalDate vencimiento, LocalDate interna,
            boolean requiereRevision, String motivoRevision, String estado) {
        return new MapSqlParameterSource()
                .addValue("expedienteId", r.expedienteId()).addValue("actuacionId", r.actuacionOrigenId())
                .addValue("resolucionId", r.resolucionId()).addValue("tipoPlazoId", r.tipoPlazoId())
                .addValue("descripcion", r.descripcion().trim()).addValue("origen", r.origen().trim().toUpperCase())
                .addValue("fechaNotif", r.fechaNotificacion()).addValue("inicio", inicio).addValue("dias", r.diasPlazo())
                .addValue("computo", r.tipoComputo().trim().toUpperCase()).addValue("vencimiento", vencimiento).addValue("interna", interna)
                .addValue("responsableId", r.responsableId()).addValue("revisorId", r.revisorId()).addValue("estado", estado)
                .addValue("riesgo", r.nivelRiesgo().trim().toUpperCase()).addValue("requiere", requiereRevision).addValue("motivo", motivoRevision)
                .addValue("actuacionRequerida", limpiar(r.actuacionRequerida())).addValue("documentoId", r.documentoId())
                .addValue("observaciones", limpiar(r.observaciones()));
    }

    private PlazoLista mapLista(ResultSet rs, int n) throws SQLException {
        return new PlazoLista(
                rs.getString("uuid_publico"), rs.getString("origen"), rs.getLong("expediente_id"), rs.getString("expediente_codigo"),
                rs.getString("numero_expediente"), rs.getString("cliente_nombre"), rs.getString("materia"), rs.getString("organo"),
                rs.getString("distrito_judicial"), rs.getLong("tipo_plazo_id"), rs.getString("tipo_codigo"),
                rs.getString("tipo_nombre"), rs.getString("descripcion"), rs.getDate("fecha_notificacion").toLocalDate(),
                rs.getDate("fecha_inicio_computo").toLocalDate(), rs.getInt("dias_plazo"), rs.getString("tipo_computo"),
                rs.getDate("fecha_vencimiento").toLocalDate(), getDate(rs, "fecha_vencimiento_interna"), rs.getInt("dias_restantes"),
                rs.getLong("responsable_id"), rs.getString("responsable"), getLong(rs, "revisor_id"), rs.getString("revisor"),
                rs.getString("estado"), rs.getString("nivel_riesgo"), rs.getBoolean("requiere_revision_profesional"),
                rs.getString("motivo_revision"), rs.getString("actuacion_requerida"), getLong(rs, "resolucion_id"),
                rs.getString("numero_resolucion"), getLong(rs, "actuacion_origen_id"), rs.getString("actuacion_asunto"),
                getLong(rs, "documento_id"), rs.getString("documento_nombre"), rs.getString("observaciones"), rs.getInt("alertas"));
    }

    private TipoPlazoOpcion mapTipo(ResultSet rs) throws SQLException {
        return new TipoPlazoOpcion(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"), getInt(rs, "dias_por_defecto"),
                rs.getString("tipo_computo_defecto"), rs.getString("nivel_riesgo_defecto"));
    }

    private MapSqlParameterSource base() { return new MapSqlParameterSource().addValue("estudioId", SesionActual.estudioId()); }

    private void aplicarPortal(StringBuilder sql, MapSqlParameterSource p) {
        if (SesionActual.esPortalCliente()) { sql.append(" AND e.cliente_id=:clienteId"); p.addValue("clienteId", SesionActual.clienteId()); }
    }

    private static Long getLong(ResultSet rs, String c) throws SQLException { return rs.getObject(c) == null ? null : rs.getLong(c); }
    private static Integer getInt(ResultSet rs, String c) throws SQLException { return rs.getObject(c) == null ? null : rs.getInt(c); }
    private static LocalDate getDate(ResultSet rs, String c) throws SQLException { return rs.getDate(c) == null ? null : rs.getDate(c).toLocalDate(); }
    private static String limpiar(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
