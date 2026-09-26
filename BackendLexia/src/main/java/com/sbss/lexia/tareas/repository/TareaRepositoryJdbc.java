package com.sbss.lexia.tareas.repository;

import com.sbss.lexia.shared.security.SesionActual;
import com.sbss.lexia.shared.security.VisibilidadExpedientes;
import com.sbss.lexia.tareas.dto.TareaDtos.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TareaRepositoryJdbc {
    private final NamedParameterJdbcTemplate jdbc;

    public List<TareaLista> listar(String texto, String estado, String prioridad, Long responsableId,
                                   Long expedienteId, LocalDateTime desde, LocalDateTime hasta,
                                   int page, int size) {
        MapSqlParameterSource p = base();
        String where = construirFiltros(p, texto, estado, prioridad, responsableId, expedienteId, desde, hasta);
        p.addValue("limit", size).addValue("offset", (long) page * size);
        String sql = baseSelect() + where + orden() + " LIMIT :limit OFFSET :offset";
        return jdbc.query(sql, p, (rs,n) -> mapLista(rs));
    }

    public List<ExportacionFila> listarExportacion(String texto, String estado, String prioridad, Long responsableId,
                                              Long expedienteId, LocalDateTime desde, LocalDateTime hasta, int limit) {
        MapSqlParameterSource p = base();
        String where = construirFiltros(p, texto, estado, prioridad, responsableId, expedienteId, desde, hasta);
        p.addValue("limit", limit);
        return jdbc.query(exportSelect() + where + orden() + " LIMIT :limit", p, (rs,n) -> mapExport(rs));
    }

    public long contar(String texto, String estado, String prioridad, Long responsableId,
                       Long expedienteId, LocalDateTime desde, LocalDateTime hasta) {
        MapSqlParameterSource p = base();
        String where = construirFiltros(p, texto, estado, prioridad, responsableId, expedienteId, desde, hasta);
        Long total = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM tareas t
            JOIN expedientes e ON e.id=t.expediente_id
            JOIN usuarios u ON u.id=t.usuario_responsable_id
            """ + where, p, Long.class);
        return total == null ? 0 : total;
    }

    public Resumen resumen() {
        return jdbc.queryForObject("""
            SELECT COUNT(*) total,
              COALESCE(SUM(t.estado='PENDIENTE'),0) pendientes,
              COALESCE(SUM(t.estado IN ('EN_PROCESO','OBSERVADA')),0) proceso,
              COALESCE(SUM(t.estado IN ('EN_REVISION','APROBADA')),0) revision,
              COALESCE(SUM(t.estado='VENCIDA' OR (t.fecha_limite<NOW() AND t.estado NOT IN ('COMPLETADA','CANCELADA'))),0) vencidas,
              COALESCE(SUM(t.estado='COMPLETADA'),0) completadas
            FROM tareas t JOIN expedientes e ON e.id=t.expediente_id
            WHERE e.estudio_id=:estudioId AND t.activo=1
            """ + visibilidad(), base(), (rs,n) -> new Resumen(rs.getLong("pendientes"), rs.getLong("proceso"),
                    rs.getLong("revision"), rs.getLong("vencidas"), rs.getLong("completadas"), rs.getLong("total")));
    }

    public Catalogos catalogos() {
        List<ExpedienteOpcion> expedientes = jdbc.query("""
            SELECT e.id,e.codigo_interno,e.numero_expediente,c.nombre_busqueda
            FROM expedientes e JOIN clientes c ON c.id=e.cliente_id
            WHERE e.estudio_id=:estudioId AND e.activo=1
            """ + visibilidad() + """
            ORDER BY e.codigo_interno DESC
            """, base(), (rs,n) -> new ExpedienteOpcion(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)));
        List<UsuarioOpcion> usuarios = jdbc.query("""
            SELECT id,nombre_completo,cargo FROM usuarios
            WHERE estudio_id=:estudioId AND activo=1 AND estado='ACTIVO' AND tipo_usuario='INTERNO'
            ORDER BY nombre_completo
            """, base(), (rs,n) -> new UsuarioOpcion(rs.getLong(1),rs.getString(2),rs.getString(3)));
        return new Catalogos(expedientes, usuarios);
    }

    public TareaInfo tareaInfo(String uuid) {
        MapSqlParameterSource p=base().addValue("uuid",uuid);
        List<TareaInfo> r=jdbc.query(detailSelect()+" WHERE e.estudio_id=:estudioId AND t.uuid_publico=:uuid AND t.activo=1"+visibilidad(),p,(rs,n)->mapInfo(rs));
        return r.isEmpty()?null:r.get(0);
    }

    public Long idPorUuid(String uuid) {
        MapSqlParameterSource p=base().addValue("uuid",uuid);
        List<Long> r=jdbc.query("SELECT t.id FROM tareas t JOIN expedientes e ON e.id=t.expediente_id WHERE e.estudio_id=:estudioId AND t.uuid_publico=:uuid AND t.activo=1"+visibilidad(),p,(rs,n)->rs.getLong(1));
        return r.isEmpty()?null:r.get(0);
    }

    public String uuidPorId(Long id) {
        MapSqlParameterSource p=base().addValue("id",id);
        List<String> r=jdbc.query("SELECT t.uuid_publico FROM tareas t JOIN expedientes e ON e.id=t.expediente_id WHERE e.estudio_id=:estudioId AND t.id=:id AND t.activo=1"+visibilidad(),p,(rs,n)->rs.getString(1));
        return r.isEmpty()?null:r.get(0);
    }

    public boolean expedienteValido(Long id) {
        // Con la regla de visibilidad: si no lo puede ver, no puede colgarle
        // una tarea. Antes bastaba con que fuera del mismo estudio.
        return existe("SELECT COUNT(*) FROM expedientes e WHERE e.id=:id AND e.estudio_id=:estudioId"
                + " AND e.activo=1" + visibilidad(), id);
    }
    public boolean usuarioValido(Long id) { return id == null || existe("SELECT COUNT(*) FROM usuarios WHERE id=:id AND estudio_id=:estudioId AND activo=1 AND estado='ACTIVO'", id); }

    public Long insertar(GuardarRequest r) {
        MapSqlParameterSource p=base().addValue("expedienteId",r.expedienteId()).addValue("titulo",r.titulo().trim())
                .addValue("descripcion",r.descripcion()).addValue("responsableId",r.responsableId()).addValue("supervisorId",r.supervisorId())
                .addValue("prioridad",r.prioridad()).addValue("fechaLimite",r.fechaLimite()).addValue("estimado",r.tiempoEstimadoMinutos())
                .addValue("usuarioId",SesionActual.usuarioId());
        jdbc.update("""
            INSERT INTO tareas(expediente_id,titulo,descripcion,usuario_responsable_id,usuario_supervisor_id,prioridad,estado,etapa_flujo,
              fecha_limite,tiempo_estimado_minutos,porcentaje_avance,usuario_creacion_id)
            VALUES(:expedienteId,:titulo,:descripcion,:responsableId,:supervisorId,:prioridad,'PENDIENTE','ELABORAR',:fechaLimite,:estimado,0,:usuarioId)
            """,p);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Map.of(),Long.class);
    }

    public void actualizar(Long id, GuardarRequest r) {
        MapSqlParameterSource p=base().addValue("id",id).addValue("expedienteId",r.expedienteId()).addValue("titulo",r.titulo().trim())
                .addValue("descripcion",r.descripcion()).addValue("responsableId",r.responsableId()).addValue("supervisorId",r.supervisorId())
                .addValue("prioridad",r.prioridad()).addValue("fechaLimite",r.fechaLimite()).addValue("estimado",r.tiempoEstimadoMinutos())
                .addValue("usuarioId",SesionActual.usuarioId());
        jdbc.update("""
            UPDATE tareas t JOIN expedientes e ON e.id=t.expediente_id
            SET t.expediente_id=:expedienteId,t.titulo=:titulo,t.descripcion=:descripcion,t.usuario_responsable_id=:responsableId,
              t.usuario_supervisor_id=:supervisorId,t.prioridad=:prioridad,t.fecha_limite=:fechaLimite,t.tiempo_estimado_minutos=:estimado,
              t.usuario_modificacion_id=:usuarioId
            WHERE t.id=:id AND e.estudio_id=:estudioId
            """ + visibilidad() + """
            """,p);
    }

    public void reemplazarSubtareas(Long tareaId, List<SubtareaRequest> subtareas) {
        jdbc.update("DELETE FROM subtareas WHERE tarea_id=:id",Map.of("id",tareaId));
        if(subtareas==null) return;
        int orden=0;
        for(SubtareaRequest s:subtareas){
            if(s==null||s.titulo()==null||s.titulo().isBlank()) continue;
            jdbc.update("INSERT INTO subtareas(tarea_id,titulo,orden,usuario_responsable_id) VALUES(:t,:titulo,:orden,:r)",
                    new MapSqlParameterSource().addValue("t",tareaId).addValue("titulo",s.titulo().trim()).addValue("orden",orden++).addValue("r",s.responsableId()));
        }
    }

    public void cambiarFlujo(Long id,String estado,String etapa,String observacion) {
        MapSqlParameterSource p=base().addValue("id",id).addValue("estado",estado).addValue("etapa",etapa).addValue("usuarioId",SesionActual.usuarioId());
        Map<String,Object> anterior=jdbc.queryForMap("SELECT estado,etapa_flujo FROM tareas WHERE id=:id",Map.of("id",id));
        jdbc.update("""
          UPDATE tareas t JOIN expedientes e ON e.id=t.expediente_id
          SET t.estado=:estado,t.etapa_flujo=:etapa,
              t.fecha_inicio_real=CASE WHEN :estado='EN_PROCESO' AND t.fecha_inicio_real IS NULL THEN NOW(3) ELSE t.fecha_inicio_real END,
              t.fecha_completado=CASE WHEN :estado='COMPLETADA' THEN NOW(3) ELSE t.fecha_completado END,
              t.porcentaje_avance=CASE WHEN :estado='COMPLETADA' THEN 100 ELSE t.porcentaje_avance END,
              t.usuario_modificacion_id=:usuarioId
          WHERE t.id=:id AND e.estudio_id=:estudioId
          """ + visibilidad() + """
          """,p);
        jdbc.update("""
          INSERT INTO tarea_historial(tarea_id,usuario_id,estado_anterior,estado_nuevo,etapa_anterior,etapa_nueva,observacion)
          VALUES(:t,:u,:ea,:en,:fa,:fn,:obs)
          """, new MapSqlParameterSource().addValue("t",id).addValue("u",SesionActual.usuarioId())
                .addValue("ea",anterior.get("estado")).addValue("en",estado).addValue("fa",anterior.get("etapa_flujo"))
                .addValue("fn",etapa).addValue("obs",observacion));
    }

    public boolean toggleSubtarea(Long tareaId,Long subtareaId){
        int n=jdbc.update("""
            UPDATE subtareas s JOIN tareas t ON t.id=s.tarea_id JOIN expedientes e ON e.id=t.expediente_id
            SET s.completada=IF(s.completada=1,0,1), s.fecha_completado=IF(s.completada=0,NOW(3),NULL)
            WHERE s.id=:sid AND s.tarea_id=:tid AND e.estudio_id=:estudioId
            """ + visibilidad() + """
            """,base().addValue("sid",subtareaId).addValue("tid",tareaId));
        actualizarAvance(tareaId); return n>0;
    }

    private void actualizarAvance(Long tareaId){
        jdbc.update("""
          UPDATE tareas SET porcentaje_avance=COALESCE((SELECT ROUND(100*SUM(completada)/COUNT(*)) FROM subtareas WHERE tarea_id=:id),porcentaje_avance)
          WHERE id=:id AND estado<>'COMPLETADA'
          """,Map.of("id",tareaId));
    }

    public void comentar(Long tareaId,String texto){
        Long expedienteId=jdbc.queryForObject("SELECT expediente_id FROM tareas WHERE id=:id",Map.of("id",tareaId),Long.class);
        jdbc.update("INSERT INTO comentarios(expediente_id,tarea_id,usuario_id,texto) VALUES(:e,:t,:u,:x)",
                new MapSqlParameterSource().addValue("e",expedienteId).addValue("t",tareaId).addValue("u",SesionActual.usuarioId()).addValue("x",texto.trim()));
    }

    public void registrarTiempo(Long tareaId,Integer minutos){
        jdbc.update("UPDATE tareas SET tiempo_real_minutos=:m,usuario_modificacion_id=:u WHERE id=:id",
                new MapSqlParameterSource().addValue("m",minutos).addValue("u",SesionActual.usuarioId()).addValue("id",tareaId));
    }

    public List<Subtarea> subtareas(Long id){ return jdbc.query("""
      SELECT s.id,s.titulo,s.completada,s.orden,s.usuario_responsable_id,u.nombre_completo,s.fecha_completado
      FROM subtareas s LEFT JOIN usuarios u ON u.id=s.usuario_responsable_id WHERE s.tarea_id=:id ORDER BY s.orden,s.id
      """,Map.of("id",id),(rs,n)->new Subtarea(rs.getLong(1),rs.getString(2),rs.getBoolean(3),rs.getInt(4),getLong(rs,5),rs.getString(6),getDateTime(rs,7))); }

    public List<Comentario> comentarios(Long id){ return jdbc.query("""
      SELECT c.id,u.nombre_completo,c.texto,c.fecha_creacion FROM comentarios c JOIN usuarios u ON u.id=c.usuario_id
      WHERE c.tarea_id=:id AND c.activo=1 ORDER BY c.fecha_creacion DESC
      """,Map.of("id",id),(rs,n)->new Comentario(rs.getLong(1),rs.getString(2),rs.getString(3),getDateTime(rs,4))); }

    public List<Adjunto> adjuntos(Long id){ return jdbc.query("""
      SELECT d.uuid_publico,d.nombre,d.categoria,d.estado,d.fecha_creacion FROM documentos d
      WHERE d.tarea_id=:id AND d.activo=1 AND d.en_papelera=0 ORDER BY d.fecha_creacion DESC
      """,Map.of("id",id),(rs,n)->new Adjunto(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),getDateTime(rs,5))); }

    public List<Historial> historial(Long id){ return jdbc.query("""
      SELECT h.id,u.nombre_completo,h.estado_anterior,h.estado_nuevo,h.etapa_anterior,h.etapa_nueva,h.observacion,h.fecha_cambio
      FROM tarea_historial h JOIN usuarios u ON u.id=h.usuario_id WHERE h.tarea_id=:id ORDER BY h.fecha_cambio DESC,h.id DESC
      """,Map.of("id",id),(rs,n)->new Historial(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),getDateTime(rs,8))); }

    /**
     * Marca como VENCIDA lo que paso de fecha y sigue en curso.
     *
     * Ya NO toca APROBADA ni PRESENTADA: una tarea aprobada que pasa de fecha
     * perdia su aprobacion y salia de la columna Completado del tablero. La
     * aprobacion es un hecho, no caduca porque el plazo interno se pase.
     *
     * Va sin la regla de visibilidad a proposito: es un barrido del estudio, no
     * una consulta de usuario. Acotarlo por lo que ve quien llama dejaria el
     * resto sin marcar segun quien entre primero.
     */
    public void marcarVencidas(){ jdbc.update("""
      UPDATE tareas t JOIN expedientes e ON e.id=t.expediente_id
      SET t.estado='VENCIDA' WHERE e.estudio_id=:estudioId AND t.activo=1 AND t.fecha_limite<NOW()
        AND t.estado IN ('PENDIENTE','EN_PROCESO','EN_REVISION','OBSERVADA')
      """,new MapSqlParameterSource("estudioId",SesionActual.estudioId())); }

    private String construirFiltros(MapSqlParameterSource p,String texto,String estado,String prioridad,Long responsableId,
                                    Long expedienteId,LocalDateTime desde,LocalDateTime hasta){
        StringBuilder sql=new StringBuilder(" WHERE e.estudio_id=:estudioId AND t.activo=1 ")
                .append(visibilidad());
        if(texto!=null&&!texto.isBlank()){
            sql.append(" AND (LOWER(t.titulo) LIKE :texto OR LOWER(COALESCE(t.descripcion,'')) LIKE :texto OR LOWER(e.codigo_interno) LIKE :texto OR LOWER(COALESCE(e.numero_expediente,'')) LIKE :texto OR LOWER(u.nombre_completo) LIKE :texto) ");
            p.addValue("texto","%"+texto.trim().toLowerCase()+"%");
        }
        if(estado!=null){sql.append(" AND t.estado=:estado ");p.addValue("estado",estado);}
        if(prioridad!=null){sql.append(" AND t.prioridad=:prioridad ");p.addValue("prioridad",prioridad);}
        if(responsableId!=null){sql.append(" AND t.usuario_responsable_id=:responsableId ");p.addValue("responsableId",responsableId);}
        if(expedienteId!=null){sql.append(" AND t.expediente_id=:expedienteId ");p.addValue("expedienteId",expedienteId);}
        if(desde!=null){sql.append(" AND t.fecha_limite>=:desde ");p.addValue("desde",desde);}
        if(hasta!=null){sql.append(" AND t.fecha_limite<=:hasta ");p.addValue("hasta",hasta);}
        return sql.toString();
    }

    private String orden(){return " ORDER BY FIELD(t.estado,'VENCIDA','PENDIENTE','EN_PROCESO','EN_REVISION','OBSERVADA','APROBADA','PRESENTADA','COMPLETADA','CANCELADA'), FIELD(t.prioridad,'URGENTE','ALTA','MEDIA','BAJA'), t.fecha_limite ASC, t.orden_tablero ASC ";}
    private boolean existe(String sql,Long id){ return Boolean.TRUE.equals(jdbc.queryForObject(sql,base().addValue("id",id),Integer.class)>0); }
    private MapSqlParameterSource base(){
        MapSqlParameterSource p = new MapSqlParameterSource("estudioId", SesionActual.estudioId());
        p.addValues(VisibilidadExpedientes.parametros());
        return p;
    }

    /**
     * La regla de RF-02, compartida con expedientes, actuaciones, agenda,
     * documental, plazos y monitor.
     *
     * Este modulo acotaba solo por `e.estudio_id`, asi que cualquiera del
     * estudio veia las tareas de expedientes confidenciales que no tiene
     * asignados, y podia crear tareas sobre ellos. Es la cuarta vez que la
     * regla se pierde al escribir un modulo nuevo; el helper existe para que
     * sea una linea.
     */
    private static String visibilidad(){ return VisibilidadExpedientes.sql("e"); }

    private String baseSelect(){ return """
      SELECT t.id,t.uuid_publico,t.titulo,t.expediente_id,e.codigo_interno,e.numero_expediente,c.nombre_busqueda,
        t.usuario_responsable_id,u.nombre_completo,t.usuario_supervisor_id,s.nombre_completo supervisor,t.prioridad,t.estado,t.etapa_flujo,
        t.fecha_limite,t.porcentaje_avance,
        (SELECT COUNT(*) FROM subtareas st WHERE st.tarea_id=t.id) subtareas_total,
        (SELECT COUNT(*) FROM subtareas st WHERE st.tarea_id=t.id AND st.completada=1) subtareas_completadas,
        (SELECT COUNT(*) FROM documentos d WHERE d.tarea_id=t.id AND d.activo=1 AND d.en_papelera=0) adjuntos,
        (SELECT COUNT(*) FROM comentarios cm WHERE cm.tarea_id=t.id AND cm.activo=1) comentarios
      FROM tareas t
      JOIN expedientes e ON e.id=t.expediente_id
      JOIN clientes c ON c.id=e.cliente_id
      JOIN usuarios u ON u.id=t.usuario_responsable_id
      LEFT JOIN usuarios s ON s.id=t.usuario_supervisor_id
      """; }

    private String detailSelect(){ return """
      SELECT t.id,t.uuid_publico,t.titulo,t.descripcion,t.expediente_id,e.codigo_interno,e.numero_expediente,c.nombre_busqueda,
        t.usuario_responsable_id,u.nombre_completo,t.usuario_supervisor_id,s.nombre_completo supervisor,t.prioridad,t.estado,t.etapa_flujo,
        t.fecha_asignacion,t.fecha_limite,t.fecha_inicio_real,t.fecha_completado,t.tiempo_estimado_minutos,t.tiempo_real_minutos,t.porcentaje_avance,
        (SELECT COUNT(*) FROM subtareas st WHERE st.tarea_id=t.id) subtareas_total,
        (SELECT COUNT(*) FROM subtareas st WHERE st.tarea_id=t.id AND st.completada=1) subtareas_completadas,
        (SELECT COUNT(*) FROM documentos d WHERE d.tarea_id=t.id AND d.activo=1 AND d.en_papelera=0) adjuntos,
        (SELECT COUNT(*) FROM comentarios cm WHERE cm.tarea_id=t.id AND cm.activo=1) comentarios
      FROM tareas t
      JOIN expedientes e ON e.id=t.expediente_id
      JOIN clientes c ON c.id=e.cliente_id
      JOIN usuarios u ON u.id=t.usuario_responsable_id
      LEFT JOIN usuarios s ON s.id=t.usuario_supervisor_id
      """; }

    private String exportSelect(){ return """
      SELECT t.titulo,e.codigo_interno,u.nombre_completo,s.nombre_completo supervisor,t.prioridad,t.estado,t.etapa_flujo,t.fecha_limite
      FROM tareas t
      JOIN expedientes e ON e.id=t.expediente_id
      JOIN usuarios u ON u.id=t.usuario_responsable_id
      LEFT JOIN usuarios s ON s.id=t.usuario_supervisor_id
      """; }

    private TareaLista mapLista(ResultSet rs)throws SQLException{
        return new TareaLista(rs.getLong("id"),rs.getString("uuid_publico"),rs.getString("titulo"),rs.getLong("expediente_id"),
                rs.getString("codigo_interno"),rs.getString("numero_expediente"),rs.getString("nombre_busqueda"),
                rs.getLong("usuario_responsable_id"),rs.getString("nombre_completo"),getLong(rs,"usuario_supervisor_id"),rs.getString("supervisor"),
                rs.getString("prioridad"),rs.getString("estado"),rs.getString("etapa_flujo"),getDateTime(rs,"fecha_limite"),
                rs.getInt("porcentaje_avance"),rs.getLong("subtareas_total"),rs.getLong("subtareas_completadas"),rs.getLong("adjuntos"),rs.getLong("comentarios"));
    }

    private TareaInfo mapInfo(ResultSet rs)throws SQLException{
        return new TareaInfo(rs.getLong("id"),rs.getString("uuid_publico"),rs.getString("titulo"),rs.getString("descripcion"),
                rs.getLong("expediente_id"),rs.getString("codigo_interno"),rs.getString("numero_expediente"),rs.getString("nombre_busqueda"),
                rs.getLong("usuario_responsable_id"),rs.getString("nombre_completo"),getLong(rs,"usuario_supervisor_id"),rs.getString("supervisor"),
                rs.getString("prioridad"),rs.getString("estado"),rs.getString("etapa_flujo"),getDateTime(rs,"fecha_asignacion"),
                getDateTime(rs,"fecha_limite"),getDateTime(rs,"fecha_inicio_real"),getDateTime(rs,"fecha_completado"),
                getInt(rs,"tiempo_estimado_minutos"),getInt(rs,"tiempo_real_minutos"),rs.getInt("porcentaje_avance"),
                rs.getLong("subtareas_total"),rs.getLong("subtareas_completadas"),rs.getLong("adjuntos"),rs.getLong("comentarios"));
    }

    private ExportacionFila mapExport(ResultSet rs)throws SQLException{
        return new ExportacionFila(rs.getString("titulo"),rs.getString("codigo_interno"),rs.getString("nombre_completo"),rs.getString("supervisor"),
                rs.getString("prioridad"),rs.getString("estado"),rs.getString("etapa_flujo"),getDateTime(rs,"fecha_limite"));
    }

    private Long getLong(ResultSet rs,String c)throws SQLException{long v=rs.getLong(c);return rs.wasNull()?null:v;} private Long getLong(ResultSet rs,int c)throws SQLException{long v=rs.getLong(c);return rs.wasNull()?null:v;}
    private Integer getInt(ResultSet rs,String c)throws SQLException{int v=rs.getInt(c);return rs.wasNull()?null:v;} private LocalDateTime getDateTime(ResultSet rs,String c)throws SQLException{var t=rs.getTimestamp(c);return t==null?null:t.toLocalDateTime();} private LocalDateTime getDateTime(ResultSet rs,int c)throws SQLException{var t=rs.getTimestamp(c);return t==null?null:t.toLocalDateTime();}
}
